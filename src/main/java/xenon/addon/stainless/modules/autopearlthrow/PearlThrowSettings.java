package xenon.addon.stainless.modules.autopearlthrow;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;

public class PearlThrowSettings {

    // ---- Constants
    private static final int MIN_RESERVE_TOTEMS = 1;
    private static final int MAX_RESERVE_TOTEMS = 10;
    private static final int DEFAULT_RESERVE_TOTEMS = 2;

    // ---- Dependencies
    private final Settings moduleSettings;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // ---- Setting Groups
    private final SettingGroup sgGeneral;
    private final SettingGroup sgAiming;
    private final SettingGroup sgSafety;
    private final SettingGroup sgInv;
    private final SettingGroup sgDebug;

    // ---- General Settings
    private final Setting<Boolean> rotate;
    private final Setting<Double> pitchUpDegrees;
    private final Setting<Integer> throwDelayMs;
    private final Setting<Integer> cooldownMs;
    private final Setting<Boolean> silentSwap;
    private final Setting<Boolean> jumpOnThrow;
    private final Setting<Integer> jumpWaitMs;
    private final Setting<Boolean> reserveTotems;
    private final Setting<Integer> reserveTotemCount;

    // ---- Aiming Settings
    private final Setting<Boolean> autoBackThrowPitch;
    private final Setting<Boolean> aimFullCircle;
    private final Setting<Boolean> avoidEnemyCone;
    private final Setting<Double> enemyConeDegrees;
    private final Setting<Double> minBackDist;
    private final Setting<Double> maxBackDist;

    // ---- Safety Settings
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

    // ---- Inventory Settings
    private final Setting<Boolean> pullFromInventory;
    private final Setting<Boolean> preferEmptyHotbar;
    private final Setting<Integer> tempHotbarSlot;

    // ---- Keybind Settings
    private final Setting<Keybind> forceThrowBind;

    // ---- Debug Settings
    private final Setting<Boolean> debug;
    private final Setting<Boolean> testNow;

    // ---- Constructor
    public PearlThrowSettings(Settings moduleSettings) {
        this.moduleSettings = moduleSettings;

        // Initialize groups
        sgGeneral = moduleSettings.getDefaultGroup();
        sgAiming = moduleSettings.createGroup("Aiming");
        sgSafety = moduleSettings.createGroup("Safety");
        sgInv = moduleSettings.createGroup("Inventory");
        sgDebug = moduleSettings.createGroup("Debug");

        // Build settings
        rotate = buildRotateSetting();
        pitchUpDegrees = buildPitchUpSetting();
        throwDelayMs = buildThrowDelaySetting();
        cooldownMs = buildCooldownSetting();
        silentSwap = buildSilentSwapSetting();
        jumpOnThrow = buildJumpOnThrowSetting();
        jumpWaitMs = buildJumpWaitSetting();
        reserveTotems = buildReserveTotemsEnableSetting();
        reserveTotemCount = buildReserveTotemCountSetting();

        autoBackThrowPitch = buildAutoPitchSetting();
        aimFullCircle = buildAimFullCircleSetting();
        avoidEnemyCone = buildAvoidEnemyConeSetting();
        enemyConeDegrees = buildEnemyConeDegreesSetting();
        minBackDist = buildMinBackDistSetting();
        maxBackDist = buildMaxBackDistSetting();

        clearCheckDistance = buildClearCheckDistanceSetting();
        trySideOffsets = buildTrySideOffsetsSetting();
        escalatePitch = buildEscalatePitchSetting();
        fallbackStraightUp = buildFallbackStraightUpSetting();
        nearPathCheck = buildNearPathCheckSetting();
        initialClearance = buildInitialClearanceSetting();
        corridorCheckDist = buildCorridorCheckDistSetting();
        corridorHalfWidth = buildCorridorHalfWidthSetting();
        enemyCorridorBoost = buildEnemyCorridorBoostSetting();
        enemyConeSoftPad = buildEnemyConeSoftPadSetting();
        cancelIfNoEscape = buildCancelIfNoEscapeSetting();
        probeStepDegrees = buildProbeStepDegreesSetting();
        probePitchCount = buildProbePitchCountSetting();
        startBias = buildStartBiasSetting();
        rayStep = buildRayStepSetting();

        pullFromInventory = buildPullFromInventorySetting();
        preferEmptyHotbar = buildPreferEmptyHotbarSetting();
        tempHotbarSlot = buildTempHotbarSlotSetting();

        forceThrowBind = buildForceThrowBindSetting();

        debug = buildDebugSetting();
        testNow = buildTestNowSetting();
    }

