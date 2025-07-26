package me.aov.sellgui.utils;

import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/**
 * Utility class for identifying different types of items (Vanilla, MMOItems, Nexo)
 *
 * NBT Tags used for detection:
 * - MMOItems: "MMOITEMS_ITEM_ID"
 * - Nexo: "nexo:id"
 */
public class ItemIdentifier {
    
    public enum ItemType {
        VANILLA,
        MMOITEMS,
        NEXO,
        UNKNOWN
    }
    
    /**
     * Identifies the type of an ItemStack
     * @param itemStack The item to identify
     * @return The type of the item
     */
    public static ItemType getItemType(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return ItemType.UNKNOWN;
        }

        try {
            NBTItem nbtItem = NBTItem.get(itemStack);

            // Check for MMOItems
            if (nbtItem.hasTag("MMOITEMS_ITEM_ID")) {
                return ItemType.MMOITEMS;
            }

            // Check for Nexo items - correct NBT tag is "nexo:id"
            if (nbtItem.hasTag("nexo:id")) {
                return ItemType.NEXO;
            }
        } catch (Exception e) {
            // If NBT reading fails, fall back to vanilla
        }

        // Default to vanilla
        return ItemType.VANILLA;
    }
    
    /**
     * Gets a unique identifier for an item based on its type
     * @param itemStack The item to get identifier for
     * @return Unique identifier string
     */
    public static String getItemIdentifier(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }

        ItemType type = getItemType(itemStack);

        try {
            NBTItem nbtItem = NBTItem.get(itemStack);

            switch (type) {
                case MMOITEMS:
                    String mmoItemType = nbtItem.getType();
                    String mmoItemId = nbtItem.getString("MMOITEMS_ITEM_ID");
                    if (mmoItemType != null && mmoItemId != null) {
                        return "MMOITEMS:" + mmoItemType.toUpperCase() + "." + mmoItemId.toUpperCase();
                    }
                    break;

                case NEXO:
                    // Nexo items use "nexo:id" NBT tag
                    String nexoId = nbtItem.getString("nexo:id");
                    if (nexoId != null && !nexoId.isEmpty()) {
                        return "NEXO:" + nexoId;
                    }
                    break;

                case VANILLA:
                    return "VANILLA:" + itemStack.getType().name();

                default:
                    return null;
            }
        } catch (Exception e) {
            // If NBT reading fails, fall back to vanilla for vanilla items
            if (type == ItemType.VANILLA) {
                return "VANILLA:" + itemStack.getType().name();
            }
            return null;
        }

        return null;
    }
    
    /**
     * Gets the display name for an item
     * @param itemStack The item to get display name for
     * @return Display name of the item
     */
    public static String getItemDisplayName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return "Unknown Item";
        }
        
        // If item has custom display name, use it
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            return itemStack.getItemMeta().getDisplayName();
        }
        
        // Otherwise use material name formatted
        String materialName = itemStack.getType().name().toLowerCase().replace('_', ' ');
        return capitalizeWords(materialName);
    }
    
    /**
     * Capitalizes the first letter of each word
     * @param str The string to capitalize
     * @return Capitalized string
     */
    private static String capitalizeWords(String str) {
        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            if (words[i].length() > 0) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
            }
        }
        
        return result.toString();
    }
}
