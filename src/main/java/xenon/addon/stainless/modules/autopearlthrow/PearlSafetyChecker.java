package xenon.addon.stainless.modules.autopearlthrow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class PearlSafetyChecker {

    // ---- Physics Constants
    private static final double PEARL_VELOCITY = 1.5;
    private static final double GRAVITY = 0.03;
    private static final double DRAG = 0.99;
    private static final int MAX_TRAJECTORY_STEPS = 28;

    // ---- Dependencies
    private final PearlThrowSettings settings;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // ---- Constructor
    public PearlSafetyChecker(PearlThrowSettings settings) {
        this.settings = settings;
    }

    // ---- Initial Clearance Check
    /**
     * Checks if there's clear space directly in front of the player
     * @param yawDeg Yaw angle in degrees
     * @param pitchDeg Pitch angle in degrees
     * @param distance Distance to check
     * @return true if clear, false if blocked
     */
    public boolean hasInitialClearance(float yawDeg, float pitchDeg, double distance) {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        Vec3d start = calculateStartPosition(yawDeg, pitchDeg, 0.0);
        Vec3d direction = directionFromYawPitch(yawDeg, pitchDeg);
        Vec3d end = start.add(direction.multiply(Math.max(0.2, distance)));

        return isPathClear(start, end);
    }

    // ---- Path Clearance Check
    /**
     * Checks if there's a clear path along the aim direction
     * @param yawDeg Yaw angle in degrees
     * @param pitchDeg Pitch angle in degrees
     * @param distance Distance to check
     * @return true if clear, false if blocked
     */
    public boolean hasClearPath(float yawDeg, float pitchDeg, double distance) {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        Vec3d start = calculateStartPosition(yawDeg, pitchDeg, 0.0);
        Vec3d direction = directionFromYawPitch(yawDeg, pitchDeg);
        Vec3d end = start.add(direction.multiply(distance));

        return isPathClear(start, end);
    }

    // ---- Early Collision Check
    /**
     * Simulates pearl trajectory to detect early collisions
     * @param yawDeg Yaw angle in degrees
     * @param pitchDeg Pitch angle in degrees
     * @param checkDistance Maximum distance to check
     * @return true if hits wall early, false if clear
     */
    public boolean hitsWallEarly(float yawDeg, float pitchDeg, double checkDistance) {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        Vec3d position = calculateStartPosition(yawDeg, pitchDeg, 0.0);
        Vec3d velocity = directionFromYawPitch(yawDeg, pitchDeg).multiply(PEARL_VELOCITY);

        double stepSize = Math.max(0.3, settings.getRayStep());
        double distanceTraveled = 0.0;

        for (int step = 0; step < MAX_TRAJECTORY_STEPS; step++) {
            Vec3d nextPosition = position.add(velocity.multiply(stepSize));

            // Check for collision on this segment
            if (!isPathClear(position, nextPosition)) {
                return true;
            }

            // Update tracking
            distanceTraveled += nextPosition.subtract(position).length();
            if (distanceTraveled >= Math.max(0.6, checkDistance)) {
                break;
            }

            // Update position and velocity
            position = nextPosition;
            velocity = applyPhysics(velocity);
        }

        return false;
    }

    // ---- Corridor Width Check
    /**
     * Checks if there's sufficient width on both sides for pearl to pass through
     * Uses horizontal rays only to avoid ceiling false positives
     * @param yawDeg Yaw angle in degrees
     * @param pitchDeg Pitch angle in degrees (not used for horizontal check)
     * @param distance How far forward to check
     * @param halfWidth Required clearance on each side
     * @return true if corridor is wide enough, false if too narrow
     */
    public boolean hasCorridorWidth(float yawDeg, float pitchDeg, double distance, double halfWidth) {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        // Calculate horizontal vectors (ignore pitch for corridor check)
        float yawRad = (float) Math.toRadians(yawDeg);
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vec3d left = new Vec3d(-forward.z, 0, forward.x);

        // Calculate check positions
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d leftStart = eyePos.add(left.multiply(halfWidth));
        Vec3d rightStart = eyePos.add(left.multiply(-halfWidth));
        Vec3d leftEnd = leftStart.add(forward.multiply(distance));
        Vec3d rightEnd = rightStart.add(forward.multiply(distance));

        // Check both sides
        boolean leftClear = isPathClear(leftStart, leftEnd);
        boolean rightClear = isPathClear(rightStart, rightEnd);

        return leftClear && rightClear;
    }

    // ---- Helper Methods
    private Vec3d calculateStartPosition(float yawDeg, float pitchDeg, double extraBias) {
        if (mc.player == null) {
            return Vec3d.ZERO;
        }

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d direction = directionFromYawPitch(yawDeg, pitchDeg);
        double totalBias = Math.max(0.0, settings.getStartBias() + extraBias);

        return eyePos.add(direction.multiply(totalBias));
    }

    private boolean isPathClear(Vec3d start, Vec3d end) {
        if (mc.world == null || mc.player == null) {
            return false;
        }

        RaycastContext context = new RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        );

        HitResult hit = mc.world.raycast(context);
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    private Vec3d applyPhysics(Vec3d velocity) {
        return velocity
            .multiply(DRAG, DRAG, DRAG)
            .add(0, -GRAVITY, 0);
    }

    // ---- Direction Calculation (Static Utility)
    /**
     * Converts yaw and pitch angles to a normalized direction vector
     * @param yawDeg Yaw in degrees
     * @param pitchDeg Pitch in degrees
     * @return Normalized direction vector
     */
    public static Vec3d directionFromYawPitch(float yawDeg, float pitchDeg) {
        float yawRad = (float) Math.toRadians(yawDeg);
        float pitchRad = (float) Math.toRadians(pitchDeg);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vec3d(x, y, z).normalize();
    }
}
