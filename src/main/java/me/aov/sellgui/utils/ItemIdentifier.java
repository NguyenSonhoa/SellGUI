package me.aov.sellgui.utils;

import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Bukkit;

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

            // Check for Nexo items using Nexo API
            if (isNexoItem(itemStack)) {
                return ItemType.NEXO;
            }

            // Debug: Print all NBT tags for unknown items
            if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                System.out.println("[SellGUI Debug] Item '" + itemStack.getItemMeta().getDisplayName() + "' NBT tags:");
                // This is a simple debug - in production you might want to use reflection to get all tags
            }

        } catch (Exception e) {
            // If NBT reading fails, fall back to vanilla
            System.out.println("[SellGUI Debug] NBT reading failed: " + e.getMessage());
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
                    // Use Nexo API to get item ID
                    String nexoId = getNexoItemId(itemStack);
                    if (nexoId != null && !nexoId.isEmpty()) {
                        System.out.println("[SellGUI Debug] Nexo ID found: " + nexoId);
                        return "NEXO:" + nexoId; // Keep original case for Nexo IDs
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

    /**
     * Debug method to print item information
     * @param itemStack The item to debug
     */
    public static void debugItemNBT(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            System.out.println("[SellGUI Debug] Item is null or AIR");
            return;
        }

        System.out.println("[SellGUI Debug] === Item Debug for " + itemStack.getType().name() + " ===");

        // Basic item info
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            System.out.println("[SellGUI Debug] Display Name: " + itemStack.getItemMeta().getDisplayName());
        }

        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasCustomModelData()) {
            System.out.println("[SellGUI Debug] Custom Model Data: " + itemStack.getItemMeta().getCustomModelData());
        }

        // Test NBT tags safely
        try {
            NBTItem nbtItem = NBTItem.get(itemStack);

            // Check common NBT tags
            String[] commonTags = {
                "nexo:id",
                "MMOITEMS_ITEM_ID", "MMOITEMS_ITEM_TYPE"
            };

            for (String tag : commonTags) {
                if (nbtItem.hasTag(tag)) {
                    try {
                        String value = nbtItem.getString(tag);
                        if (value != null && !value.isEmpty()) {
                            System.out.println("[SellGUI Debug] NBT " + tag + " = " + value);
                        } else {
                            System.out.println("[SellGUI Debug] NBT " + tag + " = (exists but empty)");
                        }
                    } catch (Exception e) {
                        System.out.println("[SellGUI Debug] NBT " + tag + " = (error reading: " + e.getMessage() + ")");
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("[SellGUI Debug] Error reading NBT: " + e.getMessage());
        }

        // Test Nexo API
        System.out.println("[SellGUI Debug] === Nexo API Test ===");
        try {
            boolean isNexo = isNexoItem(itemStack);
            String nexoId = getNexoItemId(itemStack);
            System.out.println("[SellGUI Debug] Is Nexo Item: " + isNexo);
            System.out.println("[SellGUI Debug] Nexo ID: " + nexoId);
        } catch (Exception e) {
            System.out.println("[SellGUI Debug] Nexo API error: " + e.getMessage());
        }
        System.out.println("[SellGUI Debug] === End Nexo API Test ===");

        System.out.println("[SellGUI Debug] === End Item Debug ===");
    }

    /**
     * Check if an item is a Nexo item using Nexo API
     * @param itemStack The item to check
     * @return true if it's a Nexo item
     */
    private static boolean isNexoItem(ItemStack itemStack) {
        try {
            // Check if Nexo plugin is loaded
            if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
                return false;
            }

            // Use reflection to call NexoItems.idFromItem(itemStack)
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            java.lang.reflect.Method idFromItemMethod = nexoItemsClass.getMethod("idFromItem", ItemStack.class);
            String itemId = (String) idFromItemMethod.invoke(null, itemStack);

            return itemId != null && !itemId.isEmpty();

        } catch (Exception e) {
            // Nexo not available or method call failed
            System.out.println("[SellGUI Debug] Nexo API check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get Nexo item ID using Nexo API
     * @param itemStack The item to get ID for
     * @return Nexo item ID or null
     */
    private static String getNexoItemId(ItemStack itemStack) {
        try {
            // Check if Nexo plugin is loaded
            if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
                return null;
            }

            // Use reflection to call NexoItems.idFromItem(itemStack)
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            java.lang.reflect.Method idFromItemMethod = nexoItemsClass.getMethod("idFromItem", ItemStack.class);
            String itemId = (String) idFromItemMethod.invoke(null, itemStack);

            return itemId;

        } catch (Exception e) {
            // Nexo not available or method call failed
            System.out.println("[SellGUI Debug] Nexo API getNexoItemId failed: " + e.getMessage());
            return null;
        }
    }
}
