package xenon.addon.stainless.modules.autopearlstasis;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Alt account mode - receives messages and activates stasis
 */
public class APSAltMode {

    // ---- Settings
    private final APSSettings settings;

    // ---- State
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final APSStasisFinder stasisFinder;
    private final Runnable debugLogger;
    private final Runnable infoLogger;

    private BlockPos lastTrapdoorUsed = null;
    private int reopenDelayTicks = -1;

    // ---- Constructor
    public APSAltMode(APSSettings settings, APSStasisFinder stasisFinder, Runnable debugLogger, Runnable infoLogger) {
        this.settings = settings;
        this.stasisFinder = stasisFinder;
        this.debugLogger = debugLogger;
        this.infoLogger = infoLogger;
    }

    // ---- Lifecycle
    public void reset() {
        lastTrapdoorUsed = null;
        reopenDelayTicks = -1;
    }

    public void tick() {
        if (reopenDelayTicks >= 0) {
            if (reopenDelayTicks == 0 && lastTrapdoorUsed != null) {
                ensureTrapdoorOpen(lastTrapdoorUsed);
                reopenDelayTicks = -1;
                lastTrapdoorUsed = null;
            } else {
                reopenDelayTicks--;
            }
        }
    }

    // ---- Events
    @EventHandler
    public void onAltChat(ReceiveMessageEvent event) {
        Text txt = event.getMessage();
        String msg = txt.getString().toLowerCase(Locale.ROOT);
        String main = settings.mainName.get().trim();
        if (main.isEmpty()) return;

        if (!msg.contains(main.toLowerCase(Locale.ROOT))) return;
        if (!msg.contains("totem remaining")) return;

        int count = -1;
        for (String w : msg.split(" ")) {
            try {
                count = Integer.parseInt(w);
                break;
            } catch (NumberFormatException ignored) {}
        }
        if (count == -1 || count > 64) return;

        if (count <= settings.altTriggerThreshold.get()) {
            BlockPos trapdoor = useBestStasisWithPearl();
            if (trapdoor != null) {
                lastTrapdoorUsed = trapdoor;
                reopenDelayTicks = settings.altReopenDelay.get();
                if (settings.altSendConfirm.get()) {
                    String mainUser = settings.mainName.get().trim();
                    if (!mainUser.isEmpty()) {
                        ChatUtils.sendPlayerMsg("/msg " + mainUser + " tp-ok");
                    }
                }
            } else {
                infoLogger.run();
            }
        }
    }

    // ---- Helpers
    private BlockPos useBestStasisWithPearl() {
        if (mc == null || mc.player == null || mc.world == null) return null;

        BlockPos bestTrapdoor = stasisFinder.findBestStasisWithPearl();
        if (bestTrapdoor == null) return null;

        interactTrapdoor(bestTrapdoor);
        return bestTrapdoor;
    }

    private void interactTrapdoor(BlockPos trapdoorPos) {
        if (mc.getNetworkHandler() == null) return;
        Vec3d hit = Vec3d.ofCenter(trapdoorPos);
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
            new BlockHitResult(hit, Direction.UP, trapdoorPos, false), 0));
    }

    private void ensureTrapdoorOpen(BlockPos trapdoorPos) {
        if (mc == null || mc.world == null || trapdoorPos == null) return;
        BlockState s = mc.world.getBlockState(trapdoorPos);
        if (!(s.getBlock() instanceof TrapdoorBlock)) return;
        Boolean open = s.get(TrapdoorBlock.OPEN);
        if (open != null && !open) interactTrapdoor(trapdoorPos);
    }
}