    // ---- General Setting Builders
    private Setting<Boolean> buildRotateSetting() {
        return sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate your view to aim the pearl.")
            .defaultValue(true)
            .build());
    }

    private Setting<Double> buildPitchUpSetting() {
        return sgGeneral.add(new DoubleSetting.Builder()
            .name("pitch-up")
            .description("Base upward pitch (degrees) when Auto Pitch is OFF.")
            .defaultValue(35.0)
            .min(0.0)
            .max(89.0)
            .sliderMin(0.0)
            .sliderMax(80.0)
            .visible(() -> !autoBackThrowPitch.get())
            .build());
    }

    private Setting<Integer> buildThrowDelaySetting() {
        return sgGeneral.add(new IntSetting.Builder()
            .name("throw-delay")
            .description("Delay (ms) after totem pop before throwing pearl.")
            .defaultValue(120)
            .min(0)
            .max(2000)
            .sliderMax(500)
            .build());
    }

    private Setting<Integer> buildCooldownSetting() {
        return sgGeneral.add(new IntSetting.Builder()
            .name("cooldown")
            .description("Minimum time (ms) between consecutive throws.")
            .defaultValue(1000)
            .min(100)
            .max(5000)
            .sliderMax(2000)
            .build());
    }

    private Setting<Boolean> buildSilentSwapSetting() {
        return sgGeneral.add(new BoolSetting.Builder()
            .name("silent-swap")
            .description("Silently swap to pearl slot without changing held item visually.")
            .defaultValue(true)
            .build());
    }

    private Setting<Boolean> buildJumpOnThrowSetting() {
        return sgGeneral.add(new BoolSetting.Builder()
            .name("jump-on-throw")
            .description("Jump before throwing to clear 2-block walls and obstacles.")
            .defaultValue(true)
            .build());
    }

    private Setting<Integer> buildJumpWaitSetting() {
        return sgGeneral.add(new IntSetting.Builder()
            .name("jump-wait")
            .description("Wait time (ms) after jumping before calculating aim and throwing.")
            .defaultValue(180)
            .min(50)
            .max(400)
            .sliderMax(300)
            .visible(jumpOnThrow::get)
            .build());
    }

    private Setting<Boolean> buildReserveTotemsEnableSetting() {
        return sgGeneral.add(new BoolSetting.Builder()
            .name("reserve-totems")
            .description("Skip throwing when totem count is at or below reserve amount.")
            .defaultValue(true)
            .build());
    }

    private Setting<Integer> buildReserveTotemCountSetting() {
        return sgGeneral.add(new IntSetting.Builder()
            .name("reserve-count")
            .description("Minimum totems to keep (counts inventory + hotbar + offhand + cursor).")
            .defaultValue(DEFAULT_RESERVE_TOTEMS)
            .min(MIN_RESERVE_TOTEMS)
            .max(MAX_RESERVE_TOTEMS)
            .sliderMin(MIN_RESERVE_TOTEMS)
            .sliderMax(6)
            .visible(reserveTotems::get)
            .build());
    }

    // ---- Aiming Setting Builders
    private Setting<Boolean> buildAutoPitchSetting() {
        return sgAiming.add(new BoolSetting.Builder()
            .name("auto-pitch")
            .description("Automatically calculate the flattest pitch that lands within distance range.")
            .defaultValue(true)
            .build());
    }

    private Setting<Boolean> buildAimFullCircleSetting() {
        return sgAiming.add(new BoolSetting.Builder()
            .name("allow-360-aim")
            .description("Search all 360° for best throw direction (not just behind you).")
            .defaultValue(true)
            .build());
    }

    private Setting<Boolean> buildAvoidEnemyConeSetting() {
        return sgAiming.add(new BoolSetting.Builder()
            .name("avoid-enemy-cone")
            .description("Avoid throwing toward the nearest enemy player.")
            .defaultValue(false)
            .visible(aimFullCircle::get)
            .build());
    }

    private Setting<Double> buildEnemyConeDegreesSetting() {
        return sgAiming.add(new DoubleSetting.Builder()
            .name("enemy-cone-degrees")
            .description("Width of forbidden cone pointing toward nearest enemy.")
            .defaultValue(80.0)
            .min(20.0)
            .max(140.0)
            .sliderMin(40.0)
            .sliderMax(120.0)
            .visible(() -> aimFullCircle.get() && avoidEnemyCone.get())
            .build());
    }

    private Setting<Double> buildMinBackDistSetting() {
        return sgAiming.add(new DoubleSetting.Builder()
            .name("min-escape-distance")
            .description("Minimum landing distance (blocks) from throw position.")
            .defaultValue(6.0)
            .min(3.0)
            .max(60.0)
            .sliderMin(4.0)
            .sliderMax(40.0)
            .build());
    }

    private Setting<Double> buildMaxBackDistSetting() {
        return sgAiming.add(new DoubleSetting.Builder()
            .name("max-escape-distance")
            .description("Maximum landing distance (blocks) from throw position.")
            .defaultValue(22.0)
            .min(8.0)
            .max(70.0)
            .sliderMin(12.0)
            .sliderMax(50.0)
            .build());
    }

    // ---- Safety Setting Builders
    private Setting<Double> buildClearCheckDistanceSetting() {
        return sgSafety.add(new DoubleSetting.Builder()
            .name("clear-check-distance")
            .description("How far to raycast along throw direction for obstructions (blocks).")
            .defaultValue(3.0)
            .min(1.0)
            .max(10.0)
            .sliderMin(2.0)
            .sliderMax(6.0)
            .build());
    }

    private Setting<Boolean> buildTrySideOffsetsSetting() {
        return sgSafety.add(new BoolSetting.Builder()
            .name("try-side-offsets")
            .description("Try yaw offsets (±20°) when using legacy fallback mode.")
            .defaultValue(true)
            .build());
    }

    private Setting<Boolean> buildEscalatePitchSetting() {
        return sgSafety.add(new BoolSetting.Builder()
            .name("escalate-pitch")
            .description("Use finer pitch resolution if normal resolution fails.")
            .defaultValue(true)
            .build());
    }

    private Setting<Boolean> buildFallbackStraightUpSetting() {
        return sgSafety.add(new BoolSetting.Builder()
            .name("fallback-straight-up")
            .description("As last resort, throw straight up if all other angles blocked.")
            .defaultValue(true)
            .build());
    }

    private Setting<Double> buildNearPathCheckSetting() {
        return sgSafety.add(new DoubleSetting.Builder()
            .name("near-path-check")
            .description("Simulate early pearl trajectory to check for collisions (blocks).")
            .defaultValue(3.0)
            .min(1.0)
            .max(8.0)
            .sliderMin(2.0)
            .sliderMax(6.0)
            .build());
    }

    private Setting<Double> buildInitialClearanceSetting() {
        return sgSafety.add(new DoubleSetting.Builder()
            .name("initial-clearance")
            .description("Minimum clear space directly in front before throwing (blocks).")
            .defaultValue(0.8)
            .min(0.2)
            .max(1.5)
            .sliderMin(0.5)
            .sliderMax(1.2)
            .build());
    }

    private Setting<Double> buildCorridorCheckDistSetting() {
        return sgSafety.add(new DoubleSetting.Builder()
            .name("corridor-check-distance")
            .description("How far forward to verify corridor width (blocks).")
            .defaultValue(2.0)
            .min(1.0)
            .max(6.0)
            .sliderMin(1.0)
            .sliderMax(5.0)
            .build());
    }

    private Setting<Double> buildCorridorHalfWidthSetting() {
        return sgSafety.add(new DoubleSetting.Builder()
            .name("corridor-half-width")
            .description("Required clearance on each side of throw path (blocks). 0.5 ≈ one block.")
            .defaultValue(0.30)
            .min(0.25)
            .max(1.0)
            .sliderMin(0.25)
            .sliderMax(0.8)
            .build());
    }

    private Setting<Double> buildEnemyCorridorBoostSetting() {
        return sgSafety.add(new DoubleSetting.Builder()
            .name("enemy-corridor-boost")
            .description("Additional clearance required when throwing near enemy cone (blocks).")
            .defaultValue(0.20)
            .min(0.0)
            .max(0.6)
            .sliderMin(0.0)
            .sliderMax(0.4)
            .visible(() -> aimFullCircle.get() && avoidEnemyCone.get())
            .build());
    }

    private Setting<Double> buildEnemyConeSoftPadSetting() {
        return sgSafety.add(new DoubleSetting.Builder()
            .name("enemy-cone-soft-pad")
            .description("Extra degrees around enemy cone for corridor boost activation.")
            .defaultValue(12.0)
            .min(0.0)
            .max(30.0)
            .sliderMin(6.0)
            .sliderMax(24.0)
            .visible(() -> aimFullCircle.get() && avoidEnemyCone.get())
            .build());
    }

    private Setting<Boolean> buildCancelIfNoEscapeSetting() {
        return sgSafety.add(new BoolSetting.Builder()
            .name("cancel-if-no-escape")
            .description("Cancel throw if no angle can reach minimum escape distance.")
            .defaultValue(false)
            .build());
    }

    private Setting<Double> buildProbeStepDegreesSetting() {
        return sgSafety.add(new DoubleSetting.Builder()
            .name("probe-step-degrees")
            .description("Yaw increment when scanning for escape routes (degrees).")
            .defaultValue(20.0)
            .min(10.0)
            .max(45.0)
            .sliderMin(15.0)
            .sliderMax(30.0)
            .visible(cancelIfNoEscape::get)
            .build());
    }

    private Setting<Integer> buildProbePitchCountSetting() {
        return sgSafety.add(new IntSetting.Builder()
            .name("probe-pitch-count")
            .description("Number of pitch angles to test per yaw when probing escape.")
            .defaultValue(3)
            .min(1)
            .max(6)
            .visible(cancelIfNoEscape::get)
            .build());
    }

    private Setting<Double> buildStartBiasSetting() {
        return sgSafety.add(new DoubleSetting.Builder()
            .name("start-bias")
            .description("Offset raycast start forward from eyes (blocks). Helps with tight gaps.")
            .defaultValue(0.25)
            .min(0.0)
            .max(0.6)
            .sliderMin(0.0)
            .sliderMax(0.4)
            .build());
    }

    private Setting<Double> buildRayStepSetting() {
        return sgSafety.add(new DoubleSetting.Builder()
            .name("ray-step")
            .description("Step size for trajectory collision simulation (blocks). Larger = faster but less accurate.")
            .defaultValue(0.6)
            .min(0.3)
            .max(0.9)
            .sliderMin(0.4)
            .sliderMax(0.8)
            .build());
    }

    // ---- Inventory Setting Builders
    private Setting<Boolean> buildPullFromInventorySetting() {
        return sgInv.add(new BoolSetting.Builder()
            .name("pull-from-inventory")
            .description("Automatically move pearls from inventory to hotbar if needed.")
            .defaultValue(true)
            .build());
    }

    private Setting<Boolean> buildPreferEmptyHotbarSetting() {
        return sgInv.add(new BoolSetting.Builder()
            .name("prefer-empty-hotbar")
            .description("Prioritize empty hotbar slots when pulling pearls.")
            .defaultValue(true)
            .visible(pullFromInventory::get)
            .build());
    }

    private Setting<Integer> buildTempHotbarSlotSetting() {
        return sgInv.add(new IntSetting.Builder()
            .name("temp-hotbar-slot")
            .description("Fallback hotbar slot (0-8) if no empty slots available.")
            .defaultValue(8)
            .min(0)
            .max(8)
            .visible(() -> pullFromInventory.get() && !preferEmptyHotbar.get())
            .build());
    }

    // ---- Keybind Setting Builders
    private Setting<Keybind> buildForceThrowBindSetting() {
        return sgGeneral.add(new KeybindSetting.Builder()
            .name("force-throw-bind")
            .description("Keybind to force throw pearl immediately (bypasses cooldown and totem pop).")
            .defaultValue(Keybind.none())
            .build());
    }

    // ---- Debug Setting Builders
    private Setting<Boolean> buildDebugSetting() {
        return sgDebug.add(new BoolSetting.Builder()
            .name("debug")
            .description("Log detailed throw detection and execution steps.")
            .defaultValue(false)
            .build());
    }

    private Setting<Boolean> buildTestNowSetting() {
        return sgDebug.add(new BoolSetting.Builder()
            .name("test-now")
            .description("Toggle to manually trigger a test throw (auto-resets).")
            .defaultValue(false)
            .build());
    }

    // ---- Public Getters - General
    public boolean shouldRotate() {
        return rotate.get();
    }

    public double getPitchUpDegrees() {
        return pitchUpDegrees.get();
    }

    public int getThrowDelayMs() {
        return throwDelayMs.get();
    }

    public int getCooldownMs() {
        return cooldownMs.get();
    }

    public boolean isSilentSwap() {
        return silentSwap.get();
    }

    public boolean shouldJumpOnThrow() {
        return jumpOnThrow.get();
    }

    public int getJumpWaitMs() {
        return jumpWaitMs.get();
    }

    // ---- Public Getters - Reserve Totem Logic
    public boolean isReserveGuardActive() {
        if (!reserveTotems.get()) {
            return false;
        }
        return countAllTotems() <= reserveTotemCount.get();
    }

    private int countAllTotems() {
        if (mc.player == null) {
            return 0;
        }

        int count = 0;

        // Count inventory and hotbar
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                count += mc.player.getInventory().getStack(i).getCount();
            }
        }

        // Count offhand
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            count += mc.player.getOffHandStack().getCount();
        }

        // Count cursor (if in a screen)
        if (mc.player.currentScreenHandler != null) {
            var cursorStack = mc.player.currentScreenHandler.getCursorStack();
            if (cursorStack != null && cursorStack.isOf(Items.TOTEM_OF_UNDYING)) {
                count += cursorStack.getCount();
            }
        }

        return count;
    }

    // ---- Public Getters - Aiming
    public boolean useAutoPitch() {
        return autoBackThrowPitch.get();
    }

    public boolean aimFullCircle() {
        return aimFullCircle.get();
    }

    public boolean avoidEnemyCone() {
        return avoidEnemyCone.get();
    }

    public double getEnemyConeDegrees() {
        return enemyConeDegrees.get();
    }

    public double getMinBackDist() {
        return minBackDist.get();
    }

    public double getMaxBackDist() {
        return maxBackDist.get();
    }

    // ---- Public Getters - Safety
    public double getClearCheckDistance() {
        return clearCheckDistance.get();
    }

    public boolean shouldTrySideOffsets() {
        return trySideOffsets.get();
    }

    public boolean shouldEscalatePitch() {
        return escalatePitch.get();
    }

    public boolean useFallbackStraightUp() {
        return fallbackStraightUp.get();
    }

    public double getNearPathCheck() {
        return nearPathCheck.get();
    }

    public double getInitialClearance() {
        return initialClearance.get();
    }

    public double getCorridorCheckDist() {
        return corridorCheckDist.get();
    }

    public double getCorridorHalfWidth() {
        return corridorHalfWidth.get();
    }

    public double getEnemyCorridorBoost() {
        return enemyCorridorBoost.get();
    }

    public double getEnemyConeSoftPad() {
        return enemyConeSoftPad.get();
    }

    public boolean shouldCancelIfNoEscape() {
        return cancelIfNoEscape.get();
    }

    public double getProbeStepDegrees() {
        return probeStepDegrees.get();
    }

    public int getProbePitchCount() {
        return probePitchCount.get();
    }

    public double getStartBias() {
        return startBias.get();
    }

    public double getRayStep() {
        return rayStep.get();
    }

    // ---- Public Getters - Inventory
    public boolean shouldPullFromInventory() {
        return pullFromInventory.get();
    }

    public boolean shouldPreferEmptyHotbar() {
        return preferEmptyHotbar.get();
    }

    public int getTempHotbarSlot() {
        return tempHotbarSlot.get();
    }

    // ---- Public Getters - Keybind
    public Keybind getForceThrowBind() {
        return forceThrowBind.get();
    }

    // ---- Public Getters - Debug
    public boolean isDebugEnabled() {
        return debug.get();
    }

    public boolean isTestNowTriggered() {
        return testNow.get();
    }

    public void resetTestNow() {
        testNow.set(false);
    }
}
