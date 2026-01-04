package xenon.addon.stainless.modules.autopearlthrow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class PearlAimCalculator {

    // ---- Constants
    private static final float MIN_PITCH = -89.9f;
    private static final float MAX_PITCH = 89.9f;
    private static final float YAW_NORMALIZATION = 360f;
    private static final float YAW_LOWER_BOUND = -180f;
    private static final float YAW_UPPER_BOUND = 180f;
    private static final int MAX_FLIGHT_TICKS = 80;
    private static final float[] YAW_WIGGLES = {0f, 7f, -7f, 12f, -12f};

    private static final int FULL_CIRCLE_DEG_STEP = 15;
    private static final int LENIENT_YAW_STEP = 20;
    private static final float LENIENT_PITCH_START = -4f;
    private static final float LENIENT_PITCH_END = -80f;
    private static final float LENIENT_PITCH_STEP = 2f;

    private static final float NORMAL_PITCH_START = -2f;
    private static final float NORMAL_PITCH_END = -89f;
    private static final float NORMAL_PITCH_STEP = 0.5f;
    private static final float FINE_PITCH_STEP = 0.25f;

    private static final long ENEMY_YAW_CACHE_MS = 50;

    // ---- Settings
    private final PearlThrowSettings settings;

    // ---- State
    private final PearlSafetyChecker safetyChecker;
    private final PearlPhysicsSimulator simulator;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // ---- Cache
    private Float cachedEnemyYaw = null;
    private long lastEnemyYawCheck = 0;

    // ---- Constructor
    public PearlAimCalculator(PearlThrowSettings settings, PearlSafetyChecker safetyChecker) {
        this.settings = settings;
        this.safetyChecker = safetyChecker;
        this.simulator = new PearlPhysicsSimulator();
    }

    // ---- Public API
    public Aim calculateBestAim() {
        Aim aim = pickAimSmart();
        if (aim == null) {
            aim = pickAimLenient();
        }
        return aim;
    }

    // ---- Smart Aim Selection
    private Aim pickAimSmart() {
        DistanceRange range = getDistanceRange();

        if (!canEscapeIfRequired(range)) {
            return null;
        }

        Aim bestAim = findBestAimFromCandidates(range);
        return bestAim != null ? bestAim : tryLegacyFallback(range);
    }

    private boolean canEscapeIfRequired(DistanceRange range) {
        if (!settings.shouldCancelIfNoEscape()) {
            return true;
        }
        return bestReachableDistance(range) >= range.min;
    }

    private Aim findBestAimFromCandidates(DistanceRange range) {
        float[] yawCandidates = buildYawCandidates();

        AimCandidate best = new AimCandidate();

        for (float yawBase : yawCandidates) {
            for (float wiggle : YAW_WIGGLES) {
                float yaw = wrapYaw(yawBase + wiggle);
                Float pitch = calculatePitchForYaw(yaw, range);

                if (pitch == null) continue;
                if (!passesAllSafetyChecks(yaw, pitch)) continue;

                double time = simulator.estimateFlightTicks(yaw, pitch, MAX_FLIGHT_TICKS);
                if (best.isBetterThan(pitch, time)) {
                    best.update(yaw, pitch, time);
                }
            }
        }

        return best.toAim();
    }

    // ---- Lenient Aim Selection
    private Aim pickAimLenient() {
        DistanceRange range = getDistanceRange();
        LenientSafetyParams safetyParams = createLenientSafetyParams();

        List<Float> yaws = buildLenientYawCandidates();

        for (float yaw : yaws) {
            for (float pitch = LENIENT_PITCH_START; pitch >= LENIENT_PITCH_END; pitch -= LENIENT_PITCH_STEP) {
                if (isValidLenientAim(yaw, pitch, range, safetyParams)) {
                    return new Aim(yaw, clampPitch(pitch));
                }
            }
        }

        return null;
    }

    private List<Float> buildLenientYawCandidates() {
        List<Float> yaws = new ArrayList<>();
        for (int deg = 0; deg < 360; deg += LENIENT_YAW_STEP) {
            yaws.add((float) deg - YAW_UPPER_BOUND);
        }
        return yaws;
    }

    private boolean isValidLenientAim(float yaw, float pitch, DistanceRange range, LenientSafetyParams params) {
        if (!safetyChecker.hasInitialClearance(yaw, pitch, params.initialClearance)) {
            return false;
        }

        double distance = simulator.simulatePearlRange(yaw, pitch, MAX_FLIGHT_TICKS);
        if (!range.contains(distance)) {
            return false;
        }

        if (!safetyChecker.hasClearPath(yaw, pitch, params.clearCheckDistance)) {
            return false;
        }

        if (safetyChecker.hitsWallEarly(yaw, pitch, params.nearPathCheck)) {
            return false;
        }

        return true;
    }

    private LenientSafetyParams createLenientSafetyParams() {
        return new LenientSafetyParams(
            Math.max(0.6, settings.getInitialClearance() - 0.3),
            Math.max(3.0, settings.getClearCheckDistance() - 1.0),
            Math.max(3.0, settings.getNearPathCheck() - 1.5)
        );
    }

    // ---- Yaw Candidate Building
    private float[] buildYawCandidates() {
        if (!settings.aimFullCircle()) {
            return buildBehindPlayerYaws();
        }

        float enemyYaw = getNearestEnemyYaw();
        boolean haveEnemy = !Float.isNaN(enemyYaw);
        return buildFullCircleYaws(enemyYaw, haveEnemy);
    }

    private float[] buildBehindPlayerYaws() {
        if (mc.player == null) {
            return new float[]{0f};
        }

        float baseYaw = wrapYaw(mc.player.getYaw() + YAW_UPPER_BOUND);

        if (settings.shouldTrySideOffsets()) {
            return new float[]{
                baseYaw,
                wrapYaw(baseYaw + 20f),
                wrapYaw(baseYaw - 20f)
            };
        }

        return new float[]{baseYaw};
    }

    private float[] buildFullCircleYaws(float enemyYaw, boolean haveEnemy) {
        List<Float> yaws = new ArrayList<>();

        for (int deg = 0; deg < 360; deg += FULL_CIRCLE_DEG_STEP) {
            float yaw = (float) deg - YAW_UPPER_BOUND;
            if (shouldIncludeYaw(yaw, enemyYaw, haveEnemy)) {
                yaws.add(yaw);
            }
        }

        if (yaws.isEmpty() && haveEnemy) {
            yaws.add(wrapYaw(enemyYaw + YAW_UPPER_BOUND));
        }

        return toFloatArray(yaws);
    }

    private boolean shouldIncludeYaw(float yaw, float enemyYaw, boolean haveEnemy) {
        if (!haveEnemy || !settings.avoidEnemyCone()) {
            return true;
        }

        float halfCone = (float) (settings.getEnemyConeDegrees() / 2.0);
        return angleDelta(yaw, enemyYaw) > halfCone;
    }

    // ---- Pitch Calculation
    private Float calculatePitchForYaw(float yaw, DistanceRange range) {
        if (mc.player == null) {
            return null;
        }

        if (settings.useAutoPitch()) {
            return findFlattestPitchForDistanceRange(yaw, range);
        } else {
            return clampPitch((float) -settings.getPitchUpDegrees());
        }
    }

    private Float findFlattestPitchForDistanceRange(float yaw, DistanceRange range) {
        // Try normal resolution first
        Float result = searchPitchRange(yaw, range, NORMAL_PITCH_STEP);
        if (result != null) {
            return result;
        }

        // Try finer resolution if escalate is enabled
        if (settings.shouldEscalatePitch()) {
            return searchPitchRange(yaw, range, FINE_PITCH_STEP);
        }

        return null;
    }

    private Float searchPitchRange(float yaw, DistanceRange range, float step) {
        for (float pitch = NORMAL_PITCH_START; pitch >= NORMAL_PITCH_END; pitch -= step) {
            if (isValidPitchForDistance(yaw, pitch, range)) {
                return clampPitch(pitch);
            }
        }
        return null;
    }

    private boolean isValidPitchForDistance(float yaw, float pitch, DistanceRange range) {
        if (!safetyChecker.hasInitialClearance(yaw, pitch, settings.getInitialClearance())) {
            return false;
        }

        double distance = simulator.simulatePearlRange(yaw, pitch, MAX_FLIGHT_TICKS);
        if (!range.contains(distance)) {
            return false;
        }

        if (!safetyChecker.hasClearPath(yaw, pitch, settings.getClearCheckDistance())) {
            return false;
        }

        if (safetyChecker.hitsWallEarly(yaw, pitch, settings.getNearPathCheck())) {
            return false;
        }

        return true;
    }

    // ---- Safety Checks
    private boolean passesAllSafetyChecks(float yaw, float pitch) {
        if (!safetyChecker.hasInitialClearance(yaw, pitch, settings.getInitialClearance())) {
            return false;
        }

        if (!safetyChecker.hasClearPath(yaw, pitch, settings.getClearCheckDistance())) {
            return false;
        }

        if (safetyChecker.hitsWallEarly(yaw, pitch, settings.getNearPathCheck())) {
            return false;
        }

        double requiredHalfWidth = calculateRequiredCorridorWidth(yaw);
        if (!safetyChecker.hasCorridorWidth(yaw, pitch, settings.getCorridorCheckDist(), requiredHalfWidth)) {
            return false;
        }

        return true;
    }

    private double calculateRequiredCorridorWidth(float yaw) {
        double halfWidth = settings.getCorridorHalfWidth();

        if (settings.aimFullCircle() && settings.avoidEnemyCone()) {
            halfWidth = applyEnemyCorridorBoost(yaw, halfWidth);
        }

        return halfWidth;
    }

    private double applyEnemyCorridorBoost(float yaw, double baseHalfWidth) {
        float enemyYaw = getNearestEnemyYaw();
        if (Float.isNaN(enemyYaw)) {
            return baseHalfWidth;
        }

        float delta = angleDelta(yaw, enemyYaw);
        float hardCone = (float) (settings.getEnemyConeDegrees() / 2.0);
        float softCone = hardCone + (float) settings.getEnemyConeSoftPad();

        if (delta <= softCone) {
            return baseHalfWidth + settings.getEnemyCorridorBoost();
        }

        return baseHalfWidth;
    }

    // ---- Legacy Fallback
    private Aim tryLegacyFallback(DistanceRange range) {
        if (mc.player == null) {
            return null;
        }

        float baseYaw = wrapYaw(mc.player.getYaw() + YAW_UPPER_BOUND);
        float basePitch = (float) -settings.getPitchUpDegrees();

        float[] pitchAdds = settings.shouldEscalatePitch()
            ? new float[]{0f, 15f, 30f}
            : new float[]{0f};

        float[] yawOffsets = settings.shouldTrySideOffsets()
            ? new float[]{0f, 20f, -20f}
            : new float[]{0f};

        LegacySafetyParams params = createLegacySafetyParams();

        for (float pitchAdd : pitchAdds) {
            float pitch = clampPitch(basePitch - pitchAdd);

            for (float yawOffset : yawOffsets) {
                float yaw = wrapYaw(baseYaw + yawOffset);

                if (isValidLegacyAim(yaw, pitch, params)) {
                    return new Aim(yaw, pitch);
                }
            }
        }

        return tryFallbackStraightUp(range);
    }

    private boolean isValidLegacyAim(float yaw, float pitch, LegacySafetyParams params) {
        if (!safetyChecker.hasInitialClearance(yaw, pitch, params.initialClearance)) {
            return false;
        }

        if (!safetyChecker.hasClearPath(yaw, pitch, params.clearCheckDistance)) {
            return false;
        }

        if (safetyChecker.hitsWallEarly(yaw, pitch, params.nearPathCheck)) {
            return false;
        }

        if (!safetyChecker.hasCorridorWidth(yaw, pitch, params.corridorCheckDist, params.corridorHalfWidth)) {
            return false;
        }

        return true;
    }

    private LegacySafetyParams createLegacySafetyParams() {
        return new LegacySafetyParams(
            Math.max(0.6, settings.getInitialClearance()),
            Math.max(3.0, settings.getClearCheckDistance() - 1.0),
            Math.max(3.0, settings.getNearPathCheck() - 1.5),
            Math.max(1.5, settings.getCorridorCheckDist() - 1.0),
            Math.max(0.45, settings.getCorridorHalfWidth() - 0.1)
        );
    }

    private Aim tryFallbackStraightUp(DistanceRange range) {
        if (!settings.useFallbackStraightUp()) {
            return null;
        }

        if (mc.player == null) {
            return null;
        }

        double bestReachable = settings.shouldCancelIfNoEscape()
            ? bestReachableDistance(range)
            : 0.0;

        if (!settings.shouldCancelIfNoEscape() || bestReachable < range.min * 0.9) {
            float yaw = wrapYaw(mc.player.getYaw() + YAW_UPPER_BOUND);
            return new Aim(yaw, MIN_PITCH);
        }

        return null;
    }

    // ---- Best Reachable Distance
    private double bestReachableDistance(DistanceRange range) {
        if (mc.player == null) {
            return 0.0;
        }

        float[] yaws = buildProbeYaws();
        float[] pitches = buildProbePitches();

        double best = 0.0;

        for (float yaw : yaws) {
            for (float pitch : pitches) {
                double distance = probeDistance(yaw, pitch);
                if (range.contains(distance)) {
                    best = Math.max(best, distance);
                }
            }
        }

        return best;
    }

    private float[] buildProbeYaws() {
        float enemyYaw = getNearestEnemyYaw();
        boolean haveEnemy = !Float.isNaN(enemyYaw);

        List<Float> yaws = new ArrayList<>();
        double step = MathHelper.clamp(settings.getProbeStepDegrees(), 10.0, 45.0);

        for (double deg = -180.0; deg < 180.0; deg += step) {
            float yaw = (float) deg;

            if (shouldSkipProbeYaw(yaw, enemyYaw, haveEnemy)) {
                continue;
            }

            yaws.add(yaw);
        }

        if (yaws.isEmpty() && haveEnemy) {
            yaws.add(wrapYaw(enemyYaw + YAW_UPPER_BOUND));
        }

        return toFloatArray(yaws);
    }

    private boolean shouldSkipProbeYaw(float yaw, float enemyYaw, boolean haveEnemy) {
        if (!settings.aimFullCircle() || !settings.avoidEnemyCone() || !haveEnemy) {
            return false;
        }

        float halfCone = (float) (settings.getEnemyConeDegrees() / 2.0);
        return angleDelta(yaw, enemyYaw) <= halfCone;
    }

    private float[] buildProbePitches() {
        int count = Math.max(1, Math.min(6, settings.getProbePitchCount()));
        float[] pitches = new float[count];

        for (int i = 0; i < count; i++) {
            pitches[i] = clampPitch(-2f - i * 6f);
        }

        return pitches;
    }

    private double probeDistance(float yaw, float pitch) {
        if (!safetyChecker.hasInitialClearance(yaw, pitch, settings.getInitialClearance())) {
            return 0.0;
        }

        if (!safetyChecker.hasClearPath(yaw, pitch, settings.getClearCheckDistance())) {
            return 0.0;
        }

        if (safetyChecker.hitsWallEarly(yaw, pitch, settings.getNearPathCheck())) {
            return 0.0;
        }

        return simulator.simulatePearlRange(yaw, pitch, MAX_FLIGHT_TICKS);
    }

    // ---- Enemy Detection
    private float getNearestEnemyYaw() {
        long now = System.currentTimeMillis();

        if (cachedEnemyYaw != null && (now - lastEnemyYawCheck) < ENEMY_YAW_CACHE_MS) {
            return cachedEnemyYaw;
        }

        cachedEnemyYaw = calculateNearestEnemyYaw();
        lastEnemyYawCheck = now;
        return cachedEnemyYaw;
    }

    private float calculateNearestEnemyYaw() {
        if (mc.world == null || mc.player == null) {
            return Float.NaN;
        }

        PlayerEntity nearest = findNearestPlayer();
        if (nearest == null) {
            return Float.NaN;
        }

        return calculateYawToPlayer(nearest);
    }

    private PlayerEntity findNearestPlayer() {
        PlayerEntity nearest = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            double distance = player.squaredDistanceTo(mc.player);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = player;
            }
        }

        return nearest;
    }

    private float calculateYawToPlayer(PlayerEntity target) {
        Vec3d myPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());

        double dx = targetPos.x - myPos.x;
        double dz = targetPos.z - myPos.z;


        float yawRad = (float) Math.atan2(-dx, dz);
        return (float) Math.toDegrees(yawRad);
    }

    // ---- Utility Methods
    private DistanceRange getDistanceRange() {
        double min = Math.min(settings.getMinBackDist(), settings.getMaxBackDist());
        double max = Math.max(settings.getMinBackDist(), settings.getMaxBackDist());
        return new DistanceRange(min, max);
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private static float angleDelta(float a, float b) {
        return Math.abs(wrapYaw(a - b));
    }

    private static float clampPitch(float pitch) {
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
    }

    private static float wrapYaw(float yaw) {
        while (yaw <= YAW_LOWER_BOUND) yaw += YAW_NORMALIZATION;
        while (yaw > YAW_UPPER_BOUND) yaw -= YAW_NORMALIZATION;
        return yaw;
    }

    // ---- Helper Classes
    private static class AimCandidate {
        Float yaw = null;
        Float pitch = null;
        double time = Double.POSITIVE_INFINITY;

        boolean isBetterThan(float candidatePitch, double candidateTime) {
            if (pitch == null) return true;
            if (Math.abs(candidatePitch) < Math.abs(pitch)) return true;
            if (Math.abs(candidatePitch) == Math.abs(pitch) && candidateTime < time) return true;
            return false;
        }

        void update(float newYaw, float newPitch, double newTime) {
            this.yaw = newYaw;
            this.pitch = newPitch;
            this.time = newTime;
        }

        Aim toAim() {
            return pitch != null ? new Aim(yaw, pitch) : null;
        }
    }

    private static class DistanceRange {
        final double min;
        final double max;

        DistanceRange(double min, double max) {
            this.min = min;
            this.max = max;
        }

        boolean contains(double distance) {
            return distance >= min && distance <= max;
        }
    }

    private static class LenientSafetyParams {
        final double initialClearance;
        final double clearCheckDistance;
        final double nearPathCheck;

        LenientSafetyParams(double initialClearance, double clearCheckDistance, double nearPathCheck) {
            this.initialClearance = initialClearance;
            this.clearCheckDistance = clearCheckDistance;
            this.nearPathCheck = nearPathCheck;
        }
    }

    private static class LegacySafetyParams {
        final double initialClearance;
        final double clearCheckDistance;
        final double nearPathCheck;
        final double corridorCheckDist;
        final double corridorHalfWidth;

        LegacySafetyParams(double initialClearance, double clearCheckDistance, double nearPathCheck,
                           double corridorCheckDist, double corridorHalfWidth) {
            this.initialClearance = initialClearance;
            this.clearCheckDistance = clearCheckDistance;
            this.nearPathCheck = nearPathCheck;
            this.corridorCheckDist = corridorCheckDist;
            this.corridorHalfWidth = corridorHalfWidth;
        }
    }

    // ---- Return Type
    public static class Aim {
        public final float yaw;
        public final float pitch;

        public Aim(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
