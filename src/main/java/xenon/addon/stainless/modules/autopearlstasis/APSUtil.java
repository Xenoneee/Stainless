package xenon.addon.stainless.modules.autopearlstasis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Utility methods for AutoPearlStasis
 */
public class APSUtil {

    // ---- State
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // ---- Helpers
    public static int countTotems() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING))
                count += mc.player.getInventory().getStack(i).getCount();
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING))
            count += mc.player.getOffHandStack().getCount();
        return count;
    }

    public static int parseKey(String key) {
        try {
            return GLFW.class.getField("GLFW_KEY_" + key.toUpperCase(Locale.ROOT)).getInt(null);
        } catch (Throwable ignored) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }
    }

    public static String normalizeName(String s) {
        return (s == null) ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
