package xenon.addon.stainless.modules.autopearlstasis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Controls player movement, rotation, and key presses
 */
public class APSMovementController {

    // ---- Settings
    private final APSSettings settings;

    // ---- State
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private int holdForward = 0;
    private int holdSprint = 0;
    private int holdJump = 0;

    // ---- Constructor
    public APSMovementController(APSSettings settings) {
        this.settings = settings;
    }

    // ---- Helpers
    public void faceTowardXZ(Vec3d target) {
        if (mc.player == null || target == null) return;
        Vec3d playerPos = mc.player.getPos();
        Vec3d flatDelta = new Vec3d(target.x - playerPos.x, 0, target.z - playerPos.z);
        if (flatDelta.lengthSquared() < 1e-6) return;

        float yaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(flatDelta.z, flatDelta.x)) - 90.0));
        mc.player.setYaw(yaw);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            yaw, mc.player.getPitch(), mc.player.isOnGround(), false));
    }

    public void walkTowardExact(Vec3d target) {
        if (mc.player == null || target == null) return;
        Vec3d pos = mc.player.getPos();
        Vec3d flatDelta = new Vec3d(target.x - pos.x, 0, target.z - pos.z);
        if (flatDelta.lengthSquared() < 1e-5) return;

        faceTowardXZ(target);
        pressForward(3);
        pressSprint(5);

        if (mc.player.isTouchingWater()) {
            Vec3d vel = mc.player.getVelocity();
            Vec3d fwd = new Vec3d(-Math.sin(Math.toRadians(mc.player.getYaw())), 0, Math.cos(Math.toRadians(mc.player.getYaw())));
            mc.player.setVelocity(vel.add(fwd.multiply(0.04)).add(0, 0.03, 0));
        }
    }

    public void strongWaterEscapeBoost(Vec3d target) {
        if (mc.player == null) return;
        faceTowardXZ(target);
        try { mc.player.jump(); } catch (Throwable ignored) {}
        float yaw = mc.player.getYaw();
        Vec3d vel = mc.player.getVelocity();
        Vec3d dir = new Vec3d(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        mc.player.setVelocity(vel.add(dir.multiply(0.20)).add(0, 0.32, 0));
        mc.player.setSprinting(true);
    }

    public void stepBackFromStasis(Vec3d stepBackTarget) {
        if (stepBackTarget == null || mc.player == null) return;

        releaseSneak();
        faceTowardXZ(stepBackTarget);

        pressForward(5);
        pressSprint(8);

        float yaw = mc.player.getYaw();
        Vec3d vel = mc.player.getVelocity();
        Vec3d dir = new Vec3d(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        mc.player.setVelocity(vel.add(dir.multiply(0.12)));
    }

    public void pressSneak() {
        try {
            Object opts = mc.options;
            var f = opts.getClass().getField("sneakKey");
            Object key = f.get(opts);
            key.getClass().getMethod("setPressed", boolean.class).invoke(key, true);
        } catch (Throwable t) {
            try { mc.player.setSneaking(true); } catch (Throwable ignored) {}
        }
    }

    public void releaseSneak() {
        try {
            Object opts = mc.options;
            var f = opts.getClass().getField("sneakKey");
            Object key = f.get(opts);
            key.getClass().getMethod("setPressed", boolean.class).invoke(key, false);
        } catch (Throwable t) {
            try { mc.player.setSneaking(false); } catch (Throwable ignored) {}
        }
    }

    public void pressForward(int ticks) { holdForward = Math.max(holdForward, ticks); }
    public void pressSprint(int ticks)  { holdSprint  = Math.max(holdSprint , ticks); }
    public void pressJump(int ticks)    { holdJump    = Math.max(holdJump   , ticks); }

    public void maintainMovementKeys() {
        setKeyPressed("forwardKey", holdForward-- > 0);
        setKeyPressed("sprintKey",  holdSprint--  > 0);
        setKeyPressed("jumpKey",    holdJump--    > 0);
    }

    public void releaseAllMoveKeys() {
        holdForward = holdSprint = holdJump = 0;
        setKeyPressed("forwardKey", false);
        setKeyPressed("sprintKey", false);
        setKeyPressed("jumpKey", false);
    }

    private void setKeyPressed(String fieldName, boolean pressed) {
        try {
            Object opts = mc.options;
            Object key = opts.getClass().getField(fieldName).get(opts);
            key.getClass().getMethod("setPressed", boolean.class).invoke(key, pressed);
        } catch (Throwable ignored) {}
    }
}
