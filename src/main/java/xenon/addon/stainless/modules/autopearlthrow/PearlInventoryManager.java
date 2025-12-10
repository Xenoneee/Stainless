package xenon.addon.stainless.modules.autopearlthrow;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class PearlInventoryManager {

    // ---- Settings
    private final PearlThrowSettings settings;

    // ---- State
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // ---- Constructor
    public PearlInventoryManager(PearlThrowSettings settings) {
        this.settings = settings;
    }

    // ---- Helpers
    public InventoryPlan preparePearl() {
        Hand handToUse = Hand.MAIN_HAND;
        FindItemResult hotbarPearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        boolean useOffhand = settings.shouldPreferOffhand() &&
            mc.player.getOffHandStack().isOf(Items.ENDER_PEARL);

        boolean didSwap = false;
        boolean didMoveFromInv = false;
        int movedFromSlot = -1;
        int movedToHotbar = -1;

        // Try to pull from inventory if needed
        if (!useOffhand && !hotbarPearl.found() && settings.shouldPullFromInventory()) {
            FindItemResult anyPearl = InvUtils.find(Items.ENDER_PEARL);
            if (anyPearl.found()) {
                int toHotbar = pickHotbarSlot();
                InvUtils.move().from(anyPearl.slot()).toHotbar(toHotbar);
                didMoveFromInv = true;
                movedFromSlot = anyPearl.slot();
                movedToHotbar = toHotbar;
                hotbarPearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
            }
        }

        // Check if we have pearls now
        if (!useOffhand && !hotbarPearl.found()) {
            return new InventoryPlan(false, null, false, false, -1, -1);
        }

        // Swap to pearl if not using offhand
        if (!useOffhand) {
            if (settings.isSilentSwap()) {
                InvUtils.swap(hotbarPearl.slot(), true);
                didSwap = true;
            } else {
                InvUtils.swap(hotbarPearl.slot(), false);
            }
            handToUse = Hand.MAIN_HAND;
        } else {
            handToUse = Hand.OFF_HAND;
        }

        return new InventoryPlan(true, handToUse, didSwap, didMoveFromInv,
            movedFromSlot, movedToHotbar);
    }

    private int pickHotbarSlot() {
        if (settings.shouldPreferEmptyHotbar()) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isEmpty()) {
                    return i;
                }
            }
        }
        return MathHelper.clamp(settings.getTempHotbarSlot(), 0, 8);
    }

    // ---- Enums
    public static class InventoryPlan {
        private final boolean valid;
        private final Hand hand;
        private final boolean didSwap;
        private final boolean didMoveFromInv;
        private final int movedFromSlot;
        private final int movedToHotbar;

        public InventoryPlan(boolean valid, Hand hand, boolean didSwap,
                             boolean didMoveFromInv, int movedFromSlot, int movedToHotbar) {
            this.valid = valid;
            this.hand = hand;
            this.didSwap = didSwap;
            this.didMoveFromInv = didMoveFromInv;
            this.movedFromSlot = movedFromSlot;
            this.movedToHotbar = movedToHotbar;
        }

        public boolean isValid() {
            return valid;
        }

        public Hand getHand() {
            return hand;
        }

        public void cleanup() {
            if (didSwap) {
                InvUtils.swapBack();
            }
            if (didMoveFromInv && movedToHotbar >= 0 && movedFromSlot >= 0) {
                InvUtils.move().fromHotbar(movedToHotbar).to(movedFromSlot);
            }
        }
    }
}
