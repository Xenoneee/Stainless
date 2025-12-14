package xenon.addon.stainless.modules;

import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.component.DataComponentTypes;

import java.util.*;

public class AutoMinePlus extends StainlessModule {

    // ==================== CONSTANTS ====================
    private static final int TARGET_UPDATE_INTERVAL = 10; // ticks
    private static final int MIN_CHAT_COOLDOWN_TICKS = 40; // 2 seconds
    private static final double RENDER_PULSE_SPEED = 2.0;
    private static final double RENDER_PULSE_MAX = 2.0;
    private static final double RENDER_PULSE_MIN = -2.0;
    private static final double PROGRESS_DIVISOR = 2.0;

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgBedrock = settings.createGroup("Bedrock");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General
    private final Setting<Double> breakRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("break-range")
        .description("Maximum block break range.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate to block before mining.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> support = sgGeneral.add(new BoolSetting.Builder()
        .name("support")
        .description("Places support block under break target if missing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("How far to place support blocks.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .visible(support::get)
        .build()
    );

    private final Setting<Boolean> pauseWhileEating = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-eating")
        .description("Temporarily pauses AutoMinePlus while you're eating food.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info")
        .description("Sends debug info in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chatDelay = sgGeneral.add(new IntSetting.Builder()
        .name("chat-delay")
        .description("Minimum ticks between messages (2s min enforced).")
        .defaultValue(40)
        .min(0)
        .sliderMax(200)
        .build()
    );

    // Targeting
    private final Setting<Double> targetRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Range to target players.")
        .defaultValue(5.5)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Don't target players on your friends list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreNaked = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-naked")
        .description("Don't target players with no armor equipped.")
        .defaultValue(true)
        .build()
    );

