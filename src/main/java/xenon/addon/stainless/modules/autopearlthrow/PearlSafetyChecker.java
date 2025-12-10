package xenon.addon.stainless.modules.autopearlthrow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class PearlSafetyChecker {
    private final PearlThrowSettings settings;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public PearlSafetyChecker(PearlThrowSettings settings) {
        this.settings = settings;
    }

    public boolean hasInitialClearance(float yawDeg, float pitchDeg, double dist) {
        Vec3d start = biasedStart(yawDeg, pitchDeg, 0.0);
        Vec3d dir = directionFromYawPitch(yawDeg, pitchDeg);
        Vec3d end = start.add(dir.multiply(Math.max(0.2, dist)));

        RaycastContext ctx = new RaycastContext(start, end,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        HitResult hit = mc.world.raycast(ctx);

        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    public boolean hasClearPath(float yawDeg, float pitchDeg, double distance) {
        Vec3d start = biasedStart(yawDeg, pitchDeg, 0.0);
        Vec3d dir = directionFromYawPitch(yawDeg, pitchDeg);
        Vec3d end = start.add(dir.multiply(distance));

        RaycastContext ctx = new RaycastContext(start, end,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        HitResult hit = mc.world.raycast(ctx);

        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    public boolean hitsWallEarly(float yawDeg, float pitchDeg, double checkBlocks) {
        if (mc.player == null) return false;

        Vec3d last = biasedStart(yawDeg, pitchDeg, 0.0);
        Vec3d vel = directionFromYawPitch(yawDeg, pitchDeg).multiply(1.5f);

        final double gravity = 0.03;
        final double drag = 0.99;
        double traveled = 0.0;
        double step = Math.max(0.3, settings.getRayStep());

        for (int i = 0; i < 28; i++) {
            Vec3d next = last.add(vel.multiply(step));

            RaycastContext ctx = new RaycastContext(last, next,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            HitResult hr = mc.world.raycast(ctx);

            if (hr != null && hr.getType() != HitResult.Type.MISS) {
                return true;
            }

            traveled += next.subtract(last).length();
            if (traveled >= Math.max(0.6, checkBlocks)) break;

            last = next;
            vel = vel.multiply(drag, drag, drag).add(0, -gravity, 0);
        }

        return false;
    }

    /**
     * Corridor/slit guard — HORIZONTAL rays only (no ceiling false positives).
     */
    public boolean hasCorridorWidth(float yawDeg, float pitchDeg, double dist, double halfWidth) {
        if (mc.player == null) return false;

        // Horizontal forward vector (ignore pitch)
        float yawRad = (float) Math.toRadians(yawDeg);
        Vec3d fwd = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vec3d left = new Vec3d(-fwd.z, 0, fwd.x);

        Vec3d eye = mc.player.getEyePos();
        Vec3d startL = eye.add(left.multiply(halfWidth));
        Vec3d startR = eye.add(left.multiply(-halfWidth));
        Vec3d endL = startL.add(fwd.multiply(dist));
        Vec3d endR = startR.add(fwd.multiply(dist));

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

    private Vec3d biasedStart(float yawDeg, float pitchDeg, double extra) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = directionFromYawPitch(yawDeg, pitchDeg);
        double bias = Math.max(0.0, settings.getStartBias() + extra);
        return eye.add(dir.multiply(bias));
    }

    public static Vec3d directionFromYawPitch(float yawDeg, float pitchDeg) {
        float yaw = (float) Math.toRadians(yawDeg);
        float pitch = (float) Math.toRadians(pitchDeg);
        double x = -Math.sin(yaw) * Math.cos(pitch);
        double y = -Math.sin(pitch);
        double z = Math.cos(yaw) * Math.cos(pitch);
        return new Vec3d(x, y, z).normalize();
    }
}
