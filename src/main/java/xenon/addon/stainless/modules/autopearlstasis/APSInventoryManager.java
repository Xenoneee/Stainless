package xenon.addon.stainless.modules.autopearlstasis;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Method;

/**
 * Manages pearl inventory operations: finding, swapping, cleanup
 */
public class APSInventoryManager {

    // ---- Settings
    private final APSSettings settings;

    // ---- State
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // State tracking
    private boolean usingOffhand = false;
    private boolean didSilentSwap = false;
    private boolean didMoveFromInv = false;
    private int movedFromSlot = -1;
    private int movedToHotbar = -1;

    // ---- Constructor
    public APSInventoryManager(APSSettings settings) {
        this.settings = settings;
    }

    // ---- Lifecycle
    public boolean ensurePearlReady() {
        if (mc.player == null || mc.interactionManager == null) return false;

        cleanupInventoryState();
        usingOffhand = false;

        // Check offhand first
        if (settings.preferOffhand.get() && mc.player.getOffHandStack().getItem() == Items.ENDER_PEARL) {
            usingOffhand = true;
            return true;
        }

        // Check main hand
        if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) {
            return true;
        }

        // Check hotbar
        FindItemResult hotbarPearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (hotbarPearl.found()) {
            if (settings.silentSwap.get()) {
                InvUtils.swap(hotbarPearl.slot(), true);
                didSilentSwap = true;
            } else {
                InvUtils.swap(hotbarPearl.slot(), false);
                didSilentSwap = false;
            }
            return true;
        }

        // Pull from inventory
        if (settings.pullFromInventory.get()) {
            FindItemResult anyPearl = InvUtils.find(Items.ENDER_PEARL);
            if (anyPearl.found()) {
                int toHotbar = pickHotbarSlot();
                InvUtils.move().from(anyPearl.slot()).toHotbar(toHotbar);
                InvUtils.swap(toHotbar, settings.silentSwap.get());
                didMoveFromInv = true;
                movedFromSlot = anyPearl.slot();
                movedToHotbar = toHotbar;
                didSilentSwap = settings.silentSwap.get();
                return true;
            }
        }

        return false;
    }

    public void cleanupInventoryState() {
        if (mc.player == null || mc.interactionManager == null) return;

        if (didSilentSwap) {
            try {
                InvUtils.swapBack();
            } catch (Throwable ignored) {}
        }

        if (didMoveFromInv && movedToHotbar >= 0 && movedFromSlot >= 0) {
            ItemStack hotbarStack = safeGet(movedToHotbar);
            if (!hotbarStack.isEmpty() && hotbarStack.getItem() == Items.ENDER_PEARL) {
                ItemStack original = safeGet(movedFromSlot);
                if (canAcceptPearls(original)) {
                    InvUtils.move().fromHotbar(movedToHotbar).to(movedFromSlot);
                } else if (settings.silentSwap.get() || settings.swapBackOnStricterServers.get()) {
                    int stackable = findMainInvPearlStackSlot();
                    if (stackable >= 0) {
                        InvUtils.move().fromHotbar(movedToHotbar).to(stackable);
                    } else {
                        int empty = findFirstEmptyMainSlot();
                        if (empty >= 0) {
                            InvUtils.move().fromHotbar(movedToHotbar).to(empty);
                        }
                    }
                }
            }
        }

        usingOffhand = false;
        didSilentSwap = false;
        didMoveFromInv = false;
        movedFromSlot = -1;
        movedToHotbar = -1;
    }

    // ---- Helpers
    public Hand getPearlHand() {
        return usingOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    public boolean pearlOnCooldown() {
        try {
            Object icm = mc.player.getItemCooldownManager();
            try {
                Method isCD = icm.getClass().getMethod("isCoolingDown", net.minecraft.item.Item.class);
                Object r = isCD.invoke(icm, Items.ENDER_PEARL);
                return r instanceof Boolean && (Boolean) r;
            } catch (NoSuchMethodException ignore) {}
            try {
                Method gcp = icm.getClass().getMethod("getCooldownProgress", net.minecraft.item.Item.class, float.class);
                Object r = gcp.invoke(icm, Items.ENDER_PEARL, 0f);
                return r instanceof Float && ((Float) r) > 0f;
            } catch (NoSuchMethodException ignore) {}
            try {
                Method gcp2 = icm.getClass().getMethod("getCooldownProgress", net.minecraft.item.ItemStack.class, float.class);
                Object r = gcp2.invoke(icm, new ItemStack(Items.ENDER_PEARL), 0f);
                return r instanceof Float && ((Float) r) > 0f;
            } catch (NoSuchMethodException ignore) {}
        } catch (Throwable ignored) {}
        return false;
    }

    private ItemStack safeGet(int slot) {
        try {
            return mc.player.getInventory().getStack(slot);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private boolean canAcceptPearls(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.getItem() != Items.ENDER_PEARL) return false;
        return stack.getCount() < Math.min(stack.getMaxCount(), 16);
    }

    private int findMainInvPearlStackSlot() {
        int size = mc.player.getInventory().size();
        for (int i = 9; i < size; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.ENDER_PEARL && s.getCount() < Math.min(s.getMaxCount(), 16)) return i;
        }
        return -1;
    }

    private int findFirstEmptyMainSlot() {
        int size = mc.player.getInventory().size();
        for (int i = 9; i < size; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private int pickHotbarSlot() {
        if (mc != null && mc.player != null && settings.preferEmptyHotbar.get()) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isEmpty()) return i;
            }
        }
        return MathHelper.clamp(settings.tempHotbarSlot.get(), 0, 8);
    }
}
