package xenon.addon.stainless.modules.autopearlthrow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class PearlPhysicsSimulator {
    private static final double GRAVITY = 0.03;
    private static final double DRAG = 0.99;
    private static final double INITIAL_VELOCITY = 1.5;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public double estimateFlightTicks(float yawDeg, float pitchDeg, int maxTicks) {
        if (mc.player == null) return Double.POSITIVE_INFINITY;

        Vec3d pos = mc.player.getEyePos();
        Vec3d vel = PearlSafetyChecker.directionFromYawPitch(yawDeg, pitchDeg)
            .multiply(INITIAL_VELOCITY);

        double groundY = mc.player.getY();

        for (int i = 0; i < maxTicks; i++) {
            pos = pos.add(vel);
            vel = vel.multiply(DRAG, DRAG, DRAG).add(0, -GRAVITY, 0);

            if (pos.y <= groundY + 0.1 && i > 2) {
                return i;
            }
        }

        return maxTicks;
    }

    public double simulatePearlRange(float yawDeg, float pitchDeg, int maxTicks) {
        if (mc.player == null) return 0.0;

        Vec3d pos = mc.player.getEyePos();
        Vec3d vel = PearlSafetyChecker.directionFromYawPitch(yawDeg, pitchDeg)
            .multiply(INITIAL_VELOCITY);

        double startY = mc.player.getY();
        double groundY = startY;
        double horizontalDist = 0.0;

        for (int i = 0; i < maxTicks; i++) {
            pos = pos.add(vel);
            vel = vel.multiply(DRAG, DRAG, DRAG).add(0, -GRAVITY, 0);

            Vec3d flat = new Vec3d(
                pos.x - mc.player.getX(),
                0,
                pos.z - mc.player.getZ()
            );
            horizontalDist = flat.length();

            // Check if pearl has landed
            if (pos.y <= groundY + 0.1 && i > 2) {
                break;
            }

            // Safety bounds check
            if (pos.y > startY + 80 || pos.y < startY - 80) {
                break;
            }
        }

        return horizontalDist;
    }

    /**
     * Get the landing position of a pearl thrown at the given angle
     */
    public Vec3d simulateLandingPosition(float yawDeg, float pitchDeg, int maxTicks) {
        if (mc.player == null) return null;

        Vec3d pos = mc.player.getEyePos();
        Vec3d vel = PearlSafetyChecker.directionFromYawPitch(yawDeg, pitchDeg)
            .multiply(INITIAL_VELOCITY);

        double startY = mc.player.getY();
        double groundY = startY;

        for (int i = 0; i < maxTicks; i++) {
            pos = pos.add(vel);
            vel = vel.multiply(DRAG, DRAG, DRAG).add(0, -GRAVITY, 0);

            if (pos.y <= groundY + 0.1 && i > 2) {
                return pos;
            }

            if (pos.y > startY + 80 || pos.y < startY - 80) {
                break;
            }
        }

        return pos;
    }
}
