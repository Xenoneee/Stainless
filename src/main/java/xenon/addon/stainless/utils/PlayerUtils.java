package xenon.addon.stainless.utils;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Utility methods for working with PlayerEntity objects.
 */
public class PlayerUtils {
    private PlayerUtils() {} // Prevent instantiation

    /**
     * Checks if the player is "naked" (no armor equipped).
     * @param player The player to check
     * @return true if the player has no armor in any armor slot
     */
    public static boolean isNaked(PlayerEntity player) {
        if (player == null) return true;

        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        }) {
            ItemStack armor = player.getEquippedStack(slot);
            if (armor != null && !armor.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
