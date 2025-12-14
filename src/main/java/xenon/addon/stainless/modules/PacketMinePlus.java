package xenon.addon.stainless.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;

public class PacketMinePlus extends StainlessModule {

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum mining range.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate to block when mining.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> breakProgress = sgGeneral.add(new DoubleSetting.Builder()
        .name("break-progress")
        .description("When to send break packet (1.0 = 100%).")
        .defaultValue(0.95)
        .min(0.7)
        .max(1.0)
        .sliderMin(0.7)
        .sliderMax(1.0)
        .build()
    );

    private final Setting<Boolean> doubleBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("double-break")
        .description("Mine two blocks at once.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically switch to best tool.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silentSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-switch")
        .description("Switch tools via packets only (no visual change).")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Boolean> reBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("re-break")
        .description("Automatically re-mine blocks that reappear.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instant = sgGeneral.add(new BoolSetting.Builder()
        .name("instant")
        .description("Instantly re-mine blocks (faster).")
        .defaultValue(false)
        .visible(reBreak::get)
        .build()
    );

    private final Setting<Integer> instantDelay = sgGeneral.add(new IntSetting.Builder()
        .name("instant-delay")
        .description("Delay in ms before instant re-break.")
        .defaultValue(0)
        .min(0)
        .sliderMax(500)
        .visible(() -> reBreak.get() && instant.get())
        .build()
    );

