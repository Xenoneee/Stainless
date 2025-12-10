package xenon.addon.stainless.modules.autopearlthrow;

import meteordevelopment.meteorclient.settings.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;

public class PearlThrowSettings {
    private final Settings moduleSettings;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // Setting groups
    private final SettingGroup sgGeneral;
    private final SettingGroup sgAiming;
    private final SettingGroup sgSafety;
    private final SettingGroup sgInv;
    private final SettingGroup sgDebug;

    // General settings
    private final Setting<Boolean> rotate;
    private final Setting<Double> pitchUpDegrees;
    private final Setting<Integer> throwDelayMs;
    private final Setting<Integer> cooldownMs;
    private final Setting<Boolean> silentSwap;
    private final Setting<Boolean> preferOffhand;
    private final Setting<Boolean> jumpOnThrow;
    private final Setting<Integer> jumpWaitMs;
    private final Setting<Boolean> reserveTotems;
    private final Setting<Integer> reserveTotemCount;

    // Aiming settings
    private final Setting<Boolean> autoBackThrowPitch;
    private final Setting<Boolean> aimFullCircle;
    private final Setting<Boolean> avoidEnemyCone;
    private final Setting<Double> enemyConeDegrees;
    private final Setting<Double> minBackDist;
    private final Setting<Double> maxBackDist;

    // Safety settings
    private final Setting<Double> clearCheckDistance;
    private final Setting<Boolean> trySideOffsets;
    private final Setting<Boolean> escalatePitch;
    private final Setting<Boolean> fallbackStraightUp;
    private final Setting<Double> nearPathCheck;
    private final Setting<Double> initialClearance;
    private final Setting<Double> corridorCheckDist;
    private final Setting<Double> corridorHalfWidth;
    private final Setting<Double> enemyCorridorBoost;
    private final Setting<Double> enemyConeSoftPad;
    private final Setting<Boolean> cancelIfNoEscape;
    private final Setting<Double> probeStepDegrees;
    private final Setting<Integer> probePitchCount;
    private final Setting<Double> startBias;
    private final Setting<Double> rayStep;

    // Inventory settings
    private final Setting<Boolean> pullFromInventory;
    private final Setting<Boolean> preferEmptyHotbar;
    private final Setting<Integer> tempHotbarSlot;

    // Debug settings
    private final Setting<Boolean> debug;
    private final Setting<Boolean> testNow;

