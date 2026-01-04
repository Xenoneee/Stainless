package xenon.addon.stainless.modules.autopearlstasis;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import com.mojang.authlib.GameProfile;

import java.util.*;

/**
 * Main account mode - monitors totems, handles proximity, sends messages
 */
public class APSMainMode {

    // ---- Settings
    private final APSSettings settings;

    // ---- State
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Runnable onTeleportTriggered;
    private final Runnable debugLogger;
    private final Runnable infoLogger;

    private int lastCount = -1;
    private boolean key1WasDown = false;
    private boolean key2WasDown = false;

    // Proximity tracking
    private final Set<UUID> proxSeen = new HashSet<>();
    private int proxCdTicks = 0;
    private int proxMotionTicks = 0;
    private Vec3d lastPos = null;

    // Teleport detection
    private boolean teleportPending = false;
    private Vec3d teleportOrigin = null;
    private int teleportWindowTicks = 0;
    private int postTeleportDelayTicks = -1;

    // ---- Constructor
    public APSMainMode(APSSettings settings, Runnable onTeleportTriggered, Runnable debugLogger, Runnable infoLogger) {
        this.settings = settings;
        this.onTeleportTriggered = onTeleportTriggered;
        this.debugLogger = debugLogger;
        this.infoLogger = infoLogger;
    }

    // ---- Lifecycle
    public void reset() {
        lastCount = APSUtil.countTotems();
        key1WasDown = false;
        key2WasDown = false;
        proxSeen.clear();
        proxCdTicks = 0;
        proxMotionTicks = 0;
        lastPos = null;
        teleportPending = false;
        teleportOrigin = null;
        teleportWindowTicks = 0;
        postTeleportDelayTicks = -1;
    }