    private final Setting<Boolean> resetOnSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("reset-on-switch")
        .description("Reset mining progress when switching slots.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info")
        .description("Send mining info to chat.")
        .defaultValue(false)
        .build()
    );

    // ==================== STATE ====================
    private MiningData currentMining;
    private MiningData previousMining;
    private long instantTimer = 0;

    // ==================== CONSTRUCTOR ====================
    public PacketMinePlus() {
        super(Stainless.STAINLESS_CATEGORY, "PacketMinePlus",
            "Packet-based mining system for better server compatibility.");
    }

    // ==================== LIFECYCLE ====================
    @Override
    public void onActivate() {
        super.onActivate();
        reset();
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        if (currentMining != null) {
            abortMining(currentMining);
        }
        if (previousMining != null) {
            abortMining(previousMining);
        }
        reset();
    }

    private void reset() {
        currentMining = null;
        previousMining = null;
        instantTimer = 0;
    }

    // ==================== EVENT HANDLERS ====================
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Update current mining
        if (currentMining != null) {
            if (!isInRange(currentMining.pos)) {
                abortMining(currentMining);
                currentMining = null;
                if (chatInfo.get()) info("Aborted mining: out of range");
            } else {
                updateMining(currentMining);
            }
        }

        // Update previous mining (double break)
        if (previousMining != null) {
            if (!isInRange(previousMining.pos)) {
                previousMining = null;
            } else {
                updateMining(previousMining);
            }
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof BlockUpdateS2CPacket packet)) return;

        // Handle re-break
        if (currentMining != null &&
            packet.getPos().equals(currentMining.pos) &&
            !packet.getState().isReplaceable() &&
            currentMining.shouldRemine) {

            if (chatInfo.get()) info("Re-breaking block");
            startMining(currentMining.pos, currentMining.direction, true);
        }

        // Clear previous mining when block breaks
        if (previousMining != null &&
            packet.getPos().equals(previousMining.pos) &&
            packet.getState().isAir()) {

            previousMining = null;
        }

        // Clear current mining when block breaks
        if (currentMining != null &&
            packet.getPos().equals(currentMining.pos) &&
            packet.getState().isAir() &&
            !currentMining.shouldRemine) {

            currentMining = null;
        }
    }

    // ==================== PUBLIC API ====================

    /**
     * Start mining a block at the given position
     */
    public void startMining(BlockPos pos, Direction direction) {
        startMining(pos, direction, false);
    }

    /**
     * Start mining with optional reset
     */
    public void startMining(BlockPos pos, Direction direction, boolean reset) {
        if (pos == null || direction == null) return;
        if (!canMine(pos)) return;
        if (!reset && mc.world.getBlockState(pos).isAir()) return;
        if (!isInRange(pos)) return;

        boolean shouldReset = reset || currentMining == null || !pos.equals(currentMining.pos);

        if (shouldReset) {
            // Handle double break
            if (doubleBreak.get() &&
                currentMining != null &&
                previousMining == null &&
                !mc.world.getBlockState(currentMining.pos).isAir()) {

                previousMining = currentMining;
                if (chatInfo.get()) info("Started double break");
            }

            // Create new mining data
            currentMining = new MiningData(pos, direction);

            // Send start packets
            sendStartPackets(pos, direction);

            if (chatInfo.get()) {
                info("Started mining at " + pos.toShortString());
            }
        }
    }

    /**
     * Stop mining current block
     */
    public void stopMining() {
        if (currentMining != null) {
            abortMining(currentMining);
            currentMining = null;
        }
        if (previousMining != null) {
            abortMining(previousMining);
            previousMining = null;
        }
    }

    /**
     * Check if we can mine at this position
     */
    public boolean canMine(BlockPos pos) {
        if (mc.player == null || mc.world == null) return false;
        if (mc.player.isCreative()) return false;

        BlockState state = mc.world.getBlockState(pos);
        if (state.getHardness(mc.world, pos) < 0) return false;

        // Don't double-mine same position
        if (doubleBreak.get() &&
            previousMining != null &&
            previousMining.pos.equals(pos)) {
            return false;
        }

        return true;
    }

    /**
     * Get current mining position (or null)
     */
    public BlockPos getCurrentPos() {
        return currentMining != null ? currentMining.pos : null;
    }

    /**
     * Get previous mining position (or null)
     */
    public BlockPos getPreviousPos() {
        return previousMining != null ? previousMining.pos : null;
    }

    /**
     * Check if actively mining
     */
    public boolean isMining() {
        return currentMining != null && !mc.world.getBlockState(currentMining.pos).isAir();
    }

    /**
     * Check if mining is ready to break (progress >= threshold)
     */
    public boolean isReady() {
        return currentMining != null && currentMining.damage >= breakProgress.get();
    }

    /**
     * Get mining progress (0.0 to 1.0)
     */
    public double getProgress() {
        if (currentMining == null) return 0;
        return Math.min(currentMining.damage, 1.0);
    }

    /**
     * Get normalized progress (0.0 to 1.0 based on break threshold)
     */
    public double getNormalizedProgress() {
        if (currentMining == null) return 0;
        return Math.min(currentMining.damage / breakProgress.get(), 1.0);
    }

    // ==================== MINING LOGIC ====================

    private void updateMining(MiningData data) {
        if (data == null) return;

        // Calculate damage this tick using best tool
        double delta = calculateBlockBreakingDelta(data.pos);
        data.damage += delta;
        data.damage = Math.min(data.damage, 1.0);

        // Check if ready to break
        if (data.damage >= breakProgress.get()) {
            // Check instant re-break timer
            if (data.shouldRemine && System.currentTimeMillis() - instantTimer < instantDelay.get()) {
                return;
            }

            attemptBreak(data);
        }
    }

    private void attemptBreak(MiningData data) {
        int originalSlot = mc.player.getInventory().getSelectedSlot();
        int toolSlot = -1;

        // Find and switch to best tool
        if (autoSwitch.get()) {
            BlockState state = mc.world.getBlockState(data.pos);
            FindItemResult tool = InvUtils.findFastestTool(state);
            if (tool.found() && tool.isHotbar() && tool.slot() != originalSlot) {
                toolSlot = tool.slot();

                if (silentSwitch.get()) {
                    // Silent: send packet only, don't update client slot
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(toolSlot));
                } else {
                    // Normal: actually switch slot
                    InvUtils.swap(toolSlot, false);
                }
            }
        }

        // Rotate if needed
        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(data.pos), Rotations.getPitch(data.pos));
        }

        // Send break packets
        sendBreakPackets(data.pos, data.direction);

        // Swap back
        if (toolSlot != -1) {
            if (silentSwitch.get()) {
                // Silent: send packet to restore original slot
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            } else {
                // Normal: actually switch back
                InvUtils.swap(originalSlot, false);
            }
        }

        if (chatInfo.get()) {
            info("Broke block at " + data.pos.toShortString());
        }

        // Handle re-break
        if (!reBreak.get()) {
            if (data == currentMining) {
                currentMining = null;
            }
        } else if (instant.get()) {
            // Instant re-break: immediately restart mining
            data.shouldRemine = true;
            data.damage = 0; // Reset to 0 for instant restart
            instantTimer = System.currentTimeMillis();

            // Send start packets immediately for instant re-mine
            sendStartPackets(data.pos, data.direction);
        } else {
            // Reset and re-start
            startMining(data.pos, data.direction, true);
        }
    }

    private void abortMining(MiningData data) {
        if (data == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
            data.pos,
            data.direction
        ));
    }

    // ==================== PACKET SENDING ====================

    private void sendStartPackets(BlockPos pos, Direction direction) {
        int originalSlot = mc.player.getInventory().getSelectedSlot();
        int toolSlot = -1;

        // Find and switch to best tool for start packets
        if (autoSwitch.get()) {
            BlockState state = mc.world.getBlockState(pos);
            FindItemResult tool = InvUtils.findFastestTool(state);
            if (tool.found() && tool.isHotbar() && tool.slot() != originalSlot) {
                toolSlot = tool.slot();

                if (silentSwitch.get()) {
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(toolSlot));
                } else {
                    InvUtils.swap(toolSlot, false);
                }
            }
        }

        if (doubleBreak.get()) {
            // Double break sequence
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction));
        } else {
            // Single break
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction));
        }

        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        // Swap back after sending start packets
        if (toolSlot != -1) {
            if (silentSwitch.get()) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            } else {
                InvUtils.swap(originalSlot, false);
            }
        }
    }

    private void sendBreakPackets(BlockPos pos, Direction direction) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction));

        if (!doubleBreak.get()) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, direction));
        }

        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    // ==================== CALCULATIONS ====================

    /**
     * Calculate block breaking delta using the best available tool
     */
    private double calculateBlockBreakingDelta(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        float hardness = state.getHardness(mc.world, pos);

        if (hardness == -1.0f) return 0;

        // Find best tool for accurate progress calculation
        ItemStack tool = mc.player.getMainHandStack();
        if (autoSwitch.get()) {
            FindItemResult bestTool = InvUtils.findFastestTool(state);
            if (bestTool.found() && bestTool.isHotbar()) {
                tool = mc.player.getInventory().getStack(bestTool.slot());
            }
        }

        int i = canHarvest(state, tool) ? 30 : 100;
        return getBlockBreakingSpeed(state, tool) / hardness / (float) i;
    }

    private boolean canHarvest(BlockState state, ItemStack tool) {
        // Check if block doesn't require a tool
        if (!state.isToolRequired()) return true;

        return tool.isSuitableFor(state);
    }

    private int getEnchantmentLevel(ItemStack stack, String enchantmentName) {
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
        for (var entry : enchantments.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> enchantment = entry.getKey();
            String description = enchantment.getIdAsString();
            if (description.toLowerCase().contains(enchantmentName.toLowerCase())) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private float getBlockBreakingSpeed(BlockState block, ItemStack tool) {
        float speed = tool.getMiningSpeedMultiplier(block);

        if (speed > 1.0f) {
            int efficiency = getEnchantmentLevel(tool, "efficiency");

            if (efficiency > 0 && !tool.isEmpty()) {
                speed += efficiency * efficiency + 1;
            }
        }

        if (StatusEffectUtil.hasHaste(mc.player)) {
            speed *= 1.0f + (StatusEffectUtil.getHasteAmplifier(mc.player) + 1) * 0.2f;
        }

        if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            int amplifier = mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier();
            float k = switch (amplifier) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 8.1E-4f;
            };
            speed *= k;
        }

        if (mc.player.isSubmergedIn(FluidTags.WATER)) {
            // Check for Aqua Affinity enchantment
            ItemStack helmet = mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
            boolean hasAquaAffinity = getEnchantmentLevel(helmet, "aqua_affinity") > 0;

            if (!hasAquaAffinity) {
                speed /= 5.0f;
            }
        }

        if (!mc.player.isOnGround()) {
            speed /= 5.0f;
        }

        return speed;
    }

    private boolean isInRange(BlockPos pos) {
        double rangeSq = range.get() * range.get();
        return mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq;
    }

    // ==================== MINING DATA ====================

    private static class MiningData {
        final BlockPos pos;
        final Direction direction;
        double damage = 0;
        boolean shouldRemine = false;

        MiningData(BlockPos pos, Direction direction) {
            this.pos = pos.toImmutable();
            this.direction = direction;
        }
    }
}