    public PearlThrowSettings(Settings moduleSettings) {
        this.moduleSettings = moduleSettings;

        // Initialize groups
        sgGeneral = moduleSettings.getDefaultGroup();
        sgAiming = moduleSettings.createGroup("Aiming");
        sgSafety = moduleSettings.createGroup("Safety");
        sgInv = moduleSettings.createGroup("Inventory");
        sgDebug = moduleSettings.createGroup("Debug");

        // General settings
        rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate").description("Rotate your view to aim the pearl.").defaultValue(true).build());

        pitchUpDegrees = sgGeneral.add(new DoubleSetting.Builder()
            .name("pitch-up").description("Base upward pitch (deg) when Auto Pitch is OFF.")
            .defaultValue(35.0).min(0.0).max(89.0).sliderMin(0.0).sliderMax(80.0).build());

        throwDelayMs = sgGeneral.add(new IntSetting.Builder()
            .name("throw-delay-ms").description("Delay after pop before throwing.")
            .defaultValue(120).min(0).max(2000).build());

        cooldownMs = sgGeneral.add(new IntSetting.Builder()
            .name("cooldown-ms").description("Minimum time between throws.")
            .defaultValue(1000).min(100).max(5000).build());

        silentSwap = sgGeneral.add(new BoolSetting.Builder()
            .name("silent-swap").description("Temporarily swap to pearls, then back.")
            .defaultValue(true).build());

        preferOffhand = sgGeneral.add(new BoolSetting.Builder()
            .name("prefer-offhand").description("Use offhand pearls if available.")
            .defaultValue(true).build());

        jumpOnThrow = sgGeneral.add(new BoolSetting.Builder()
            .name("jump-on-throw").description("Jump right before throwing to see over 2-block walls.")
            .defaultValue(true).build());

        jumpWaitMs = sgGeneral.add(new IntSetting.Builder()
            .name("jump-wait-ms").description("Wait this long after jumping before aiming/throwing.")
            .defaultValue(180).min(50).max(400).visible(jumpOnThrow::get).build());

        reserveTotems = sgGeneral.add(new BoolSetting.Builder()
            .name("reserve-totems")
            .description("Skip scheduling/throwing when total totems are at or below the set amount.")
            .defaultValue(true).build());

        reserveTotemCount = sgGeneral.add(new IntSetting.Builder()
            .name("reserve-count")
            .description("Keep at least this many totems (inv + hotbar + offhand + cursor).")
            .defaultValue(2).min(1).max(10).sliderMin(1).sliderMax(6)
            .visible(reserveTotems::get).build());

        // Aiming settings
        autoBackThrowPitch = sgAiming.add(new BoolSetting.Builder()
            .name("auto-pitch").description("Auto-pick the flattest pitch that lands within the distance window.")
            .defaultValue(true).build());

        aimFullCircle = sgAiming.add(new BoolSetting.Builder()
            .name("allow-360-aim").description("Search 360° for the best yaw (not just behind you).")
            .defaultValue(true).build());

        avoidEnemyCone = sgAiming.add(new BoolSetting.Builder()
            .name("avoid-enemy-cone").description("Skip a cone toward the nearest enemy.")
            .defaultValue(false).visible(aimFullCircle::get).build());

        enemyConeDegrees = sgAiming.add(new DoubleSetting.Builder()
            .name("enemy-cone-deg").description("Width of the forbidden cone toward the nearest enemy.")
            .defaultValue(80.0).min(20.0).max(140.0).sliderMin(40.0).sliderMax(120.0)
            .visible(() -> aimFullCircle.get() && avoidEnemyCone.get()).build());

        minBackDist = sgAiming.add(new DoubleSetting.Builder()
            .name("min-escape-distance").description("Minimum landing distance (blocks) from your position.")
            .defaultValue(6.0).min(3.0).max(60.0).sliderMin(4.0).sliderMax(40.0).build());

        maxBackDist = sgAiming.add(new DoubleSetting.Builder()
            .name("max-escape-distance").description("Maximum landing distance (blocks) from your position.")
            .defaultValue(22.0).min(8.0).max(70.0).sliderMin(12.0).sliderMax(50.0).build());

        // Safety settings
        clearCheckDistance = sgSafety.add(new DoubleSetting.Builder()
            .name("clear-check-distance").description("Distance along aim direction to check for blocking walls.")
            .defaultValue(3.0).min(1.0).max(10.0).sliderMin(2.0).sliderMax(6.0).build());

        trySideOffsets = sgSafety.add(new BoolSetting.Builder()
            .name("try-side-offsets").description("If legacy fallback is used, try yaw offsets (±20°).")
            .defaultValue(true).build());

        escalatePitch = sgSafety.add(new BoolSetting.Builder()
            .name("escalate-pitch").description("If blocked, increase pitch (more up) to clear obstructions.")
            .defaultValue(true).build());

        fallbackStraightUp = sgSafety.add(new BoolSetting.Builder()
            .name("fallback-straight-up").description("If still blocked, throw straight up as a last resort.")
            .defaultValue(true).build());

        nearPathCheck = sgSafety.add(new DoubleSetting.Builder()
            .name("near-path-check").description("Checks the first N blocks of the pearl's path for collisions.")
            .defaultValue(3.0).min(1.0).max(8.0).sliderMin(2.0).sliderMax(6.0).build());

        initialClearance = sgSafety.add(new DoubleSetting.Builder()
            .name("initial-clearance")
            .description("Minimum clear distance from your eyes along the aim ray. Helps when touching a wall.")
            .defaultValue(0.8).min(0.2).max(1.5).sliderMin(0.5).sliderMax(1.2).build());

        corridorCheckDist = sgSafety.add(new DoubleSetting.Builder()
            .name("corridor-check-distance")
            .description("How far ahead to verify the corridor's width (blocks).")
            .defaultValue(2.0).min(1.0).max(6.0).sliderMin(1.0).sliderMax(5.0).build());

        corridorHalfWidth = sgSafety.add(new DoubleSetting.Builder()
            .name("corridor-half-width")
            .description("Half width that must be clear on both sides of the throw ray (blocks). 0.5 ≈ one full block.")
            .defaultValue(0.30).min(0.25).max(1.0).sliderMin(0.25).sliderMax(0.8).build());

        enemyCorridorBoost = sgSafety.add(new DoubleSetting.Builder()
            .name("enemy-corridor-boost")
            .description("Extra half-width required when aiming near the enemy cone.")
            .defaultValue(0.20).min(0.0).max(0.6).sliderMin(0.0).sliderMax(0.4)
            .visible(() -> aimFullCircle.get() && avoidEnemyCone.get()).build());

        enemyConeSoftPad = sgSafety.add(new DoubleSetting.Builder()
            .name("enemy-cone-soft-pad")
            .description("Additional degrees around the enemy cone treated as 'near enemy' for corridor boost.")
            .defaultValue(12.0).min(0.0).max(30.0).sliderMin(6.0).sliderMax(24.0)
            .visible(() -> aimFullCircle.get() && avoidEnemyCone.get()).build());

        cancelIfNoEscape = sgSafety.add(new BoolSetting.Builder()
            .name("cancel-if-no-escape")
            .description("Skip throwing entirely if no direction can reach the minimum escape distance.")
            .defaultValue(false).build());

        probeStepDegrees = sgSafety.add(new DoubleSetting.Builder()
            .name("probe-step-deg")
            .description("Yaw step when probing for escape (coarse scan).")
            .defaultValue(20.0).min(10.0).max(45.0).sliderMin(15.0).sliderMax(30.0)
            .visible(cancelIfNoEscape::get).build());

        probePitchCount = sgSafety.add(new IntSetting.Builder()
            .name("probe-pitch-count")
            .description("How many flat-ish pitches to probe per yaw.")
            .defaultValue(3).min(1).max(6)
            .visible(cancelIfNoEscape::get).build());

        startBias = sgSafety.add(new DoubleSetting.Builder()
            .name("start-bias")
            .description("How far forward (blocks) to bias the ray start from your eyes. Helps throw through tight side gaps.")
            .defaultValue(0.25).min(0.0).max(0.6).sliderMin(0.0).sliderMax(0.4).build());

        rayStep = sgSafety.add(new DoubleSetting.Builder()
            .name("ray-step")
            .description("Step (blocks) for the early-collision simulator. Larger = fewer checks.")
            .defaultValue(0.6).min(0.3).max(0.9).sliderMin(0.4).sliderMax(0.8).build());

        // Inventory settings
        pullFromInventory = sgInv.add(new BoolSetting.Builder()
            .name("pull-from-inventory").description("If no pearls in hotbar, pull from main inventory.")
            .defaultValue(true).build());

        preferEmptyHotbar = sgInv.add(new BoolSetting.Builder()
            .name("prefer-empty-hotbar").description("Prefer an empty hotbar slot when pulling.")
            .defaultValue(true).visible(pullFromInventory::get).build());

        tempHotbarSlot = sgInv.add(new IntSetting.Builder()
            .name("temp-hotbar-slot").description("Fallback hotbar slot [0–8] when pulling.")
            .defaultValue(8).min(0).max(8).visible(pullFromInventory::get).build());

        // Debug settings
        debug = sgDebug.add(new BoolSetting.Builder()
            .name("debug").description("Log detection and throw steps.").defaultValue(false).build());

        testNow = sgDebug.add(new BoolSetting.Builder()
            .name("test-now")
            .description("Toggle to schedule a throw once (auto-resets).")
            .defaultValue(false).build());
    }

