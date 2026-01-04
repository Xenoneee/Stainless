package xenon.addon.stainless.modules.autopearlthrow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class PearlPhysicsSimulator {

    // ---- Physics Constants
    private static final double GRAVITY = 0.03;
    private static final double DRAG = 0.99;
    private static final double INITIAL_VELOCITY = 1.5;
    private static final double GROUND_THRESHOLD = 0.1;
    private static final int MIN_TICKS_BEFORE_LANDING = 2;
    private static final double MAX_VERTICAL_DEVIATION = 80.0;

    // ---- Dependencies
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // ---- Flight Time Estimation
    /**
     * Estimates how many ticks until pearl lands on ground
     * @param yawDeg Yaw angle in degrees
     * @param pitchDeg Pitch angle in degrees
     * @param maxTicks Maximum simulation time
     * @return Estimated flight time in ticks, or maxTicks if doesn't land
     */
    public double estimateFlightTicks(float yawDeg, float pitchDeg, int maxTicks) {
        if (mc.player == null) {
            return Double.POSITIVE_INFINITY;
        }

        Vec3d position = mc.player.getEyePos();
        Vec3d velocity = calculateInitialVelocity(yawDeg, pitchDeg);
        double groundLevel = mc.player.getY();

        for (int tick = 0; tick < maxTicks; tick++) {
            position = position.add(velocity);
            velocity = applyPhysics(velocity);

            if (hasLanded(position.y, groundLevel, tick)) {
                return tick;
            }
        }

        return maxTicks;
    }

    // ---- Range Simulation
    /**
     * Simulates horizontal distance traveled by pearl
     * @param yawDeg Yaw angle in degrees
     * @param pitchDeg Pitch angle in degrees
     * @param maxTicks Maximum simulation time
     * @return Horizontal distance in blocks
     */
    public double simulatePearlRange(float yawDeg, float pitchDeg, int maxTicks) {
        if (mc.player == null) {
            return 0.0;
        }

        Vec3d position = mc.player.getEyePos();
        Vec3d velocity = calculateInitialVelocity(yawDeg, pitchDeg);

        double startY = mc.player.getY();
        double groundLevel = startY;
        double horizontalDistance = 0.0;

        for (int tick = 0; tick < maxTicks; tick++) {
            position = position.add(velocity);
            velocity = applyPhysics(velocity);

            // Update horizontal distance
            horizontalDistance = calculateHorizontalDistance(position);

            // Check landing
            if (hasLanded(position.y, groundLevel, tick)) {
                break;
            }

            // Safety check for runaway trajectories
            if (isOutOfBounds(position.y, startY)) {
                break;
            }
        }

        return horizontalDistance;
    }

    // ---- Landing Position Simulation
    /**
     * Simulates and returns the exact landing position of a pearl
     * @param yawDeg Yaw angle in degrees
     * @param pitchDeg Pitch angle in degrees
     * @param maxTicks Maximum simulation time
     * @return Landing position, or last position if doesn't land
     */
    public Vec3d simulateLandingPosition(float yawDeg, float pitchDeg, int maxTicks) {
        if (mc.player == null) {
            return null;
        }

        Vec3d position = mc.player.getEyePos();
        Vec3d velocity = calculateInitialVelocity(yawDeg, pitchDeg);

        double startY = mc.player.getY();
        double groundLevel = startY;

        for (int tick = 0; tick < maxTicks; tick++) {
            position = position.add(velocity);
            velocity = applyPhysics(velocity);

            if (hasLanded(position.y, groundLevel, tick)) {
                return position;
            }

            if (isOutOfBounds(position.y, startY)) {
                break;
            }
        }

        // Return last position if didn't land
        return position;
    }

    // ---- Helper Methods
    private Vec3d calculateInitialVelocity(float yawDeg, float pitchDeg) {
        return PearlSafetyChecker.directionFromYawPitch(yawDeg, pitchDeg)
            .multiply(INITIAL_VELOCITY);
    }

    private Vec3d applyPhysics(Vec3d velocity) {
        return velocity
            .multiply(DRAG, DRAG, DRAG)
            .add(0, -GRAVITY, 0);
    }

    private double calculateHorizontalDistance(Vec3d position) {
        if (mc.player == null) {
            return 0.0;
        }

        Vec3d horizontalOffset = new Vec3d(
            position.x - mc.player.getX(),
            0,
            position.z - mc.player.getZ()
        );

        return horizontalOffset.length();
    }

    private boolean hasLanded(double currentY, double groundLevel, int tick) {
        return currentY <= groundLevel + GROUND_THRESHOLD
            && tick > MIN_TICKS_BEFORE_LANDING;
    }

    private boolean isOutOfBounds(double currentY, double startY) {
        double deviation = Math.abs(currentY - startY);
        return deviation > MAX_VERTICAL_DEVIATION;
    }
}
