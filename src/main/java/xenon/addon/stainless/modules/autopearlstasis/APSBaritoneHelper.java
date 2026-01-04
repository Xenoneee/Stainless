package xenon.addon.stainless.modules.autopearlstasis;

import net.minecraft.util.math.BlockPos;

/**
 * Helper for Baritone pathfinding integration
 */
public class APSBaritoneHelper {

    // ---- Settings
    private final APSSettings settings;

    // ---- Constructor
    public APSBaritoneHelper(APSSettings settings) {
        this.settings = settings;
    }

    // ---- Helpers
    public void startPathTo(BlockPos targetBlock) {
        if (!settings.useBaritone.get()) return;
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object provider = api.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock")
                .getConstructor(int.class, int.class, int.class)
                .newInstance(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
            Object cgp = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            cgp.getClass().getMethod("setGoalAndPath", Class.forName("baritone.api.pathing.goals.Goal"))
                .invoke(cgp, goalBlock);
        } catch (Throwable ignored) {
            // Baritone not present or failed - manual walking will handle it
        }
    }

    public void stopPath() {
        if (!settings.useBaritone.get()) return;
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object provider = api.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object cgp = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            try {
                cgp.getClass().getMethod("setGoal", Class.forName("baritone.api.pathing.goals.Goal"))
                    .invoke(cgp, new Object[]{null});
            } catch (NoSuchMethodException ignored) {}
            cgp.getClass().getMethod("cancel").invoke(cgp);
        } catch (Throwable ignored) {}
    }
}
