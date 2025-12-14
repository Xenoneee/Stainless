package xenon.addon.stainless.modules.autopearlthrow;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class PearlInventoryManager {

    // ---- Constants
    private static final int HOTBAR_SIZE = 9;
    private static final int MIN_HOTBAR_SLOT = 0;
    private static final int MAX_HOTBAR_SLOT = 8;
    private static final int INVALID_SLOT = -1;

    // ---- Settings
    private final PearlThrowSettings settings;

    // ---- State
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // ---- Constructor
    public PearlInventoryManager(PearlThrowSettings settings) {
        this.settings = settings;
    }

    // ---- Public API
    public InventoryPlan preparePearl() {
        // Always use main hand (offhand conflicts with autototem)
        FindItemResult hotbarPearl = InvUtils.findInHotbar(Items.ENDER_PEARL);

        // Try to pull from inventory if needed
        if (!hotbarPearl.found() && settings.shouldPullFromInventory()) {
            InventoryMove move = pullPearlFromInventory();
            if (move != null) {
                hotbarPearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
                return createPlanWithMove(hotbarPearl, move);
            }
        }

        // Check if we have a pearl in hotbar
        if (!hotbarPearl.found()) {
            return InventoryPlan.invalid();
        }

        // Swap to pearl slot
        return createPlanWithSwap(hotbarPearl);
    }

    public ThrowPlan prepareThrow() {
        FindItemResult hotbarPearl = InvUtils.findInHotbar(Items.ENDER_PEARL);

        if (!hotbarPearl.found()) {
            return ThrowPlan.invalid();
        }

        boolean didSwap = false;
        if (settings.isSilentSwap()) {
            InvUtils.swap(hotbarPearl.slot(), true);
            didSwap = true;
        }

        return new ThrowPlan(true, hotbarPearl.slot(), didSwap);
    }

    // ---- Inventory Management
    private InventoryMove pullPearlFromInventory() {
        FindItemResult inventoryPearl = InvUtils.find(Items.ENDER_PEARL);
        if (!inventoryPearl.found()) {
            return null;
        }

        int targetHotbarSlot = selectHotbarSlot();
        InvUtils.move().from(inventoryPearl.slot()).toHotbar(targetHotbarSlot);

        return new InventoryMove(inventoryPearl.slot(), targetHotbarSlot);
    }

    private int selectHotbarSlot() {
        if (settings.shouldPreferEmptyHotbar()) {
            int emptySlot = findEmptyHotbarSlot();
            if (emptySlot != INVALID_SLOT) {
                return emptySlot;
            }
        }

        return MathHelper.clamp(settings.getTempHotbarSlot(), MIN_HOTBAR_SLOT, MAX_HOTBAR_SLOT);
    }

    private int findEmptyHotbarSlot() {
        if (mc.player == null) {
            return INVALID_SLOT;
        }

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }

        return INVALID_SLOT;
    }

    // ---- Plan Creation
    private InventoryPlan createPlanWithSwap(FindItemResult pearlResult) {
        boolean didSwap = performSwap(pearlResult.slot());
        return new InventoryPlan(true, Hand.MAIN_HAND, didSwap, null);
    }

    private InventoryPlan createPlanWithMove(FindItemResult pearlResult, InventoryMove move) {
        boolean didSwap = performSwap(pearlResult.slot());
        return new InventoryPlan(true, Hand.MAIN_HAND, didSwap, move);
    }

    private boolean performSwap(int slot) {
        if (settings.isSilentSwap()) {
            InvUtils.swap(slot, true);
            return true;
        } else {
            InvUtils.swap(slot, false);
            return false;
        }
    }

    // ---- Helper Classes
    private static class InventoryMove {
        final int fromSlot;
        final int toHotbarSlot;

        InventoryMove(int fromSlot, int toHotbarSlot) {
            this.fromSlot = fromSlot;
            this.toHotbarSlot = toHotbarSlot;
        }
    }

    // ---- Return Types
    public static class InventoryPlan {
        private final boolean valid;
        private final Hand hand;
        private final boolean didSwap;
        private final InventoryMove inventoryMove;

        private InventoryPlan(boolean valid, Hand hand, boolean didSwap, InventoryMove inventoryMove) {
            this.valid = valid;
            this.hand = hand;
            this.didSwap = didSwap;
            this.inventoryMove = inventoryMove;
        }

        public static InventoryPlan invalid() {
            return new InventoryPlan(false, null, false, null);
        }

        public boolean isValid() {
            return valid;
        }

        public Hand getHand() {
            return hand;
        }

        public void cleanup() {
            cleanupSwap();
            cleanupInventoryMove();
        }

        private void cleanupSwap() {
            if (didSwap) {
                InvUtils.swapBack();
            }
        }

        private void cleanupInventoryMove() {
            if (inventoryMove != null) {
                InvUtils.move()
                    .fromHotbar(inventoryMove.toHotbarSlot)
                    .to(inventoryMove.fromSlot);
            }
        }
    }

    public static class ThrowPlan {
        private final boolean valid;
        private final int pearlSlot;
        private final boolean didSwap;

        private ThrowPlan(boolean valid, int pearlSlot, boolean didSwap) {
            this.valid = valid;
            this.pearlSlot = pearlSlot;
            this.didSwap = didSwap;
        }

        public static ThrowPlan invalid() {
            return new ThrowPlan(false, INVALID_SLOT, false);
        }

        public boolean isValid() {
            return valid;
        }

        public int getPearlSlot() {
            return pearlSlot;
        }

        public void cleanup() {
            if (didSwap) {
                InvUtils.swapBack();
            }
        }
    }
}
