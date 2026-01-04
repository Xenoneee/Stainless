package xenon.addon.stainless.modules;

import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoTntPlus extends StainlessModule {
    public AutoTntPlus() {
        super(Stainless.STAINLESS_CATEGORY, "AutoTntPlus",
            "Automatically drop tnt on enemies.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDelays = settings.createGroup("Delays");
    private final SettingGroup sgIgnition = settings.createGroup("Ignition");
    private final SettingGroup sgPlacement = settings.createGroup("Placement");

    // -------------------- General Settings --------------------
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range").defaultValue(4).min(0).sliderMax(6).build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").defaultValue(true).build());

    // -------------------- Delays --------------------
    private final Setting<Integer> pillarDelay = sgDelays.add(new IntSetting.Builder()
        .name("pillar-delay").defaultValue(30).min(0).sliderMax(100).build());

    private final Setting<Integer> tntDelay = sgDelays.add(new IntSetting.Builder()
        .name("tnt-delay").defaultValue(50).min(0).sliderMax(100).build());

    private final Setting<Integer> ignitionDelay = sgDelays.add(new IntSetting.Builder()
        .name("ignition-delay").defaultValue(1).min(0).sliderMax(20).build());

    // -------------------- Ignition --------------------
    private final Setting<Boolean> useFlintAndSteel = sgIgnition.add(new BoolSetting.Builder()
        .name("Flint & Steel").defaultValue(true).build());

    private final Setting<Boolean> useFireCharge = sgIgnition.add(new BoolSetting.Builder()
        .name("Fire Charge").defaultValue(false).build());

    // -------------------- Placement --------------------
    private final Setting<Boolean> airPlace = sgPlacement.add(new BoolSetting.Builder()
        .name("Air Place").defaultValue(false).build());

    private final Setting<Boolean> placeSupport = sgPlacement.add(new BoolSetting.Builder()
        .name("Place Support").defaultValue(true).build());

    // -------------------- State --------------------
    private PlayerEntity target;
    private BlockPos basePos;
    private BlockPos tntPos;
    private BlockPos lastTargetPos;
    private Direction placedDirection;
    private int currentPillarHeight = 2;
    private int cooldown = 0;
    private int igniteTicks = 0;

    // Chat cooldowns (2 seconds = 40 ticks)
    private int tntCooldown = 0;
    private int obsidianCooldown = 0;
    private int igniterCooldown = 0;
    private int movedCooldown = 0;

    @Override
    public void onActivate() {
        reset();
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.screen instanceof AnvilScreen) event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        decrementChatCooldowns();

        if (updateTarget()) return;

        handleTargetMovement();

        FindItemResult obsidian = InvUtils.findInHotbar(stack -> Block.getBlockFromItem(stack.getItem()) == Blocks.OBSIDIAN);
        FindItemResult tnt = InvUtils.findInHotbar(stack -> Block.getBlockFromItem(stack.getItem()) == Blocks.TNT);

        if (checkTNT(tnt)) return;

        if (checkSupport(obsidian)) return;

        handleAirPlace();

        handleSupportPlacement(obsidian);

        placeTNT(tnt);

        handleIgnition();
    }

    private void reset() {
        basePos = null;
        tntPos = null;
        placedDirection = null;
        lastTargetPos = null;
        currentPillarHeight = 2;
        cooldown = 0;
        igniteTicks = 0;
    }

    private void decrementChatCooldowns() {
        if (tntCooldown > 0) tntCooldown--;
        if (obsidianCooldown > 0) obsidianCooldown--;
        if (igniterCooldown > 0) igniterCooldown--;
        if (movedCooldown > 0) movedCooldown--;
    }

    private boolean updateTarget() {
        if (TargetUtils.isBadTarget(target, range.get())) {
            target = TargetUtils.getPlayerTarget(range.get(), SortPriority.LowestHealth);
            if (TargetUtils.isBadTarget(target, range.get())) return true;
            reset();
        }
        return false;
    }

    private void handleTargetMovement() {
        BlockPos targetPos = target.getBlockPos();

        if (lastTargetPos != null && !lastTargetPos.equals(targetPos)) {
            if (movedCooldown == 0) {
                info("Target moved. Resetting.");
                movedCooldown = 40;
            }
            reset();
        }
        lastTargetPos = targetPos;
    }

    private boolean checkTNT(FindItemResult tnt) {
        if (!tnt.found()) {
            if (tntCooldown == 0) {
                warning("No TNT found in hotbar!");
                tntCooldown = 40;
            }
            return true;
        }
        return false;
    }

    private boolean checkSupport(FindItemResult obsidian) {
        if (!airPlace.get() && !obsidian.found() && placeSupport.get()) {
            if (obsidianCooldown == 0) {
                warning("Missing obsidian for pillar.");
                obsidianCooldown = 40;
            }
            return true;
        }
        return false;
    }

    private void handleAirPlace() {
        if (airPlace.get()) {
            tntPos = target.getBlockPos().up(2);
        }
    }

    private void handleSupportPlacement(FindItemResult obsidian) {
        if (!placeSupport.get()) return;

        if (placedDirection == null || basePos == null || tntPos == null) {
            findSurroundPosition();
        }

        if (basePos == null || tntPos == null || placedDirection == null) return;

        for (int i = 0; i < currentPillarHeight; i++) {
            BlockPos pos = basePos.up(i);
            if (!mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN)) {
                BlockUtils.place(pos, obsidian, 0);
                cooldown = pillarDelay.get();
                return;
            }
        }
    }

    private void findSurroundPosition() {
        BlockPos targetPos = target.getBlockPos();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos surround = targetPos.offset(dir);
            BlockPos base = surround.up();

            boolean is2Tall = mc.world.getBlockState(base).isOf(Blocks.OBSIDIAN)
                && mc.world.getBlockState(base.up()).isOf(Blocks.OBSIDIAN);
            if (is2Tall || !mc.world.getBlockState(surround).isAir()) {
                placedDirection = dir;
                basePos = base;
                currentPillarHeight = 2;
                tntPos = targetPos.up(currentPillarHeight);
                break;
            }
        }
    }

    private void placeTNT(FindItemResult tnt) {
        if (tntPos != null && mc.world.getBlockState(tntPos).isReplaceable()) {
            BlockUtils.place(tntPos, tnt, 0);
            cooldown = tntDelay.get();
            igniteTicks = 0;
        }
    }

    private void handleIgnition() {
        if (igniteTicks >= ignitionDelay.get()) {
            igniteNearbyTNT();
            igniteTicks = 0;
        }
        igniteTicks++;
    }

    private void igniteNearbyTNT() {
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() instanceof net.minecraft.block.TntBlock) {
                        FindItemResult item = InvUtils.findInHotbar(stack -> {
                            if (stack.getItem() instanceof FlintAndSteelItem) {
                                return useFlintAndSteel.get();
                            } else if (stack.getItem() instanceof FireChargeItem) {
                                return useFireCharge.get();
                            }
                            return false;
                        });

                        if (!item.found()) {
                            if (igniterCooldown == 0) {
                                warning("No igniter found!");
                                igniterCooldown = 40;
                            }
                            return;
                        }

                        InvUtils.swap(item.slot(), false);
                        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);

                        if (rotate.get()) lookAt(pos);

                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        return;
                    }
                }
            }
        }
    }

    private void lookAt(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        mc.player.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, center);
    }

    @Override
    public String getInfoString() {
        return target != null ? EntityUtils.getName(target) : null;
    }
}