    // Getters for general settings
    public boolean shouldRotate() { return rotate.get(); }
    public double getPitchUpDegrees() { return pitchUpDegrees.get(); }
    public int getThrowDelayMs() { return throwDelayMs.get(); }
    public int getCooldownMs() { return cooldownMs.get(); }
    public boolean isSilentSwap() { return silentSwap.get(); }
    public boolean shouldPreferOffhand() { return preferOffhand.get(); }
    public boolean shouldJumpOnThrow() { return jumpOnThrow.get(); }
    public int getJumpWaitMs() { return jumpWaitMs.get(); }

    // Reserve totem logic
    public boolean isReserveGuardActive() {
        if (!reserveTotems.get()) return false;
        return countTotemsAll() <= reserveTotemCount.get();
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

    // Getters for aiming settings
    public boolean useAutoPitch() { return autoBackThrowPitch.get(); }
    public boolean aimFullCircle() { return aimFullCircle.get(); }
    public boolean avoidEnemyCone() { return avoidEnemyCone.get(); }
    public double getEnemyConeDegrees() { return enemyConeDegrees.get(); }
    public double getMinBackDist() { return minBackDist.get(); }
    public double getMaxBackDist() { return maxBackDist.get(); }

    // Getters for safety settings
    public double getClearCheckDistance() { return clearCheckDistance.get(); }
    public boolean shouldTrySideOffsets() { return trySideOffsets.get(); }
    public boolean shouldEscalatePitch() { return escalatePitch.get(); }
    public boolean useFallbackStraightUp() { return fallbackStraightUp.get(); }
    public double getNearPathCheck() { return nearPathCheck.get(); }
    public double getInitialClearance() { return initialClearance.get(); }
    public double getCorridorCheckDist() { return corridorCheckDist.get(); }
    public double getCorridorHalfWidth() { return corridorHalfWidth.get(); }
    public double getEnemyCorridorBoost() { return enemyCorridorBoost.get(); }
    public double getEnemyConeSoftPad() { return enemyConeSoftPad.get(); }
    public boolean shouldCancelIfNoEscape() { return cancelIfNoEscape.get(); }
    public double getProbeStepDegrees() { return probeStepDegrees.get(); }
    public int getProbePitchCount() { return probePitchCount.get(); }
    public double getStartBias() { return startBias.get(); }
    public double getRayStep() { return rayStep.get(); }

    // Getters for inventory settings
    public boolean shouldPullFromInventory() { return pullFromInventory.get(); }
    public boolean shouldPreferEmptyHotbar() { return preferEmptyHotbar.get(); }
    public int getTempHotbarSlot() { return tempHotbarSlot.get(); }

    // Getters for debug settings
    public boolean isDebugEnabled() { return debug.get(); }
    public boolean isTestNowTriggered() { return testNow.get(); }
    public void resetTestNow() { testNow.set(false); }
}
