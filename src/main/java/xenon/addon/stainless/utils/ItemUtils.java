package xenon.addon.stainless.utils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;

/**
 * Utility methods for working with ItemStacks.
 */
public class ItemUtils {
    private ItemUtils() {} // Prevent instantiation

    /**
     * Checks if the given ItemStack is food.
     * @param stack The ItemStack to check
     * @return true if the stack is not null, not empty, and has food component
     */
    public static boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.get(DataComponentTypes.FOOD) != null;
    }

    /**
     * Checks if the given ItemStack is null or empty.
     * @param stack The ItemStack to check
     * @return true if the stack is null or empty
     */
    public static boolean isNullOrEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }
}
