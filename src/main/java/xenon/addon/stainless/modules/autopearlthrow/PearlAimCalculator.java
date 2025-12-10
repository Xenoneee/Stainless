package xenon.addon.stainless.modules.autopearlthrow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class PearlAimCalculator {

    // ---- Settings
    private final PearlThrowSettings settings;

    // ---- State
    private final PearlSafetyChecker safetyChecker;
    private final PearlPhysicsSimulator simulator;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // ---- Constructor
    public PearlAimCalculator(PearlThrowSettings settings, PearlSafetyChecker safetyChecker) {
        this.settings = settings;
        this.safetyChecker = safetyChecker;
        this.simulator = new PearlPhysicsSimulator();
    }

    // ---- Helpers
    public Aim calculateBestAim() {
        Aim aim = pickAimSmart();
        if (aim == null) {
            aim = pickAimLenient();
        }
        return aim;
    }

    private Aim pickAimSmart() {
        double minD = Math.min(settings.getMinBackDist(), settings.getMaxBackDist());
        double maxD = Math.max(settings.getMinBackDist(), settings.getMaxBackDist());

        // Check if any escape is possible
        if (settings.shouldCancelIfNoEscape()) {
            double best = bestReachableDistance(minD, maxD);
            if (best < minD) return null;
        }

        // Build yaw candidates
        float[] yawCandidates = buildYawCandidates();

        Float bestPitch = null;
        Float bestYaw = null;
        double bestTime = Double.POSITIVE_INFINITY;

        for (float yawBase : yawCandidates) {
            for (float wiggle : getYawWiggles()) {
                float yaw = wrapYaw(yawBase + wiggle);

                Float pitch = calculatePitchForYaw(yaw, minD, maxD);
                if (pitch == null) continue;

                if (!passesAllSafetyChecks(yaw, pitch)) continue;

                double t = simulator.estimateFlightTicks(yaw, pitch, 80);
                if (isBetterAim(pitch, bestPitch, t, bestTime)) {
                    bestPitch = pitch;
                    bestYaw = yaw;
                    bestTime = t;
                }
            }
        }

        if (bestPitch != null) {
            return new Aim(bestYaw, bestPitch);
        }

        // Legacy behind-you fallback
        return tryLegacyFallback(minD, maxD);
    }

    private Aim pickAimLenient() {
        double minD = Math.min(settings.getMinBackDist(), settings.getMaxBackDist());
        double maxD = Math.max(settings.getMinBackDist(), settings.getMaxBackDist());

        List<Float> yaws = new ArrayList<>();
        for (int deg = 0; deg < 360; deg += 20) {
            yaws.add((float) deg - 180f);
        }

        for (float yaw : yaws) {
            for (float pitch = -4f; pitch >= -80f; pitch -= 2f) {
                if (!safetyChecker.hasInitialClearance(yaw, pitch,
                    Math.max(0.6, settings.getInitialClearance() - 0.3))) {
                    continue;
                }

                double d = simulator.simulatePearlRange(yaw, pitch, 80);
                if (d < minD || d > maxD) continue;

                if (!safetyChecker.hasClearPath(yaw, pitch,
                    Math.max(3.0, settings.getClearCheckDistance() - 1.0))) {
                    continue;
                }

                if (safetyChecker.hitsWallEarly(yaw, pitch,
                    Math.max(3.0, settings.getNearPathCheck() - 1.5))) {
                    continue;
                }

                return new Aim(yaw, clampPitch(pitch));
            }
        }

        return null;
    }

    private float[] buildYawCandidates() {
        float enemyYaw = getNearestEnemyYaw();
        boolean haveEnemy = !Float.isNaN(enemyYaw);

        if (settings.aimFullCircle()) {
            List<Float> yaws = new ArrayList<>();
            for (int deg = 0; deg < 360; deg += 15) {
                float yaw = (float) deg - 180f;

                if (haveEnemy && settings.avoidEnemyCone()) {
                    if (angleDelta(yaw, enemyYaw) <= (float) (settings.getEnemyConeDegrees() / 2.0)) {
                        continue;
                    }
                }

                yaws.add(yaw);
            }

            // Fallback: if all yaws filtered, use opposite of enemy
            if (yaws.isEmpty() && haveEnemy) {
                yaws.add(wrapYaw(enemyYaw + 180f));
            }

            float[] result = new float[yaws.size()];
            for (int i = 0; i < yaws.size(); i++) {
                result[i] = yaws.get(i);
            }
            return result;
        } else {
            // Behind player only
            float baseYaw = wrapYaw(mc.player.getYaw() + 180f);
            if (settings.shouldTrySideOffsets()) {
                return new float[]{baseYaw, wrapYaw(baseYaw + 20f), wrapYaw(baseYaw - 20f)};
            } else {
                return new float[]{baseYaw};
            }
        }
    }

    private Float calculatePitchForYaw(float yaw, double minD, double maxD) {
        if (settings.useAutoPitch()) {
            return findFlattestPitchForDistanceRange(yaw, minD, maxD);
        } else {
            return clampPitch((float) -settings.getPitchUpDegrees());
        }
    }

    private Float findFlattestPitchForDistanceRange(float yaw, double minD, double maxD) {
        // Try normal resolution first
        for (float pitch = -2f; pitch >= -89f; pitch -= 0.5f) {
            if (!safetyChecker.hasInitialClearance(yaw, pitch, settings.getInitialClearance())) {
                continue;
            }

            double d = simulator.simulatePearlRange(yaw, pitch, 80);
            if (d >= minD && d <= maxD) {
                if (safetyChecker.hasClearPath(yaw, pitch, settings.getClearCheckDistance())
                    && !safetyChecker.hitsWallEarly(yaw, pitch, settings.getNearPathCheck())) {
                    return clampPitch(pitch);
                }
            }
        }

        // Try finer resolution if escalate is enabled
        if (settings.shouldEscalatePitch()) {
            for (float pitch = -2f; pitch >= -89f; pitch -= 0.25f) {
                if (!safetyChecker.hasInitialClearance(yaw, pitch, settings.getInitialClearance())) {
                    continue;
                }

                double d = simulator.simulatePearlRange(yaw, pitch, 80);
                if (d >= minD && d <= maxD) {
                    if (safetyChecker.hasClearPath(yaw, pitch, settings.getClearCheckDistance())
                        && !safetyChecker.hitsWallEarly(yaw, pitch, settings.getNearPathCheck())) {
                        return clampPitch(pitch);
                    }
                }
            }
        }

        return null;
    }

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

        // Corridor width guard
        double halfW = calculateRequiredCorridorWidth(yaw);
        if (!safetyChecker.hasCorridorWidth(yaw, pitch, settings.getCorridorCheckDist(), halfW)) {
            return false;
        }

        return true;
    }

    private double calculateRequiredCorridorWidth(float yaw) {
        double halfW = settings.getCorridorHalfWidth();

        if (settings.aimFullCircle() && settings.avoidEnemyCone()) {
            float enemyYaw = getNearestEnemyYaw();
            if (!Float.isNaN(enemyYaw)) {
                float delta = angleDelta(yaw, enemyYaw);
                float hard = (float) (settings.getEnemyConeDegrees() / 2.0);
                float soft = hard + (float) settings.getEnemyConeSoftPad();

                if (delta <= soft) {
                    halfW += settings.getEnemyCorridorBoost();
                }
            }
        }

        return halfW;
    }

    private Aim tryLegacyFallback(double minD, double maxD) {
        float baseYaw = wrapYaw(mc.player.getYaw() + 180f);
        float basePitch = (float) -settings.getPitchUpDegrees();

        float[] pitchAdds = settings.shouldEscalatePitch()
            ? new float[]{0f, +15f, +30f}
            : new float[]{0f};

        float[] yawOffsets = settings.shouldTrySideOffsets()
            ? new float[]{0f, +20f, -20f}
            : new float[]{0f};

        for (float pAdd : pitchAdds) {
            float pitch = clampPitch(basePitch - pAdd);

            for (float yOff : yawOffsets) {
                float yaw = wrapYaw(baseYaw + yOff);

                if (safetyChecker.hasInitialClearance(yaw, pitch,
                    Math.max(0.6, settings.getInitialClearance()))
                    && safetyChecker.hasClearPath(yaw, pitch,
                    Math.max(3.0, settings.getClearCheckDistance() - 1.0))
                    && !safetyChecker.hitsWallEarly(yaw, pitch,
                    Math.max(3.0, settings.getNearPathCheck() - 1.5))) {

                    if (safetyChecker.hasCorridorWidth(yaw, pitch,
                        Math.max(1.5, settings.getCorridorCheckDist() - 1.0),
                        Math.max(0.45, settings.getCorridorHalfWidth() - 0.1))) {
                        return new Aim(yaw, pitch);
                    }
                }
            }
        }

        // Straight-up fallback
        if (settings.useFallbackStraightUp()) {
            double best = settings.shouldCancelIfNoEscape()
                ? bestReachableDistance(minD, maxD)
                : 0.0;

            if (!settings.shouldCancelIfNoEscape() || best < minD * 0.9) {
                float fyaw = wrapYaw(mc.player.getYaw() + 180f);
                return new Aim(fyaw, -89.0f);
            }
        }

        return null;
    }

    private double bestReachableDistance(double minD, double maxD) {
        if (mc.player == null) return 0.0;

        float enemyYaw = getNearestEnemyYaw();
        boolean haveEnemy = !Float.isNaN(enemyYaw);

        List<Float> yaws = new ArrayList<>();
        double step = MathHelper.clamp(settings.getProbeStepDegrees(), 10.0, 45.0);

        for (double deg = -180.0; deg < 180.0; deg += step) {
            float yaw = (float) deg;

            if (settings.aimFullCircle() && settings.avoidEnemyCone() && haveEnemy) {
                if (angleDelta(yaw, enemyYaw) <= (float) (settings.getEnemyConeDegrees() / 2.0)) {
                    continue;
                }
            }

            yaws.add(yaw);
        }

        if (yaws.isEmpty() && haveEnemy) {
            yaws.add(wrapYaw(enemyYaw + 180f));
        }

        int pc = Math.max(1, Math.min(6, settings.getProbePitchCount()));
        float[] pitches = new float[pc];
        for (int i = 0; i < pc; i++) {
            pitches[i] = clampPitch(-2f - i * 6f);
        }

        double best = 0.0;
        for (float yaw : yaws) {
            for (float pitch : pitches) {
                if (!safetyChecker.hasInitialClearance(yaw, pitch, settings.getInitialClearance())) {
                    continue;
                }

                if (!safetyChecker.hasClearPath(yaw, pitch, settings.getClearCheckDistance())) {
                    continue;
                }

                if (safetyChecker.hitsWallEarly(yaw, pitch, settings.getNearPathCheck())) {
                    continue;
                }

                double d = simulator.simulatePearlRange(yaw, pitch, 80);
                if (d >= minD && d <= maxD) {
                    best = Math.max(best, d);
                }
            }
        }

        return best;
    }

    private float getNearestEnemyYaw() {
        if (mc.world == null || mc.player == null) return Float.NaN;

        PlayerEntity nearest = null;
        double best = Double.POSITIVE_INFINITY;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double d = p.squaredDistanceTo(mc.player);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }

        if (nearest == null) return Float.NaN;

        Vec3d me = mc.player.getPos();
        Vec3d en = nearest.getPos();
        double dx = en.x - me.x;
        double dz = en.z - me.z;
        float yawRad = (float) Math.atan2(-dx, dz);

        return (float) Math.toDegrees(yawRad);
    }

    private boolean isBetterAim(Float pitch, Float bestPitch, double time, double bestTime) {
        if (bestPitch == null) return true;
        if (Math.abs(pitch) < Math.abs(bestPitch)) return true;
        if (Math.abs(pitch) == Math.abs(bestPitch) && time < bestTime) return true;
        return false;
    }

    private static float[] getYawWiggles() {
        return new float[]{0f, +7f, -7f, +12f, -12f};
    }

    private static float angleDelta(float a, float b) {
        return Math.abs(wrapYaw(a - b));
    }

    private static float clampPitch(float p) {
        return Math.max(-89.9f, Math.min(89.9f, p));
    }

    private static float wrapYaw(float yaw) {
        while (yaw <= -180f) yaw += 360f;
        while (yaw > 180f) yaw -= 360f;
        return yaw;
    }

    // ---- Enums
    public static class Aim {
        public final float yaw;
        public final float pitch;

        public Aim(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