    private final Setting<TargetMode> targetMode = sgTargeting.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("How to select which block to mine.")
        .defaultValue(TargetMode.Nearest)
        .build()
    );

    private final Setting<Boolean> mineHead = sgTargeting.add(new BoolSetting.Builder()
        .name("mine-head")
        .description("Mine block above enemy's head.")
        .defaultValue(true)
        .build()
    );

    // Bedrock
    private final Setting<Boolean> mineBedrock = sgBedrock.add(new BoolSetting.Builder()
        .name("mine-bedrock")
        .description("Allows mining bedrock blocks around the target.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> bedrockOnly = sgBedrock.add(new BoolSetting.Builder()
        .name("bedrock-only")
        .description("Only break bedrock. Ignore all other blocks.")
        .defaultValue(false)
        .visible(mineBedrock::get)
        .build()
    );

    private final Setting<Boolean> prioritizePlayerBedrock = sgBedrock.add(new BoolSetting.Builder()
        .name("prioritize-target-standing-bedrock")
        .description("Prioritize mining the bedrock the target is standing in over surrounding blocks.")
        .defaultValue(true)
        .visible(mineBedrock::get)
        .build()
    );

    private final Setting<Boolean> clearUpperBedrock = sgBedrock.add(new BoolSetting.Builder()
        .name("clear-upper-bedrock")
        .description("If phased, automatically mine the bedrock at your upper hitbox to free AutoMine/AutoCrystal.")
        .defaultValue(true)
        .build()
    );

    // Advanced
    private final Setting<Boolean> usePacketMine = sgAdvanced.add(new BoolSetting.Builder()
        .name("use-packet-mine")
        .description("Use PacketMine system for more reliable server-side mining.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> manualOverride = sgAdvanced.add(new BoolSetting.Builder()
        .name("manual-override")
        .description("Allow manual block breaking (left click) to override automatic targeting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakFace = sgAdvanced.add(new BoolSetting.Builder()
        .name("break-face")
        .description("Breaks block in your face so you don't crawl.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakCrawl = sgAdvanced.add(new BoolSetting.Builder()
        .name("break-crawl")
        .description("Automatically mine blocks when stuck crawling.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> mineBurrow = sgAdvanced.add(new BoolSetting.Builder()
        .name("mine-burrow")
        .description("Detect and mine burrow blocks using collision detection.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> selfHead = sgAdvanced.add(new BoolSetting.Builder()
        .name("self-head")
        .description("Mine block above your own head.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyInHole = sgAdvanced.add(new BoolSetting.Builder()
        .name("only-in-hole")
        .description("Only mine above your own head if you're in a hole.")
        .defaultValue(true)
        .visible(selfHead::get)
        .build()
    );

    // Render
    private final Setting<Double> animationExp = sgRender.add(new DoubleSetting.Builder()
        .name("animation-exp")
        .description("Ease exponent. 3–4 looks nice.")
        .defaultValue(3)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Which parts of render should be rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> lineStartColor = sgRender.add(new ColorSetting.Builder()
        .name("line-start-color")
        .description("Start of line gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 0))
        .build()
    );

    private final Setting<SettingColor> lineEndColor = sgRender.add(new ColorSetting.Builder()
        .name("line-end-color")
        .description("End of line gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<SettingColor> startColor = sgRender.add(new ColorSetting.Builder()
        .name("side-start-color")
        .description("Start of side gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 0))
        .build()
    );

    private final Setting<SettingColor> endColor = sgRender.add(new ColorSetting.Builder()
        .name("side-end-color")
        .description("End of side gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    private final Setting<Integer> bedrockProgressEstimateTicks = sgRender.add(new IntSetting.Builder()
        .name("bedrock-progress-estimate-ticks")
        .description("When bedrock has 0 vanilla delta, assume it breaks after this many ticks (render only).")
        .defaultValue(40)
        .min(1)
        .sliderMax(200)
        .build()
    );

    // ==================== STATE ====================
    private MiningState currentState = MiningState.IDLE;

    // Target management
    private PlayerEntity target;
    private int targetUpdateCooldown = 0;

    // Mining state
    private BlockPos targetPos;
    private BlockPos manualPos;
    private BlockPos lastBreakPos;
    private double breakProgress = 0.0;
    private boolean minedThisTick = false;

    // Chat system
    private final ChatThrottler chatThrottler = new ChatThrottler();
    private boolean wasEating = false;

    // Render state
    private final RenderAnimator renderAnimator = new RenderAnimator();

    // Block finding helper
    private BlockFinder blockFinder;

    // PacketMine integration
    private PacketMinePlus packetMine;

    // ==================== ENUMS ====================
    public enum TargetMode {
        Nearest,
        Damage,
        Random
    }

    // ==================== CONSTRUCTOR ====================
    public AutoMinePlus() {
        super(Stainless.STAINLESS_CATEGORY, "AutoMinePlus",
            "AutoMine with bedrock utilities and packet mining support.");
    }

    // ==================== LIFECYCLE ====================
    @Override
    public void onActivate() {
        super.onActivate();
        resetState();
        blockFinder = new BlockFinder();

        // Get PacketMine instance if enabled
        if (usePacketMine.get()) {
            packetMine = Modules.get().get(PacketMinePlus.class);
            if (packetMine != null && !packetMine.isActive()) {
                packetMine.toggle();
            }
        }
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();

        // Stop packet mining if we started it
        if (packetMine != null && packetMine.isActive()) {
            packetMine.stopMining();
        }

        resetState();
    }

    private void resetState() {
        currentState = MiningState.IDLE;
        target = null;
        targetPos = null;
        manualPos = null;
        targetUpdateCooldown = 0;

        breakProgress = 0;
        lastBreakPos = null;
        minedThisTick = false;

        chatThrottler.reset();
        wasEating = false;

        renderAnimator.reset();
    }

    // ==================== EVENT HANDLERS ====================
    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!manualOverride.get()) return;
        if (!isInBreakRange(event.blockPos)) return;

        manualPos = event.blockPos;
        chatThrottler.queue("Manual override: " +
            mc.world.getBlockState(event.blockPos).getBlock().getName().getString());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isValidContext()) return;

        minedThisTick = false;
        chatThrottler.tick();

        // Validate and potentially clear manual position
        if (manualPos != null) {
            if (mc.world.getBlockState(manualPos).isAir() || !isInBreakRange(manualPos)) {
                manualPos = null;
            }
        }

        // Validate current target
        validateCurrentTarget();

        // Check pause conditions
        if (handleEatingPause()) {
            currentState = MiningState.PAUSED_EATING;
            return;
        }

        // Priority 1: Manual override
        if (manualPos != null) {
            currentState = MiningState.MANUAL;
            targetPos = manualPos;
            executeMining();
            return;
        }

        // Priority 2: Clear upper bedrock
        if (shouldClearUpperBedrock()) {
            currentState = MiningState.CLEARING_UPPER_BEDROCK;
            executeClearUpperBedrock();
            return;
        }

        // Priority 3: Break face block
        if (handleFaceBlock()) {
            currentState = MiningState.BREAKING_FACE;
            return;
        }

        // Priority 4: Escape crawl
        if (handleCrawlEscape()) {
            currentState = MiningState.ESCAPING_CRAWL;
            return;
        }

        // Priority 5: Self head
        if (handleSelfHead()) {
            currentState = MiningState.MINING_SELF_HEAD;
            return;
        }

        // Update target periodically
        updateTarget();

        if (target == null) {
            currentState = MiningState.IDLE;
            targetPos = null;
            updateBreakProgress();
            return;
        }

        // Select mining target based on priority
        if (!selectMiningTarget()) {
            updateBreakProgress();
            return;
        }

        // Execute the mining operation
        executeMining();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!isValidContext() || targetPos == null) return;

        renderAnimator.update();

        double progress = calculateRenderProgress();
        double alphaPulse = renderAnimator.getPulse();

        renderBreakAnimation(event.renderer, progress, alphaPulse);
    }

    // ==================== CORE LOGIC ====================
    private boolean isValidContext() {
        return mc.player != null && mc.world != null;
    }

    private void validateCurrentTarget() {
        if (targetPos == null) return;

        // Clear if block is now air
        if (mc.world.getBlockState(targetPos).isAir()) {
            targetPos = null;
            breakProgress = 0;
            lastBreakPos = null;
            return;
        }

        // Clear if out of range
        if (!isInBreakRange(targetPos)) {
            targetPos = null;
            breakProgress = 0;
            lastBreakPos = null;
        }
    }

    private void updateTarget() {
        if (targetUpdateCooldown > 0) {
            targetUpdateCooldown--;
            // Validate existing target
            if (target != null && !isValidTarget(target)) {
                target = null;
            }
            return;
        }

        acquireTarget();
        targetUpdateCooldown = TARGET_UPDATE_INTERVAL;
    }

    private void acquireTarget() {
        target = null;
        double closestDistanceSq = targetRange.get() * targetRange.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isValidTarget(player)) continue;

            double distanceSq = player.squaredDistanceTo(mc.player);
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                target = player;
            }
        }
    }

    private boolean isValidTarget(PlayerEntity player) {
        if (player == mc.player) return false;
        if (player.isRemoved() || player.isCreative() || player.isSpectator()) return false;
        if (ignoreFriends.get() && Friends.get().isFriend(player)) return false;
        if (ignoreNaked.get() && isNaked(player)) return false;

        double rangeSq = targetRange.get() * targetRange.get();
        return player.squaredDistanceTo(mc.player) <= rangeSq;
    }

    private boolean selectMiningTarget() {
        // Priority 1: Burrow detection
        if (mineBurrow.get()) {
            BlockPos burrowPos = detectBurrowBlock(target);
            if (burrowPos != null) {
                targetPos = burrowPos;
                currentState = MiningState.MINING_BURROW;
                chatThrottler.queue(ChatMessage.BREAKING_BURROW);
                return true;
            }
        }

        // Priority 2: Standing bedrock
        if (trySelectStandingBedrock()) {
            currentState = MiningState.MINING_STANDING_BEDROCK;
            return true;
        }

        // Priority 3: Surrounding blocks
        targetPos = blockFinder.findCityBlock(target);

        if (targetPos == null) {
            if (bedrockOnly.get()) {
                chatThrottler.queue(ChatMessage.NO_BEDROCK_FOUND);
            }
            return false;
        }

        currentState = MiningState.MINING_SURROUNDING_BLOCK;

        Block block = mc.world.getBlockState(targetPos).getBlock();
        if (block == Blocks.BEDROCK) {
            chatThrottler.queue(ChatMessage.BREAKING_SURROUNDING_BEDROCK);
        } else {
            chatThrottler.queue("Breaking surrounding block: " + block.getName().getString());
        }

        return true;
    }

    private boolean trySelectStandingBedrock() {
        if (!mineBedrock.get() || !prioritizePlayerBedrock.get()) return false;

        BlockPos standingPos = target.getBlockPos();

        if (mc.world.getBlockState(standingPos).getBlock() != Blocks.BEDROCK) return false;
        if (!isInBreakRange(standingPos)) return false;

        targetPos = standingPos;
        chatThrottler.queue(ChatMessage.BREAKING_STANDING_BEDROCK);
        return true;
    }

    private void executeMining() {
        if (!isInBreakRange(targetPos)) {
            updateBreakProgress();
            return;
        }

        // Place support if needed
        if (support.get()) {
            BlockPos supportPos = targetPos.down();
            if (mc.world.getBlockState(supportPos).isAir() && isInPlaceRange(supportPos)) {
                BlockUtils.place(supportPos, InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0, true);
            }
        }

        // Check rotation allowance
        boolean allowActions = shouldAllowActions();

        // Check if we should use vanilla mining for bedrock
        boolean isBedrock = mc.world.getBlockState(targetPos).getBlock() == Blocks.BEDROCK;
        boolean forceVanillaMining = isBedrock && bedrockOnly.get();

        // Use PacketMine or vanilla mining
        if (!forceVanillaMining && usePacketMine.get() && packetMine != null && packetMine.isActive()) {
            executePacketMining(allowActions);
        } else {
            executeVanillaMining(allowActions);
        }
    }

    private void executePacketMining(boolean allowActions) {
        if (!allowActions) {
            updateBreakProgress();
            return;
        }

        // Start packet mining if not already mining this position
        if (packetMine.getCurrentPos() == null || !packetMine.getCurrentPos().equals(targetPos)) {
            packetMine.startMining(targetPos, Direction.UP);
            minedThisTick = true;
        }

        // Sync progress from PacketMine
        updateBreakProgress();
    }

    private void executeVanillaMining(boolean allowActions) {
        // Rotate if needed
        if (rotate.get() && allowActions) {
            Rotations.rotate(Rotations.getYaw(targetPos), Rotations.getPitch(targetPos));
        }

        // Execute mining
        if (allowActions) {
            mc.interactionManager.updateBlockBreakingProgress(targetPos, Direction.UP);
            mc.player.swingHand(Hand.MAIN_HAND);
            minedThisTick = true;
        }

        updateBreakProgress();
    }

    // ==================== SPECIAL MINING MODES ====================
    private boolean shouldClearUpperBedrock() {
        if (!clearUpperBedrock.get()) return false;

        BlockPos headPos = mc.player.getBlockPos().up(1);
        return mc.world.getBlockState(headPos).getBlock() == Blocks.BEDROCK;
    }

    private void executeClearUpperBedrock() {
        BlockPos headPos = mc.player.getBlockPos().up(1);
        targetPos = headPos;

        // Place support if needed
        if (support.get()) {
            BlockPos supportPos = targetPos.down();
            if (mc.world.getBlockState(supportPos).isAir()) {
                BlockUtils.place(supportPos, InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0, true);
            }
        }

        // Always use vanilla mining for clearing upper bedrock when bedrockOnly is enabled
        boolean forceVanillaMining = bedrockOnly.get();

        if (!forceVanillaMining && usePacketMine.get() && packetMine != null && packetMine.isActive()) {
            if (packetMine.getCurrentPos() == null || !packetMine.getCurrentPos().equals(targetPos)) {
                packetMine.startMining(targetPos, Direction.UP);
                minedThisTick = true;
            }
        } else {
            // Rotate and mine
            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(targetPos), Rotations.getPitch(targetPos));
            }

            mc.interactionManager.updateBlockBreakingProgress(targetPos, Direction.UP);
            mc.player.swingHand(Hand.MAIN_HAND);
            minedThisTick = true;
        }

        chatThrottler.queue(ChatMessage.CLEARING_UPPER_BEDROCK);
        updateBreakProgress();
    }

    private boolean handleFaceBlock() {
        if (!breakFace.get()) return false;

        BlockPos facePos = mc.player.getBlockPos().up();
        Block faceBlock = mc.world.getBlockState(facePos).getBlock();

        // Don't try to mine air or bedrock
        if (faceBlock == Blocks.AIR || faceBlock == Blocks.BEDROCK) return false;

        targetPos = facePos;
        chatThrottler.queue(ChatMessage.BREAKING_FACE);
        executeMining();
        return true;
    }

    private boolean handleCrawlEscape() {
        if (!breakCrawl.get()) return false;

        if (mc.player.isCrawling() && mc.player.isOnGround() && !mc.player.isGliding()) {
            BlockPos above = mc.player.getBlockPos().up();
            if (!mc.world.getBlockState(above).isAir()) {
                BlockPos escapePos = mc.player.getBlockPos().down();

                // If standing on bedrock, mine above instead
                if (mc.world.getBlockState(escapePos).getBlock() == Blocks.BEDROCK) {
                    escapePos = above;
                }

                // Don't mine bedrock unless enabled
                if (mc.world.getBlockState(escapePos).getBlock() == Blocks.BEDROCK && !mineBedrock.get()) {
                    return false;
                }

                targetPos = escapePos;
                chatThrottler.queue(ChatMessage.ESCAPING_CRAWL);
                executeMining();
                return true;
            }
        }
        return false;
    }

    private boolean handleSelfHead() {
        if (!selfHead.get()) return false;

        BlockPos headPos = mc.player.getBlockPos().up(2);

        // Check if only in hole
        if (onlyInHole.get() && !isInHole(mc.player)) {
            return false;
        }

        Block headBlock = mc.world.getBlockState(headPos).getBlock();
        if (headBlock == Blocks.AIR || headBlock == Blocks.BEDROCK) {
            return false;
        }

        targetPos = headPos;
        chatThrottler.queue(ChatMessage.MINING_SELF_HEAD);
        executeMining();
        return true;
    }

    private BlockPos detectBurrowBlock(PlayerEntity player) {
        Iterable<VoxelShape> shapes = mc.world.getBlockCollisions(
            player,
            player.getBoundingBox().withMaxY(player.getBoundingBox().minY + 0.5)
        );

        for (VoxelShape shape : shapes) {
            Box box = shape.getBoundingBox();
            BlockPos pos = BlockPos.ofFloored(box.getCenter());
            Block block = mc.world.getBlockState(pos).getBlock();

            if (block != Blocks.BEDROCK && block != Blocks.AIR && isInBreakRange(pos)) {
                return pos;
            }
        }
        return null;
    }

    // ==================== PAUSE HANDLING ====================
    private boolean handleEatingPause() {
        if (!pauseWhileEating.get()) return false;

        boolean eatingNow = mc.player.isUsingItem() && isFood(mc.player.getActiveItem());

        if (eatingNow) {
            if (!wasEating) {
                chatThrottler.queue(ChatMessage.PAUSED_EATING);
                wasEating = true;
            }
            updateBreakProgress();
            return true;
        } else if (wasEating) {
            chatThrottler.queue(ChatMessage.RESUMED_EATING);
            wasEating = false;
        }

        return false;
    }

    // ==================== PROGRESS TRACKING ====================
    private void updateBreakProgress() {
        if (targetPos == null) {
            breakProgress = 0;
            lastBreakPos = null;
            return;
        }

        // Check if we should skip PacketMine progress for bedrock in bedrockOnly mode
        boolean isBedrock = mc.world.getBlockState(targetPos).getBlock() == Blocks.BEDROCK;
        boolean forceVanillaProgress = isBedrock && bedrockOnly.get();

        // Use PacketMine progress if available and not forcing vanilla
        if (!forceVanillaProgress && usePacketMine.get() && packetMine != null &&
            packetMine.getCurrentPos() != null &&
            packetMine.getCurrentPos().equals(targetPos)) {

            breakProgress = packetMine.getNormalizedProgress();
            lastBreakPos = targetPos.toImmutable();

            if (breakProgress >= 1.0) {
                breakProgress = 0.0;
                lastBreakPos = null;
            }
            return;
        }

        // Fallback to vanilla calculation
        if (lastBreakPos == null || !lastBreakPos.equals(targetPos)) {
            breakProgress = 0;
            lastBreakPos = targetPos.toImmutable();
        }

        if (!minedThisTick) return;

        BlockState state = mc.world.getBlockState(targetPos);
        float delta = state.calcBlockBreakingDelta(mc.player, mc.world, targetPos);

        if (delta <= 0f && state.getBlock() == Blocks.BEDROCK) {
            delta = 1f / Math.max(1, bedrockProgressEstimateTicks.get());
        }

        breakProgress = MathHelper.clamp(breakProgress + delta, 0.0, 1.0);

        if (breakProgress >= 1.0) {
            breakProgress = 0.0;
            lastBreakPos = null;
        }
    }

    // ==================== RENDERING ====================
    private double calculateRenderProgress() {
        double base = MathHelper.clamp(breakProgress, 0.0, 1.0);
        // Ease-out: p = 1 - (1 - base)^exp
        return 1.0 - Math.pow(1.0 - base, animationExp.get());
    }

    private void renderBreakAnimation(Renderer3D renderer, double progress, double alphaPulse) {
        Box box = getRenderBox(progress / PROGRESS_DIVISOR);

        // Pass 1: Normal pulse
        renderer.box(
            box,
            getColor(startColor.get(), endColor.get(), progress, MathHelper.clamp(alphaPulse, 0, 1)),
            getColor(lineStartColor.get(), lineEndColor.get(), progress, MathHelper.clamp(alphaPulse, 0, 1)),
            shapeMode.get(), 0
        );

        // Pass 2: Inverted pulse
        renderer.box(
            box,
            getColor(startColor.get(), endColor.get(), progress, MathHelper.clamp(-alphaPulse, 0, 1)),
            getColor(lineStartColor.get(), lineEndColor.get(), progress, MathHelper.clamp(-alphaPulse, 0, 1)),
            shapeMode.get(), 0
        );
    }

    private Box getRenderBox(double progress) {
        double cx = targetPos.getX() + 0.5;
        double cy = targetPos.getY() + 0.5;
        double cz = targetPos.getZ() + 0.5;
        double r = MathHelper.clamp(progress, 0.0, 0.5);
        return new Box(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
    }

    private Color getColor(SettingColor start, SettingColor end, double progress, double alphaMulti) {
        return new Color(
            lerp(start.r, end.r, progress, 1),
            lerp(start.g, end.g, progress, 1),
            lerp(start.b, end.b, progress, 1),
            lerp(start.a, end.a, progress, alphaMulti)
        );
    }

    private int lerp(double start, double end, double progress, double multiplier) {
        return (int) Math.round((start + (end - start) * progress) * multiplier);
    }

    // ==================== UTILITY METHODS ====================
    private boolean shouldAllowActions() {
        BlockPos playerPos = mc.player.getBlockPos();

        // Special case: always allow if targeting block above head
        if (targetPos.equals(playerPos.up(1))) return true;

        Block blockAtFeet = mc.world.getBlockState(playerPos).getBlock();
        Block blockAboveHead = mc.world.getBlockState(playerPos.up(1)).getBlock();

        // Don't allow if player is floating (air at feet but block above head)
        return !(blockAtFeet == Blocks.AIR && blockAboveHead != Blocks.AIR);
    }

    private boolean isInBreakRange(BlockPos pos) {
        double rangeSq = breakRange.get() * breakRange.get();
        return PlayerUtils.squaredDistanceTo(pos) <= rangeSq;
    }

    private boolean isInPlaceRange(BlockPos pos) {
        double rangeSq = placeRange.get() * placeRange.get();
        return PlayerUtils.squaredDistanceTo(pos) <= rangeSq;
    }

    private boolean isNaked(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
            && player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
            && player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
            && player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.get(DataComponentTypes.FOOD) != null;
    }

    private boolean isInHole(PlayerEntity player) {
        BlockPos pos = player.getBlockPos();

        // Check all horizontal directions
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos offset = pos.offset(dir);
            Block block = mc.world.getBlockState(offset).getBlock();

            // Not in hole if any side is air
            if (block == Blocks.AIR) return false;
        }

        // Check below
        Block below = mc.world.getBlockState(pos.down()).getBlock();
        return below != Blocks.AIR;
    }

    // ==================== INNER CLASSES ====================

    /**
     * Mining state machine states
     */
    private enum MiningState {
        IDLE,
        MANUAL,
        CLEARING_UPPER_BEDROCK,
        BREAKING_FACE,
        ESCAPING_CRAWL,
        MINING_SELF_HEAD,
        MINING_BURROW,
        MINING_STANDING_BEDROCK,
        MINING_SURROUNDING_BLOCK,
        PAUSED_EATING
    }

    /**
     * Predefined chat messages
     */
    private enum ChatMessage {
        CLEARING_UPPER_BEDROCK("Clearing upper-hitbox bedrock."),
        PAUSED_EATING("Paused: eating."),
        RESUMED_EATING("Resuming after eating."),
        BREAKING_STANDING_BEDROCK("Breaking bedrock in target's lower hitbox."),
        BREAKING_SURROUNDING_BEDROCK("Breaking surrounding bedrock."),
        BREAKING_BURROW("Breaking burrow block."),
        BREAKING_FACE("Breaking face block to prevent crawl."),
        ESCAPING_CRAWL("Escaping crawl position."),
        MINING_SELF_HEAD("Mining block above own head."),
        NO_BEDROCK_FOUND("Bedrock-only: no bedrock found near target.");

        private final String message;

        ChatMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Handles chat message throttling with queue
     */
    private class ChatThrottler {
        private int cooldown = 0;
        private String pendingMessage = null;

        void tick() {
            if (cooldown > 0) {
                cooldown--;
                if (cooldown == 0 && pendingMessage != null) {
                    send(pendingMessage);
                    pendingMessage = null;
                }
            }
        }

        void queue(ChatMessage message) {
            queue(message.getMessage());
        }

        void queue(String message) {
            if (!chatInfo.get()) return;

            int cooldownTicks = Math.max(MIN_CHAT_COOLDOWN_TICKS, chatDelay.get());

            if (cooldown <= 0) {
                send(message);
            } else {
                pendingMessage = message;
            }
        }

        private void send(String message) {
            info(message);
            int cooldownTicks = Math.max(MIN_CHAT_COOLDOWN_TICKS, chatDelay.get());
            cooldown = cooldownTicks;
        }

        void reset() {
            cooldown = 0;
            pendingMessage = null;
        }
    }

    /**
     * Handles render animation state
     */
    private class RenderAnimator {
        private long lastTimeMs = 0;
        private double pulse = 1.0;
        private int direction = 1;

        void update() {
            long now = System.currentTimeMillis();
            double deltaSeconds = (now - lastTimeMs) / 1000.0;
            lastTimeMs = now;

            pulse += deltaSeconds * RENDER_PULSE_SPEED * direction;

            if (pulse >= RENDER_PULSE_MAX) {
                pulse = RENDER_PULSE_MAX;
                direction = -1;
            } else if (pulse <= RENDER_PULSE_MIN) {
                pulse = RENDER_PULSE_MIN;
                direction = 1;
            }
        }

        double getPulse() {
            return pulse;
        }

        void reset() {
            lastTimeMs = System.currentTimeMillis();
            pulse = 1.0;
            direction = 1;
        }
    }

    /**
     * Handles finding blocks to mine around targets
     */
    private class BlockFinder {

        /**
         * Finds a block to "city" around the target
         * Priority: bedrock (if enabled) -> solid blocks (with damage calculation if enabled)
         */
        BlockPos findCityBlock(PlayerEntity target) {
            BlockPos centerPos = target.getBlockPos();
            List<BlockPos> candidates = new ArrayList<>();

            // Pass 1: Find bedrock if enabled
            if (mineBedrock.get()) {
                BlockPos bedrockPos = findBedrockNear(centerPos);
                if (bedrockPos != null) return bedrockPos;
            }

            // If bedrock-only mode, don't look for other blocks
            if (bedrockOnly.get()) return null;

            // Pass 2: Collect all valid surrounding blocks
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos candidate = centerPos.offset(dir);
                if (isValidMiningCandidate(candidate)) {
                    candidates.add(candidate);
                }
            }

            // Add head position if enabled
            if (mineHead.get()) {
                BlockPos headPos = centerPos.up(2);
                if (isValidMiningCandidate(headPos)) {
                    candidates.add(headPos);
                }
            }

            if (candidates.isEmpty()) return null;

            // Select based on mode
            return selectBestCandidate(candidates, target);
        }

        private BlockPos findBedrockNear(BlockPos center) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos candidate = center.offset(dir);

                if (!isInBreakRange(candidate)) continue;
                if (mc.world.getBlockState(candidate).getBlock() != Blocks.BEDROCK) continue;

                return candidate;
            }
            return null;
        }

        private boolean isValidMiningCandidate(BlockPos pos) {
            if (!isInBreakRange(pos)) return false;
            if (!mc.world.getFluidState(pos).isEmpty()) return false;

            Block block = mc.world.getBlockState(pos).getBlock();
            if (block == Blocks.AIR || block == Blocks.BEDROCK) return false;

            return true;
        }

        private BlockPos selectBestCandidate(List<BlockPos> candidates, PlayerEntity target) {
            switch (targetMode.get()) {
                case Nearest:
                    return findNearestBlock(candidates);
                case Damage:
                    return findHighestDamageBlock(candidates, target);
                case Random:
                    return candidates.get(new Random().nextInt(candidates.size()));
                default:
                    return candidates.get(0);
            }
        }

        private BlockPos findNearestBlock(List<BlockPos> candidates) {
            BlockPos nearest = null;
            double nearestDistSq = Double.MAX_VALUE;

            for (BlockPos pos : candidates) {
                double distSq = PlayerUtils.squaredDistanceTo(pos);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = pos;
                }
            }

            return nearest;
        }

        private BlockPos findHighestDamageBlock(List<BlockPos> candidates, PlayerEntity target) {
            BlockPos best = null;
            double bestDamage = -1;

            for (BlockPos pos : candidates) {
                double damage = estimateCrystalDamage(pos, target);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    best = pos;
                }
            }

            return best != null ? best : candidates.get(0);
        }

        /**
         * Simple damage estimation based on distance
         * Can be enhanced by integrating with AutoCrystal's damage calculator
         */
        private double estimateCrystalDamage(BlockPos minePos, PlayerEntity target) {
            double distance = Math.sqrt(target.getBlockPos().getSquaredDistance(minePos));
            // Simple linear falloff: max damage at 0 distance, 0 damage at 6+ blocks
            return Math.max(0, 12 - distance * 2);
        }
    }
}