    public void tick(int ticksSinceJoin) {
        if (mc.player == null || mc.world == null) return;

        // Track motion for proximity
        if (lastPos != null) {
            double dx = mc.player.getX() - lastPos.x;
            double dz = mc.player.getZ() - lastPos.z;
            double sp2 = dx * dx + dz * dz;
            if (sp2 > 0.0025) proxMotionTicks = Math.min(40, proxMotionTicks + 1);
        }

        if (proxCdTicks > 0) proxCdTicks--;

        // Handle hotkeys
        handleHotkeys();

        // Auto-send based on totem count
        int current = APSUtil.countTotems();
        if (current != lastCount) {
            if (current <= settings.threshold1.get()) {
                sendMessageTo(settings.altName1.get(), current);
                markTeleportPending();
            }
            if (current <= settings.threshold2.get()) {
                sendMessageTo(settings.altName2.get(), current);
                markTeleportPending();
            }
        }
        lastCount = current;

        // Proximity trigger
        if (settings.enableProx.get()) {
            runProximityTrigger(ticksSinceJoin);
        }

        // Detect teleport by position jump
        if (teleportPending) {
            if (teleportWindowTicks-- <= 0) {
                teleportPending = false;
            } else {
                // FIXED: Manually construct Vec3d to avoid getPos()/position() mapping conflict
                Vec3d now = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                if (now.squaredDistanceTo(teleportOrigin) > (12 * 12) || Math.abs(now.y - teleportOrigin.y) > 4.0) {
                    teleportPending = false;
                    postTeleportDelayTicks = settings.postTpDelay.get();
                }
            }
        }

        // Trigger assist after delay
        if (postTeleportDelayTicks >= 0 && --postTeleportDelayTicks == 0) {
            onTeleportTriggered.run();
        }

        // FIXED: Manually construct Vec3d
        lastPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    // ---- Events
    @EventHandler
    public void onMainChat(ReceiveMessageEvent event) {
        if (!settings.useChatConfirm.get()) return;
        String m = event.getMessage().getString().toLowerCase(Locale.ROOT);

        boolean fromAlt = false;
        String a1 = settings.altName1.get().trim().toLowerCase(Locale.ROOT);
        String a2 = settings.altName2.get().trim().toLowerCase(Locale.ROOT);
        if (!a1.isEmpty() && m.contains(a1)) fromAlt = true;
        if (!a2.isEmpty() && m.contains(a2)) fromAlt = true;

        if ((fromAlt && m.contains("tp-ok")) || (!fromAlt && m.contains("tp-ok"))) {
            teleportPending = false;
            postTeleportDelayTicks = settings.postTpDelay.get();
        }
    }

    // ---- Helpers
    private void handleHotkeys() {
        long handle = mc.getWindow() != null ? mc.getWindow().getHandle() : 0;

        // Alt 1 hotkey
        int kc1 = APSUtil.parseKey(settings.forceKey1.get());
        if (handle != 0 && kc1 != GLFW.GLFW_KEY_UNKNOWN) {
            boolean down = GLFW.glfwGetKey(handle, kc1) == GLFW.GLFW_PRESS;
            if (down && !key1WasDown) settings.forceTeleport1.set(true);
            key1WasDown = down;
        } else {
            key1WasDown = false;
        }

        // Alt 2 hotkey
        int kc2 = APSUtil.parseKey(settings.forceKey2.get());
        if (handle != 0 && kc2 != GLFW.GLFW_KEY_UNKNOWN) {
            boolean down = GLFW.glfwGetKey(handle, kc2) == GLFW.GLFW_PRESS;
            if (down && !key2WasDown) settings.forceTeleport2.set(true);
            key2WasDown = down;
        } else {
            key2WasDown = false;
        }

        // Execute force teleports
        if (settings.forceTeleport1.get()) {
            sendMessageTo(settings.altName1.get(), Math.max(1, settings.threshold1.get()));
            markTeleportPending();
            settings.forceTeleport1.set(false);
        }
        if (settings.forceTeleport2.get()) {
            sendMessageTo(settings.altName2.get(), Math.max(1, settings.threshold2.get()));
            markTeleportPending();
            settings.forceTeleport2.set(false);
        }
    }

    private void runProximityTrigger(int ticksSinceJoin) {
        if (ticksSinceJoin < settings.proxWarmup.get()) return;
        if (proxCdTicks > 0) return;
        if (settings.proxRequireMotion.get() && proxMotionTicks < 6) return;
        if (teleportPending || postTeleportDelayTicks >= 0) return;

        Set<String> watch = buildWatchSet();
        boolean matchAnyone = settings.proxMatchAnyone.get() && watch.isEmpty();
        if (!matchAnyone && watch.isEmpty()) return;

        int r = Math.max(8, settings.proxRadius.get());
        final double r2 = r * r;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;

            // FIXED: Removed reliance on GameProfile.getName() which can be buggy in some mappings
            // Using p.getName().getString() is safer and more consistent
            String name = p.getName().getString();

            double d2 = p.squaredDistanceTo(mc.player);
            if (d2 > r2) continue;

            if (name == null) continue;

            String norm = APSUtil.normalizeName(name);
            if (!matchAnyone && !watch.contains(norm)) continue;

            if (proxSeen.contains(p.getUuid())) continue;

            BlockPos bp = p.getBlockPos();
            String info = "Proximity: " + name + " @ " + bp.getX() + " " + bp.getY() + " " + bp.getZ();

            boolean pinged = false;
            switch (settings.proxTargetAlt.get()) {
                case ALT1 -> {
                    if (!settings.altName1.get().trim().isEmpty()) {
                        sendMessageTo(settings.altName1.get(), Math.max(1, settings.threshold1.get()));
                        pinged = true;
                    }
                }
                case ALT2 -> {
                    if (!settings.altName2.get().trim().isEmpty()) {
                        sendMessageTo(settings.altName2.get(), Math.max(1, settings.threshold2.get()));
                        pinged = true;
                    }
                }
                case BOTH -> {
                    if (!settings.altName1.get().trim().isEmpty()) {
                        sendMessageTo(settings.altName1.get(), Math.max(1, settings.threshold1.get()));
                        pinged = true;
                    }
                    if (!settings.altName2.get().trim().isEmpty()) {
                        sendMessageTo(settings.altName2.get(), Math.max(1, settings.threshold2.get()));
                        pinged = true;
                    }
                }
            }

            if (pinged) {
                if (settings.proxChatFeedback.get()) infoLogger.run();
                markTeleportPending();
                proxSeen.add(p.getUuid());
                proxCdTicks = Math.max(0, settings.proxCooldown.get());
                break;
            }
        }

        proxSeen.retainAll(currentPlayerUUIDsInRadius(r2));
    }

    private Set<String> buildWatchSet() {
        Set<String> set = new LinkedHashSet<>();
        List<String> list = settings.proxNameList.get();
        if (list != null) {
            for (String s : list) {
                String tok = APSUtil.normalizeName(s);
                if (!tok.isEmpty()) set.add(tok);
            }
        }

        String raw = settings.proxNames.get();
        if (raw != null && !raw.trim().isEmpty()) {
            for (String s : raw.split(",")) {
                String tok = APSUtil.normalizeName(s);
                if (!tok.isEmpty()) set.add(tok);
            }
        }

        return set;
    }

    private Set<UUID> currentPlayerUUIDsInRadius(double r2) {
        Set<UUID> set = new HashSet<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            // FIXED: Removed GameProfile check here as it wasn't strictly necessary for distance check
            if (p.squaredDistanceTo(mc.player) <= r2) set.add(p.getUuid());
        }
        return set;
    }

    private void markTeleportPending() {
        teleportPending = true;
        // FIXED: Manually construct Vec3d
        teleportOrigin = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        teleportWindowTicks = 200;
        postTeleportDelayTicks = -1;
    }

    private void sendMessageTo(String alt, int count) {
        String name = alt == null ? "" : alt.trim();
        if (!name.isEmpty()) ChatUtils.sendPlayerMsg("/msg " + name + " " + count + " totem remaining");
    }

    public boolean shouldTriggerAssist() {
        return postTeleportDelayTicks == 0;
    }
}
