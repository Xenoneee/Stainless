package xenon.addon.stainless.modules;

import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
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

// 1.21.x (food data component)
import net.minecraft.component.DataComponentTypes;

public class AutoMinePlus extends StainlessModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");

    // Targeting & ranges
    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range").description("Range to target players.")
        .defaultValue(5.5).min(1).sliderMax(6).build()
    );

    private final Setting<Double> breakRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("break-range").description("Maximum block break range.")
        .defaultValue(4.5).min(1).sliderMax(6).build()
    );

    // Bedrock options
    private final Setting<Boolean> mineBedrock = sgGeneral.add(new BoolSetting.Builder()
        .name("mine-bedrock").description("Allows mining bedrock blocks around the target.")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> bedrockOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("bedrock-only").description("Only break bedrock. Ignore all other blocks.")
        .defaultValue(false).visible(mineBedrock::get).build()
    );

    private final Setting<Boolean> prioritizePlayerBedrock = sgGeneral.add(new BoolSetting.Builder()
        .name("prioritize-target-standing-bedrock")
        .description("Prioritize mining the bedrock the target is standing in over surrounding blocks.")
        .defaultValue(true).visible(mineBedrock::get).build()
    );

    // Clear your own upper hitbox bedrock
    private final Setting<Boolean> clearUpperBedrock = sgGeneral.add(new BoolSetting.Builder()
        .name("clear-upper-bedrock")
        .description("If phased, automatically mine the bedrock at your upper hitbox to free AutoMine/AutoCrystal.")
        .defaultValue(true).build()
    );

    // Filters
    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends").description("Don't target players on your friends list.")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> ignoreNaked = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-naked").description("Don't target players with no armor equipped.")
        .defaultValue(true).build()
    );

    // Placement/support
    private final Setting<Boolean> support = sgGeneral.add(new BoolSetting.Builder()
        .name("support").description("Places support block under break target if missing.")
        .defaultValue(true).build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range").description("How far to place support blocks.")
        .defaultValue(4.5).min(1).sliderMax(6).visible(support::get).build()
    );

    // QoL
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").description("Rotate to block before mining.")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> pauseWhileEating = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-eating").description("Temporarily pauses AutoMinePlus while you're eating food.")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info").description("Sends debug info in chat.")
        .defaultValue(true).build()
    );

    private final Setting<Integer> chatDelay = sgGeneral.add(new IntSetting.Builder()
        .name("chat-delay").description("Minimum ticks between messages (2s min enforced).")
        .defaultValue(40).min(0).sliderMax(200).build()
    );

    // ---------------- RENDER (BlackOut-style gradient, synced) ----------------
    private final Setting<Double> animationExp = sgRender.add(new DoubleSetting.Builder()
        .name("animation-exp").description("Ease exponent. 3–4 looks nice.")
        .defaultValue(3).range(0, 10).sliderRange(0, 10).build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("Shape Mode").description("Which parts of render should be rendered.")
        .defaultValue(ShapeMode.Both).build()
    );

    private final Setting<SettingColor> lineStartColor = sgRender.add(new ColorSetting.Builder()
        .name("Line Start Color").description("Start of line gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 0)).build()
    );
    private final Setting<SettingColor> lineEndColor = sgRender.add(new ColorSetting.Builder()
        .name("Line End Color").description("End of line gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 255)).build()
    );
    private final Setting<SettingColor> startColor = sgRender.add(new ColorSetting.Builder()
        .name("Side Start Color").description("Start of side gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 0)).build()
    );
    private final Setting<SettingColor> endColor = sgRender.add(new ColorSetting.Builder()
        .name("Side End Color").description("End of side gradient.")
        .defaultValue(new SettingColor(255, 0, 0, 50)).build()
    );

    // Bedrock-only progress estimate for render when vanilla delta==0
    private final Setting<Integer> bedrockProgressEstimateTicks = sgRender.add(new IntSetting.Builder()
        .name("bedrock-progress-estimate-ticks")
        .description("When bedrock has 0 vanilla delta, assume it breaks after this many ticks (render only).")
        .defaultValue(40).min(1).sliderMax(200).build()
    );

    // ---------------- State ----------------
    private PlayerEntity target;
    private BlockPos targetPos;

    // Chat throttle
    private int chatCooldown = 0;
    private String pendingChat = null;
    private boolean wasEating = false;

    // Mining progress sync
    private double breakProgress = 0.0;   // 0..1 visual progress
    private BlockPos lastBreakPos = null;
    private boolean minedThisTick = false;

    // Just to keep the two-pass alpha feel from your reference
    private long lastTimeMs = 0;
    private double renderPulse = 1;
    private int renderDir = 1;

    public AutoMinePlus() {
        super(Stainless.STAINLESS_CATEGORY, "AutoMinePlus", "AutoMine with bedrock utilities.");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        target = null;
        targetPos = null;
        chatCooldown = 0;
        pendingChat = null;
        wasEating = false;

        breakProgress = 0;
        lastBreakPos = null;
        minedThisTick = false;

        lastTimeMs = System.currentTimeMillis();
        renderPulse = 1;
        renderDir = 1;
    }

    // ---- CHAT THROTTLE (2s min) ----
    private void chat(String msg) {
        if (!chatInfo.get()) return;
        int cooldownTicks = Math.max(40, chatDelay.get());
        if (chatCooldown <= 0) {
            info(msg);
            chatCooldown = cooldownTicks;
        } else {
            pendingChat = msg;
        }
    }
    // --------------------------------

    private boolean shouldAllowRotation() {
        BlockPos playerPos = mc.player.getBlockPos();
        Block blockAtFeet = mc.world.getBlockState(playerPos).getBlock();
        Block blockAboveHead = mc.world.getBlockState(playerPos.up(1)).getBlock();
        return !(blockAtFeet == Blocks.AIR && blockAboveHead != Blocks.AIR);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        minedThisTick = false; // reset per tick

        // chat cooldown tick
        if (chatCooldown > 0) {
            chatCooldown--;
            if (chatCooldown == 0 && pendingChat != null) {
                int cooldownTicks = Math.max(40, chatDelay.get());
                info(pendingChat);
                pendingChat = null;
                chatCooldown = cooldownTicks;
            }
        }

        // pause while eating
        if (pauseWhileEating.get() && mc.player != null) {
            boolean eatingNow = mc.player.isUsingItem() && isFood(mc.player.getActiveItem());
            if (eatingNow) {
                if (!wasEating) { chat("Paused: eating."); wasEating = true; }
                // no mining; don't advance progress
                updateBreakProgress();
                return;
            } else if (wasEating) {
                chat("Resuming after eating."); wasEating = false;
            }
        }

        // 1) Clear your own upper hitbox bedrock first
        if (clearUpperBedrock.get()) {
            BlockPos headPos = mc.player.getBlockPos().up(1);
            if (mc.world.getBlockState(headPos).getBlock() == Blocks.BEDROCK) {
                targetPos = headPos;

                if (support.get() && mc.world.getBlockState(targetPos.down()).isAir()) {
                    BlockUtils.place(targetPos.down(), InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0, true);
                }

                if (rotate.get()) Rotations.rotate(Rotations.getYaw(targetPos), Rotations.getPitch(targetPos));

                mc.interactionManager.updateBlockBreakingProgress(targetPos, Direction.UP);
                mc.player.swingHand(Hand.MAIN_HAND);
                minedThisTick = true;

                chat("Clearing upper-hitbox bedrock.");
                updateBreakProgress();
                return;
            }
        }

        // 2) Acquire/refresh target
        target = null;
        double closestDistance = targetRange.get() * targetRange.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isCreative() || player.isSpectator()) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;
            if (ignoreNaked.get() && isNaked(player)) continue;

            double distance = player.squaredDistanceTo(mc.player);
            if (distance <= closestDistance) {
                closestDistance = distance;
                target = player;
            }
        }

        if (target == null) {
            targetPos = null;
            updateBreakProgress();
            return;
        }

        // 3) Prefer bedrock in the target's lower hitbox
        boolean handledStandingBedrock = false;
        if (mineBedrock.get() && prioritizePlayerBedrock.get()) {
            BlockPos lowerHitboxPos = target.getBlockPos();
            if (mc.world.getBlockState(lowerHitboxPos).getBlock() == Blocks.BEDROCK
                && PlayerUtils.squaredDistanceTo(lowerHitboxPos) <= breakRange.get() * breakRange.get()) {
                targetPos = lowerHitboxPos;
                handledStandingBedrock = true;
                chat("Breaking bedrock in target's lower hitbox.");
            }
        }

        // 4) Otherwise pick a city block (respect bedrock-only)
        if (!handledStandingBedrock) {
            targetPos = findCityBlock(target);
            if (targetPos == null) {
                if (bedrockOnly.get()) chat("Bedrock-only: no bedrock found near target.");
                updateBreakProgress();
                return;
            }
            Block blk = mc.world.getBlockState(targetPos).getBlock();
            chat(blk == Blocks.BEDROCK ? "Breaking surrounding bedrock." : "Breaking surrounding block: " + blk.getName().getString());
        }

        if (PlayerUtils.squaredDistanceTo(targetPos) > breakRange.get() * breakRange.get()) {
            updateBreakProgress();
            return;
        }

        // Support placement
        if (support.get()
            && mc.world.getBlockState(targetPos.down()).isAir()
            && PlayerUtils.squaredDistanceTo(targetPos.down()) <= placeRange.get() * placeRange.get()) {
            BlockUtils.place(targetPos.down(), InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0, true);
        }

        boolean allowActions = shouldAllowRotation();
        if (targetPos.equals(mc.player.getBlockPos().up(1))) allowActions = true;

        if (rotate.get() && allowActions) {
            Rotations.rotate(Rotations.getYaw(targetPos), Rotations.getPitch(targetPos));
        }

        if (allowActions) {
            mc.interactionManager.updateBlockBreakingProgress(targetPos, Direction.UP);
            mc.player.swingHand(Hand.MAIN_HAND);
            minedThisTick = true;
        }

        updateBreakProgress();
    }

    /**
     * Progress sync: advance visual progress when we actually mine this tick.
     * Uses vanilla delta; falls back to estimate for bedrock if needed.
     */
    private void updateBreakProgress() {
        if (targetPos == null) {
            breakProgress = 0;
            lastBreakPos = null;
            return;
        }
        if (lastBreakPos == null || !lastBreakPos.equals(targetPos)) {
            breakProgress = 0;
            lastBreakPos = targetPos.toImmutable();
        }
        if (!minedThisTick) return;

        BlockState state = mc.world.getBlockState(targetPos);
        float d = state.calcBlockBreakingDelta(mc.player, mc.world, targetPos);

        if (d <= 0f && state.getBlock() == Blocks.BEDROCK) {
            // render-only estimate so the bar moves when your server lets bedrock break
            d = 1f / Math.max(1, bedrockProgressEstimateTicks.get());
        }

        // For non-bedrock unbreakable or weird states, if delta==0, don't move.
        breakProgress = MathHelper.clamp(breakProgress + d, 0.0, 1.0);

        // Reset after "complete" so next block animates from 0
        if (breakProgress >= 1.0) {
            breakProgress = 0.0;
            lastBreakPos = null;
        }
    }

    /**
     * Bedrock priority; fall back to solids unless bedrock-only is on.
     */
    private BlockPos findCityBlock(PlayerEntity target) {
        BlockPos pos = target.getBlockPos();

        // Pass 1: bedrock
        if (mineBedrock.get()) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos off = pos.offset(dir);
                if (PlayerUtils.squaredDistanceTo(off) > breakRange.get() * breakRange.get()) continue;
                if (mc.world.getBlockState(off).getBlock() == Blocks.BEDROCK) return off;
            }
        }

        if (bedrockOnly.get()) return null;

        // Pass 2: any solid (non-air, non-liquid, not bedrock)
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos off = pos.offset(dir);
            if (PlayerUtils.squaredDistanceTo(off) > breakRange.get() * breakRange.get()) continue;
            if (!mc.world.getFluidState(off).isEmpty()) continue;
            Block b = mc.world.getBlockState(off).getBlock();
            if (b == Blocks.AIR || b == Blocks.BEDROCK) continue;
            return off;
        }
        return null;
    }

    private boolean isNaked(PlayerEntity p) {
        return p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
            && p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
            && p.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
            && p.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.get(DataComponentTypes.FOOD) != null;
    }

    // ----------gradient render----------
    private Color getColor(SettingColor start, SettingColor end, double progress, double alphaMulti) {
        return new Color(
            lerp(start.r, end.r, progress, 1),
            lerp(start.g, end.g, progress, 1),
            lerp(start.b, end.b, progress, 1),
            lerp(start.a, end.a, progress, alphaMulti)
        );
    }

    private int lerp(double start, double end, double d, double multi) {
        return (int) Math.round((start + (end - start) * d) * multi);
    }

    private Box getRenderBox(double progress) {
        double cx = targetPos.getX() + 0.5;
        double cy = targetPos.getY() + 0.5;
        double cz = targetPos.getZ() + 0.5;
        double r  = MathHelper.clamp(progress, 0.0, 0.5);
        return new Box(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (targetPos == null) return;

        // keep a gentle alpha pulse like the reference's two-pass look
        long now = System.currentTimeMillis();
        double dt = (now - lastTimeMs) / 1000d;
        lastTimeMs = now;
        renderPulse += dt * 2 * renderDir;
        if (renderPulse >= 2) { renderPulse = 2; renderDir = -1; }
        if (renderPulse <= -2){ renderPulse = -2; renderDir =  1; }

        // progress based on actual mining delta (synced)
        double base = MathHelper.clamp(breakProgress, 0.0, 1.0);
        // match reference easing: p = 1 - (1 - base)^exp
        double p = 1.0 - Math.pow(1.0 - base, animationExp.get());

        Renderer3D r = event.renderer;

        // Pass 1
        r.box(
            getRenderBox(p / 2.0),
            getColor(startColor.get(),     endColor.get(),     p, MathHelper.clamp(renderPulse,   0, 1)),
            getColor(lineStartColor.get(), lineEndColor.get(), p, MathHelper.clamp(renderPulse,   0, 1)),
            shapeMode.get(), 0
        );

        // Pass 2 (inverted alpha)
        r.box(
            getRenderBox(p / 2.0),
            getColor(startColor.get(),     endColor.get(),     p, MathHelper.clamp(-renderPulse,  0, 1)),
            getColor(lineStartColor.get(), lineEndColor.get(), p, MathHelper.clamp(-renderPulse,  0, 1)),
            shapeMode.get(), 0
        );
    }
}
