package xenon.addon.stainless.modules;

import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class AutoPearlThrow extends StainlessModule {
    // ----- Groups -----
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAiming  = settings.createGroup("Aiming");
    private final SettingGroup sgSafety  = settings.createGroup("Safety");
    private final SettingGroup sgInv     = settings.createGroup("Inventory");
    private final SettingGroup sgDebug   = settings.createGroup("Debug");

    // ----- General -----
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").description("Rotate your view to aim the pearl.").defaultValue(true).build());

    private final Setting<Double> pitchUpDegrees = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch-up").description("Base upward pitch (deg) when Auto Pitch is OFF.")
        .defaultValue(35.0).min(0.0).max(89.0).sliderMin(0.0).sliderMax(80.0).build());

    private final Setting<Integer> throwDelayMs = sgGeneral.add(new IntSetting.Builder()
        .name("throw-delay-ms").description("Delay after pop before throwing.")
        .defaultValue(120).min(0).max(2000).build());

    private final Setting<Integer> cooldownMs = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ms").description("Minimum time between throws.")
        .defaultValue(1000).min(100).max(5000).build());

    private final Setting<Boolean> silentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-swap").description("Temporarily swap to pearls, then back.")
        .defaultValue(true).build());

    private final Setting<Boolean> preferOffhand = sgGeneral.add(new BoolSetting.Builder()
        .name("prefer-offhand").description("Use offhand pearls if available.")
        .defaultValue(true).build());

    private final Setting<Boolean> jumpOnThrow = sgGeneral.add(new BoolSetting.Builder()
        .name("jump-on-throw").description("Jump right before throwing to see over 2-block walls.")
        .defaultValue(true).build());

    private final Setting<Integer> jumpWaitMs = sgGeneral.add(new IntSetting.Builder()
        .name("jump-wait-ms").description("Wait this long after jumping before aiming/throwing.")
        .defaultValue(180).min(50).max(400).visible(jumpOnThrow::get).build());

    // Reserve Totems
    private final Setting<Boolean> reserveTotems = sgGeneral.add(new BoolSetting.Builder()
        .name("reserve-totems")
        .description("Skip scheduling/throwing when total totems are at or below the set amount.")
        .defaultValue(true).build());

    private final Setting<Integer> reserveTotemCount = sgGeneral.add(new IntSetting.Builder()
        .name("reserve-count")
        .description("Keep at least this many totems (inv + hotbar + offhand + cursor).")
        .defaultValue(2).min(1).max(10).sliderMin(1).sliderMax(6)
        .visible(reserveTotems::get).build());

    // ----- Aiming -----
    private final Setting<Boolean> autoBackThrowPitch = sgAiming.add(new BoolSetting.Builder()
        .name("auto-pitch").description("Auto-pick the flattest pitch that lands within the distance window.")
        .defaultValue(true).build());

    private final Setting<Boolean> aimFullCircle = sgAiming.add(new BoolSetting.Builder()
        .name("allow-360-aim").description("Search 360° for the best yaw (not just behind you).")
        .defaultValue(true).build());

    private final Setting<Boolean> avoidEnemyCone = sgAiming.add(new BoolSetting.Builder()
        .name("avoid-enemy-cone").description("Skip a cone toward the nearest enemy.")
        // Recommended preset uses OFF by default for reliability.
        .defaultValue(false).visible(aimFullCircle::get).build());

    private final Setting<Double> enemyConeDegrees = sgAiming.add(new DoubleSetting.Builder()
        .name("enemy-cone-deg").description("Width of the forbidden cone toward the nearest enemy.")
        .defaultValue(80.0).min(20.0).max(140.0).sliderMin(40.0).sliderMax(120.0)
        .visible(() -> aimFullCircle.get() && avoidEnemyCone.get()).build());

    private final Setting<Double> minBackDist = sgAiming.add(new DoubleSetting.Builder()
        .name("min-escape-distance").description("Minimum landing distance (blocks) from your position.")
        .defaultValue(6.0).min(3.0).max(60.0).sliderMin(4.0).sliderMax(40.0).build());

    private final Setting<Double> maxBackDist = sgAiming.add(new DoubleSetting.Builder()
        .name("max-escape-distance").description("Maximum landing distance (blocks) from your position.")
        .defaultValue(22.0).min(8.0).max(70.0).sliderMin(12.0).sliderMax(50.0).build());

    // ----- Safety -----
    private final Setting<Double> clearCheckDistance = sgSafety.add(new DoubleSetting.Builder()
        .name("clear-check-distance").description("Distance along aim direction to check for blocking walls.")
        .defaultValue(3.0).min(1.0).max(10.0).sliderMin(2.0).sliderMax(6.0).build());

    private final Setting<Boolean> trySideOffsets = sgSafety.add(new BoolSetting.Builder()
        .name("try-side-offsets").description("If legacy fallback is used, try yaw offsets (±20°).")
        .defaultValue(true).build());

    private final Setting<Boolean> escalatePitch = sgSafety.add(new BoolSetting.Builder()
        .name("escalate-pitch").description("If blocked, increase pitch (more up) to clear obstructions.")
        .defaultValue(true).build());

    private final Setting<Boolean> fallbackStraightUp = sgSafety.add(new BoolSetting.Builder()
        .name("fallback-straight-up").description("If still blocked, throw straight up as a last resort.")
        .defaultValue(true).build());

    private final Setting<Double> nearPathCheck = sgSafety.add(new DoubleSetting.Builder()
        .name("near-path-check").description("Checks the first N blocks of the pearl’s path for collisions.")
        .defaultValue(3.0).min(1.0).max(8.0).sliderMin(2.0).sliderMax(6.0).build());

    private final Setting<Double> initialClearance = sgSafety.add(new DoubleSetting.Builder()
        .name("initial-clearance")
        .description("Minimum clear distance from your eyes along the aim ray. Helps when touching a wall.")
        .defaultValue(0.8).min(0.2).max(1.5).sliderMin(0.5).sliderMax(1.2).build());

    private final Setting<Double> corridorCheckDist = sgSafety.add(new DoubleSetting.Builder()
        .name("corridor-check-distance")
        .description("How far ahead to verify the corridor’s width (blocks).")
        .defaultValue(2.0).min(1.0).max(6.0).sliderMin(1.0).sliderMax(5.0).build());

    private final Setting<Double> corridorHalfWidth = sgSafety.add(new DoubleSetting.Builder()
        .name("corridor-half-width")
        .description("Half width that must be clear on both sides of the throw ray (blocks). 0.5 ≈ one full block.")
        .defaultValue(0.30).min(0.25).max(1.0).sliderMin(0.25).sliderMax(0.8).build());

    private final Setting<Double> enemyCorridorBoost = sgSafety.add(new DoubleSetting.Builder()
        .name("enemy-corridor-boost")
        .description("Extra half-width required when aiming near the enemy cone.")
        .defaultValue(0.20).min(0.0).max(0.6).sliderMin(0.0).sliderMax(0.4)
        .visible(() -> aimFullCircle.get() && avoidEnemyCone.get()).build());

    private final Setting<Double> enemyConeSoftPad = sgSafety.add(new DoubleSetting.Builder()
        .name("enemy-cone-soft-pad")
        .description("Additional degrees around the enemy cone treated as ‘near enemy’ for corridor boost.")
        .defaultValue(12.0).min(0.0).max(30.0).sliderMin(6.0).sliderMax(24.0)
        .visible(() -> aimFullCircle.get() && avoidEnemyCone.get()).build());

    private final Setting<Boolean> cancelIfNoEscape = sgSafety.add(new BoolSetting.Builder()
        .name("cancel-if-no-escape")
        .description("Skip throwing entirely if no direction can reach the minimum escape distance.")
        .defaultValue(false).build()); // preset = OFF

    private final Setting<Double> probeStepDegrees = sgSafety.add(new DoubleSetting.Builder()
        .name("probe-step-deg")
        .description("Yaw step when probing for escape (coarse scan).")
        .defaultValue(20.0).min(10.0).max(45.0).sliderMin(15.0).sliderMax(30.0)
        .visible(cancelIfNoEscape::get).build());

    private final Setting<Integer> probePitchCount = sgSafety.add(new IntSetting.Builder()
        .name("probe-pitch-count")
        .description("How many flat-ish pitches to probe per yaw.")
        .defaultValue(3).min(1).max(6)
        .visible(cancelIfNoEscape::get).build());

    // NEW: start bias & ray step
    private final Setting<Double> startBias = sgSafety.add(new DoubleSetting.Builder()
        .name("start-bias")
        .description("How far forward (blocks) to bias the ray start from your eyes. Helps throw through tight side gaps.")
        .defaultValue(0.25).min(0.0).max(0.6).sliderMin(0.0).sliderMax(0.4).build());

    private final Setting<Double> rayStep = sgSafety.add(new DoubleSetting.Builder()
        .name("ray-step")
        .description("Step (blocks) for the early-collision simulator. Larger = fewer checks.")
        .defaultValue(0.6).min(0.3).max(0.9).sliderMin(0.4).sliderMax(0.8).build());

    // ----- Inventory -----
    private final Setting<Boolean> pullFromInventory = sgInv.add(new BoolSetting.Builder()
        .name("pull-from-inventory").description("If no pearls in hotbar, pull from main inventory.")
        .defaultValue(true).build());

    private final Setting<Boolean> preferEmptyHotbar = sgInv.add(new BoolSetting.Builder()
        .name("prefer-empty-hotbar").description("Prefer an empty hotbar slot when pulling.")
        .defaultValue(true).visible(pullFromInventory::get).build());

    private final Setting<Integer> tempHotbarSlot = sgInv.add(new IntSetting.Builder()
        .name("temp-hotbar-slot").description("Fallback hotbar slot [0–8] when pulling.")
        .defaultValue(8).min(0).max(8).visible(pullFromInventory::get).build());

    // ----- Debug -----
    private final Setting<Boolean> debug = sgDebug.add(new BoolSetting.Builder()
        .name("debug").description("Log detection and throw steps.").defaultValue(false).build());

    // Replaces Keybind/KeybindSetting
    private final Setting<Boolean> testNow = sgDebug.add(new BoolSetting.Builder()
        .name("test-now")
        .description("Toggle to schedule a throw once (auto-resets).")
        .defaultValue(false).build());

    // ----- State -----
    private boolean pendingThrow = false;
    private boolean jumpedThisCycle = false;
    private long scheduleAt = 0L;
    private long lastThrowAt = 0L;

    public AutoPearlThrow() {
        super(Stainless.STAINLESS_CATEGORY, "AutoPearlThrow", "Automatically throws pearl on totem pop.");
    }

    @Override public void onDeactivate() {
        pendingThrow = false; jumpedThisCycle = false; scheduleAt = 0L;
    }

    // ===== Triggers =====
    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket p)) return;
        if (mc.player == null || mc.world == null) return;
        if (p.getStatus() == 35 && p.getEntity(mc.world) == mc.player) {
            long now = System.currentTimeMillis();
            if (now - lastThrowAt < cooldownMs.get()) { if (debug.get()) info("Pop: on cooldown."); return; }

            // Reserve guard
            if (reserveTotems.get() && countTotemsAll() <= reserveTotemCount.get()) {
                if (debug.get()) info("Reserve totems reached (" + countTotemsAll() + ") — skipping throw.");
                return;
            }

            if (debug.get()) info("Totem pop -> schedule throw.");
            scheduleThrow(now + throwDelayMs.get());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Reserve guard
        if (reserveTotems.get() && countTotemsAll() <= reserveTotemCount.get()) {
            if (pendingThrow) { pendingThrow = false; jumpedThisCycle = false; }
            if (debug.get()) info("Reserve totems reached (" + countTotemsAll() + ") — skipping.");
            return;
        }

        // Manual test (toggle)
        if (testNow.get() && !pendingThrow) {
            if (debug.get()) info("Manual test -> scheduling throw.");
            scheduleThrow(System.currentTimeMillis() + 75);
            testNow.set(false); // auto-reset
        }

        if (!pendingThrow || mc.interactionManager == null) return;
        long now = System.currentTimeMillis();
        if (now < scheduleAt) return;

        // Jump then wait
        if (jumpOnThrow.get() && !jumpedThisCycle && mc.player.isOnGround()) {
            mc.player.jump();
            jumpedThisCycle = true;
            scheduleAt = now + jumpWaitMs.get();
            if (debug.get()) info("Jumped; waiting " + jumpWaitMs.get() + "ms.");
            return;
        }

        // Hand / slot plan
        Hand handToUse = Hand.MAIN_HAND;
        FindItemResult hotbarPearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        boolean useOffhand = preferOffhand.get() && mc.player.getOffHandStack().isOf(Items.ENDER_PEARL);

        boolean didSwap = false, didMoveFromInv = false;
        int movedFromSlot = -1, movedToHotbar = -1;

        if (!useOffhand && !hotbarPearl.found() && pullFromInventory.get()) {
            FindItemResult anyPearl = InvUtils.find(Items.ENDER_PEARL);
            if (anyPearl.found()) {
                int toHotbar = pickHotbarSlot();
                if (debug.get()) info("Pulling pearl inv " + anyPearl.slot() + " -> hotbar " + toHotbar);
                InvUtils.move().from(anyPearl.slot()).toHotbar(toHotbar);
                didMoveFromInv = true; movedFromSlot = anyPearl.slot(); movedToHotbar = toHotbar;
                hotbarPearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
            }
        }

        if (!useOffhand && !hotbarPearl.found()) {
            if (debug.get()) info("No pearls in hotbar and none pulled. Cancelling.");
            cleanupAfterCycle(false, false, didMoveFromInv, movedFromSlot, movedToHotbar);
            return;
        }

        if (!useOffhand) {
            if (silentSwap.get()) { InvUtils.swap(hotbarPearl.slot(), true); didSwap = true; }
            else { InvUtils.swap(hotbarPearl.slot(), false); }
            handToUse = Hand.MAIN_HAND;
        } else handToUse = Hand.OFF_HAND;

        // Aim selection
        Aim aim = pickAimSmart();
        if (aim == null) aim = pickAimLenient(); // lenient second pass

        if (aim == null) {
            if (debug.get()) info("All angles blocked or no escape — cancelled and restored.");
            cleanupAfterCycle(false, didSwap, didMoveFromInv, movedFromSlot, movedToHotbar);
            return;
        }

        final Hand fHand = handToUse;
        final boolean fDidSwap = didSwap;
        final boolean fDidMove = didMoveFromInv;
        final int fFrom = movedFromSlot, fTo = movedToHotbar;

        Runnable doThrow = () -> {
            mc.interactionManager.interactItem(mc.player, fHand);
            if (fDidSwap) InvUtils.swapBack();
            if (fDidMove && fTo >= 0 && fFrom >= 0) InvUtils.move().fromHotbar(fTo).to(fFrom);
            lastThrowAt = System.currentTimeMillis();
            cleanupAfterCycle(true, false, false, -1, -1);
            if (debug.get()) info("Pearl thrown.");
        };

        if (rotate.get()) Rotations.rotate(aim.yaw, aim.pitch, doThrow);
        else doThrow.run();
    }

    private void scheduleThrow(long atMillis) {
        pendingThrow = true; jumpedThisCycle = false; scheduleAt = atMillis;
    }

    private void cleanupAfterCycle(boolean success, boolean didSwap, boolean didMove, int fromSlot, int toHotbar) {
        if (didSwap) InvUtils.swapBack();
        if (didMove && toHotbar >= 0 && fromSlot >= 0) InvUtils.move().fromHotbar(toHotbar).to(fromSlot);
        pendingThrow = false; jumpedThisCycle = false;
        if (!success && debug.get()) info("Cycle ended (success=" + success + ").");
    }

    // ===== Aiming =====
    private static class Aim { final float yaw, pitch; Aim(float y, float p){yaw=y; pitch=p;} }
    private static float[] yawWiggles() { return new float[]{0f, +7f, -7f, +12f, -12f}; }

    private Aim pickAimSmart() {
        double minD = Math.min(minBackDist.get(), maxBackDist.get());
        double maxD = Math.max(minBackDist.get(), maxBackDist.get());

        if (cancelIfNoEscape.get()) {
            double best = bestReachableDistance(minD, maxD);
            if (best < minD) return null;
        }

        // Build yaw candidates
        float[] yawCandidates;
        float enemyYaw = getNearestEnemyYaw();
        boolean haveEnemy = !Float.isNaN(enemyYaw);

        if (aimFullCircle.get()) {
            java.util.List<Float> yaws = new java.util.ArrayList<>();
            for (int deg = 0; deg < 360; deg += 15) {
                float yaw = (float) deg - 180f;
                if (haveEnemy && avoidEnemyCone.get()) {
                    if (angleDelta(yaw, enemyYaw) <= (float)(enemyConeDegrees.get() / 2.0)) continue;
                }
                yaws.add(yaw);
            }
            if (yaws.isEmpty() && haveEnemy) yaws.add(wrapYaw(enemyYaw + 180f));
            yawCandidates = new float[yaws.size()];
            for (int i = 0; i < yaws.size(); i++) yawCandidates[i] = yaws.get(i);
        } else {
            float baseYaw = wrapYaw(mc.player.getYaw() + 180f);
            yawCandidates = trySideOffsets.get()
                ? new float[]{baseYaw, wrapYaw(baseYaw+20f), wrapYaw(baseYaw-20f)}
                : new float[]{baseYaw};
        }

        Float bestPitch = null, bestYaw = null;
        double bestTime = Double.POSITIVE_INFINITY;

        for (float yawBase : yawCandidates) {
            for (float wig : yawWiggles()) {
                float yaw = wrapYaw(yawBase + wig);

                Float pitch;
                if (autoBackThrowPitch.get()) {
                    pitch = findFlattestPitchForDistanceRangeWithGuards(
                        yaw, minD, maxD, clearCheckDistance.get(), nearPathCheck.get(), initialClearance.get());
                } else {
                    pitch = Float.valueOf(clampPitch((float) -pitchUpDegrees.get()));
                }
                if (pitch == null) continue;

                if (!hasInitialClearance(yaw, pitch, initialClearance.get())) continue;
                if (!hasClearPath(yaw, pitch, clearCheckDistance.get())) continue;
                if (hitsWallEarly(yaw, pitch, nearPathCheck.get())) continue;

                // Corridor width guard (horizontalized)
                double halfW = corridorHalfWidth.get();
                if (aimFullCircle.get() && avoidEnemyCone.get() && haveEnemy) {
                    float delta = angleDelta(yaw, enemyYaw);
                    float hard = (float)(enemyConeDegrees.get() / 2.0);
                    float soft = hard + enemyConeSoftPad.get().floatValue();
                    if (delta <= soft) halfW += enemyCorridorBoost.get();
                }
                if (!hasCorridorWidth(yaw, pitch, corridorCheckDist.get(), halfW)) continue;

                double t = estimateFlightTicks(yaw, pitch, 80);
                if (bestPitch == null
                    || Math.abs(pitch) < Math.abs(bestPitch)
                    || (Math.abs(pitch) == Math.abs(bestPitch) && t < bestTime)) {
                    bestPitch = pitch; bestYaw = yaw; bestTime = t;
                }
            }
        }

        if (bestPitch != null) return new Aim(bestYaw, bestPitch);

        // Legacy behind-you fallback (lenient)
        float baseYaw = wrapYaw(mc.player.getYaw() + 180f);
        float basePitch = (float) -pitchUpDegrees.get();
        float[] pitchAdds  = escalatePitch.get() ? new float[]{0f, +15f, +30f} : new float[]{0f};
        float[] yawOffsets = trySideOffsets.get() ? new float[]{0f, +20f, -20f} : new float[]{0f};
        for (float pAdd : pitchAdds) {
            float pitch = clampPitch(basePitch - pAdd);
            for (float yOff : yawOffsets) {
                float yaw = wrapYaw(baseYaw + yOff);
                if (hasInitialClearance(yaw, pitch, Math.max(0.6, initialClearance.get()))
                    && hasClearPath(yaw, pitch, Math.max(3.0, clearCheckDistance.get() - 1.0))
                    && !hitsWallEarly(yaw, pitch, Math.max(3.0, nearPathCheck.get() - 1.5))) {

                    if (hasCorridorWidth(yaw, pitch, Math.max(1.5, corridorCheckDist.get() - 1.0),
                        Math.max(0.45, corridorHalfWidth.get() - 0.1))) {
                        return new Aim(yaw, pitch);
                    }
                }
            }
        }

        // Straight-up fallback only if we truly can't reach min distance anywhere
        if (fallbackStraightUp.get()) {
            double best = cancelIfNoEscape.get() ? bestReachableDistance(minD, maxD) : 0.0;
            if (!cancelIfNoEscape.get() || best < minD * 0.9) {
                float fyaw = wrapYaw(mc.player.getYaw() + 180f);
                return new Aim(fyaw, -89.0f);
            }
        }
        return null;
    }

    // Lenient second pass (shorter checks, ignores corridor width)
    private Aim pickAimLenient() {
        double minD = Math.min(minBackDist.get(), maxBackDist.get());
        double maxD = Math.max(minBackDist.get(), maxBackDist.get());

        java.util.List<Float> yaws = new java.util.ArrayList<>();
        for (int deg = 0; deg < 360; deg += 20) yaws.add((float)deg - 180f);

        for (float yaw : yaws) {
            for (float pitch = -4f; pitch >= -80f; pitch -= 2f) {
                if (!hasInitialClearance(yaw, pitch, Math.max(0.6, initialClearance.get() - 0.3))) continue;
                double d = simulatePearlRange(yaw, pitch, 80);
                if (d < minD || d > maxD) continue;
                if (!hasClearPath(yaw, pitch, Math.max(3.0, clearCheckDistance.get() - 1.0))) continue;
                if (hitsWallEarly(yaw, pitch, Math.max(3.0, nearPathCheck.get() - 1.5))) continue;
                return new Aim(yaw, clampPitch(pitch));
            }
        }
        return null;
    }

    // Nearest non-self player yaw (deg) or NaN
    private float getNearestEnemyYaw() {
        if (mc.world == null || mc.player == null) return Float.NaN;
        PlayerEntity nearest = null; double best = Double.POSITIVE_INFINITY;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double d = p.squaredDistanceTo(mc.player);
            if (d < best) { best = d; nearest = p; }
        }
        if (nearest == null) return Float.NaN;

        Vec3d me = mc.player.getPos(), en = nearest.getPos();
        double dx = en.x - me.x, dz = en.z - me.z;
        float yawRad = (float) Math.atan2(-dx, dz);
        return (float) Math.toDegrees(yawRad);
    }

    private float angleDelta(float a, float b) { return Math.abs(wrapYaw(a - b)); }

    // ---- Clearance helpers (biased start) ----
    private Vec3d biasedStart(float yawDeg, float pitchDeg, double extra) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = directionFromYawPitch(yawDeg, pitchDeg);
        double bias = Math.max(0.0, startBias.get() + extra);
        return eye.add(dir.multiply(bias));
    }

    private boolean hasInitialClearance(float yawDeg, float pitchDeg, double dist) {
        Vec3d start = biasedStart(yawDeg, pitchDeg, 0.0);
        Vec3d dir = directionFromYawPitch(yawDeg, pitchDeg);
        Vec3d end = start.add(dir.multiply(Math.max(0.2, dist)));
        RaycastContext ctx = new RaycastContext(start, end,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        HitResult hit = mc.world.raycast(ctx);
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    private boolean hasClearPath(float yawDeg, float pitchDeg, double distance) {
        Vec3d start = biasedStart(yawDeg, pitchDeg, 0.0);
        Vec3d dir = directionFromYawPitch(yawDeg, pitchDeg);
        Vec3d end = start.add(dir.multiply(distance));
        RaycastContext ctx = new RaycastContext(start, end,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        HitResult hit = mc.world.raycast(ctx);
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    private boolean hitsWallEarly(float yawDeg, float pitchDeg, double checkBlocks) {
        if (mc.player == null) return false;
        Vec3d last = biasedStart(yawDeg, pitchDeg, 0.0);
        Vec3d vel = directionFromYawPitch(yawDeg, pitchDeg).multiply(1.5f);
        final double gravity = 0.03, drag = 0.99;
        double traveled = 0.0, step = Math.max(0.3, rayStep.get());
        for (int i = 0; i < 28; i++) {
            Vec3d next = last.add(vel.multiply(step));
            RaycastContext ctx = new RaycastContext(last, next,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            HitResult hr = mc.world.raycast(ctx);
            if (hr != null && hr.getType() != HitResult.Type.MISS) return true;
            traveled += next.subtract(last).length();
            if (traveled >= Math.max(0.6, checkBlocks)) break;
            last = next;
            vel = vel.multiply(drag, drag, drag).add(0, -gravity, 0);
        }
        return false;
    }

    /** Corridor/slit guard — HORIZONTAL rays only (no ceiling false positives). */
    private boolean hasCorridorWidth(float yawDeg, float pitchDeg, double dist, double halfWidth) {
        if (mc.player == null) return false;

        // Horizontal forward vector (ignore pitch)
        float yawRad = (float) Math.toRadians(yawDeg);
        Vec3d fwd = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vec3d left = new Vec3d(-fwd.z, 0, fwd.x);

        Vec3d eye = mc.player.getEyePos();
        Vec3d startL = eye.add(left.multiply(halfWidth));
        Vec3d startR = eye.add(left.multiply(-halfWidth));
        Vec3d endL   = startL.add(fwd.multiply(dist));
        Vec3d endR   = startR.add(fwd.multiply(dist));

        RaycastContext ctxL = new RaycastContext(startL, endL,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        RaycastContext ctxR = new RaycastContext(startR, endR,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);

        HitResult hitL = mc.world.raycast(ctxL);
        HitResult hitR = mc.world.raycast(ctxR);

        boolean clearL = (hitL == null || hitL.getType() == HitResult.Type.MISS);
        boolean clearR = (hitR == null || hitR.getType() == HitResult.Type.MISS);

        return clearL && clearR;
    }

    private static Vec3d directionFromYawPitch(float yawDeg, float pitchDeg) {
        float yaw = (float) Math.toRadians(yawDeg);
        float pitch = (float) Math.toRadians(pitchDeg);
        double x = -Math.sin(yaw) * Math.cos(pitch);
        double y = -Math.sin(pitch);
        double z =  Math.cos(yaw) * Math.cos(pitch);
        return new Vec3d(x, y, z).normalize();
    }

    // ===== Auto-pitch core =====
    private Float findFlattestPitchForDistanceRangeWithGuards(
        float yawDeg, double minD, double maxD,
        double clearDist, double nearCheckBlocks, double initialClear
    ) {
        for (float pitch = -2f; pitch >= -89f; pitch -= 0.5f) {
            if (!hasInitialClearance(yawDeg, pitch, initialClear)) continue;
            double d = simulatePearlRange(yawDeg, pitch, 80);
            if (d >= minD && d <= maxD) {
                if (hasClearPath(yawDeg, pitch, clearDist) && !hitsWallEarly(yawDeg, pitch, nearCheckBlocks)) {
                    return clampPitch(pitch);
                }
            }
        }
        if (escalatePitch.get()) {
            for (float pitch = -2f; pitch >= -89f; pitch -= 0.25f) {
                if (!hasInitialClearance(yawDeg, pitch, initialClear)) continue;
                double d = simulatePearlRange(yawDeg, pitch, 80);
                if (d >= minD && d <= maxD) {
                    if (hasClearPath(yawDeg, pitch, clearDist) && !hitsWallEarly(yawDeg, pitch, nearCheckBlocks)) {
                        return clampPitch(pitch);
                    }
                }
            }
        }
        return null;
    }

    /** Coarse scan: best horizontal distance reachable inside [minD,maxD]. */
    private double bestReachableDistance(double minD, double maxD) {
        if (mc.player == null) return 0.0;

        float enemyYaw = getNearestEnemyYaw();
        boolean haveEnemy = !Float.isNaN(enemyYaw);

        java.util.List<Float> yaws = new java.util.ArrayList<>();
        double step = MathHelper.clamp(probeStepDegrees.get(), 10.0, 45.0);
        for (double deg = -180.0; deg < 180.0; deg += step) {
            float yaw = (float) deg;
            if (aimFullCircle.get() && avoidEnemyCone.get() && haveEnemy) {
                if (angleDelta(yaw, enemyYaw) <= (float)(enemyConeDegrees.get() / 2.0)) continue;
            }
            yaws.add(yaw);
        }
        if (yaws.isEmpty() && haveEnemy) yaws.add(wrapYaw(enemyYaw + 180f));

        int pc = Math.max(1, Math.min(6, probePitchCount.get()));
        float[] pitches = new float[pc];
        for (int i = 0; i < pc; i++) pitches[i] = clampPitch(-2f - i * 6f);

        double best = 0.0;
        for (float yaw : yaws) {
            for (float pitch : pitches) {
                if (!hasInitialClearance(yaw, pitch, initialClearance.get())) continue;
                if (!hasClearPath(yaw, pitch, clearCheckDistance.get())) continue;
                if (hitsWallEarly(yaw, pitch, nearPathCheck.get())) continue;

                // NOTE: corridor width check intentionally omitted in coarse probe.
                double d = simulatePearlRange(yaw, pitch, 80);
                if (d >= minD && d <= maxD) best = Math.max(best, d);
            }
        }
        return best;
    }

    private double estimateFlightTicks(float yawDeg, float pitchDeg, int maxTicks) {
        if (mc.player == null) return Double.POSITIVE_INFINITY;
        Vec3d pos = mc.player.getEyePos();
        Vec3d vel = directionFromYawPitch(yawDeg, pitchDeg).multiply(1.5f);
        final double gravity = 0.03, drag = 0.99;
        double groundY = mc.player.getY();
        for (int i = 0; i < maxTicks; i++) {
            pos = pos.add(vel);
            vel = vel.multiply(drag, drag, drag).add(0, -gravity, 0);
            if (pos.y <= groundY + 0.1 && i > 2) return i;
        }
        return maxTicks;
    }

    private double simulatePearlRange(float yawDeg, float pitchDeg, int maxTicks) {
        if (mc.player == null) return 0.0;
        Vec3d pos = mc.player.getEyePos();
        Vec3d vel = directionFromYawPitch(yawDeg, pitchDeg).multiply(1.5f);
        final double gravity = 0.03, drag = 0.99;
        double startY = mc.player.getY(), groundY = startY, horizontalDist = 0.0;
        for (int i = 0; i < maxTicks; i++) {
            pos = pos.add(vel);
            vel = vel.multiply(drag, drag, drag).add(0, -gravity, 0);
            Vec3d flat = new Vec3d(pos.x - mc.player.getX(), 0, pos.z - mc.player.getZ());
            horizontalDist = flat.length();
            if (pos.y <= groundY + 0.1 && i > 2) break;
            if (pos.y > startY + 80 || pos.y < startY - 80) break;
        }
        return horizontalDist;
    }

    // ===== Misc helpers =====
    private int pickHotbarSlot() {
        if (preferEmptyHotbar.get()) {
            for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return MathHelper.clamp(tempHotbarSlot.get(), 0, 8);
    }

    private int countTotemsAll() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING))
                count += mc.player.getInventory().getStack(i).getCount();
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING))
            count += mc.player.getOffHandStack().getCount();
        if (mc.player.currentScreenHandler != null) {
            var cur = mc.player.currentScreenHandler.getCursorStack();
            if (cur != null && cur.isOf(Items.TOTEM_OF_UNDYING)) count += cur.getCount();
        }
        return count;
    }

    private static float clampPitch(float p) { return Math.max(-89.9f, Math.min(89.9f, p)); }
    private static float wrapYaw(float yaw) { while (yaw <= -180f) yaw += 360f; while (yaw > 180f) yaw -= 360f; return yaw; }
}
