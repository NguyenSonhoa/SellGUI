package me.aov.sellgui.utils;

import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Bukkit;

public class ItemIdentifier {

    public enum ItemType {
        VANILLA,
        MMOITEMS,
        NEXO,
        UNKNOWN
    }

    public static ItemType getItemType(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return ItemType.UNKNOWN;
        }

        try {
            NBTItem nbtItem = NBTItem.get(itemStack);

            if (nbtItem.hasTag("MMOITEMS_ITEM_ID")) {
                return ItemType.MMOITEMS;
            }

            if (isNexoItem(itemStack)) {
                return ItemType.NEXO;
            }

            if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {

            }

        } catch (Exception e) {
        }

        return ItemType.VANILLA;
    }

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

                    String nexoId = getNexoItemId(itemStack);
                    if (nexoId != null && !nexoId.isEmpty()) {
                        System.out.println("[SellGUI Debug] Nexo ID found: " + nexoId);
                        return "NEXO:" + nexoId;
                    }
                    break;

                case VANILLA:
                    return "VANILLA:" + itemStack.getType().name();

                default:
                    return null;
            }
        } catch (Exception e) {

            if (type == ItemType.VANILLA) {
                return "VANILLA:" + itemStack.getType().name();
            }
            return null;
        }

        return null;
    }

    public static String getItemDisplayName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return "Unknown Item";
        }

        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            return itemStack.getItemMeta().getDisplayName();
        }

        String materialName = itemStack.getType().name().toLowerCase().replace('_', ' ');
        return capitalizeWords(materialName);
    }

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

    public static void debugItemNBT(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            System.out.println("[SellGUI Debug] Item is null or AIR");
            return;
        }

        System.out.println("[SellGUI Debug] === Item Debug for " + itemStack.getType().name() + " ===");

        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            System.out.println("[SellGUI Debug] Display Name: " + itemStack.getItemMeta().getDisplayName());
        }

        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasCustomModelData()) {
            System.out.println("[SellGUI Debug] Custom Model Data: " + itemStack.getItemMeta().getCustomModelData());
        }

        try {
            NBTItem nbtItem = NBTItem.get(itemStack);

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

    private static boolean isNexoItem(ItemStack itemStack) {
        try {

            if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
                return false;
            }

            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            java.lang.reflect.Method idFromItemMethod = nexoItemsClass.getMethod("idFromItem", ItemStack.class);
            String itemId = (String) idFromItemMethod.invoke(null, itemStack);

            return itemId != null && !itemId.isEmpty();

        } catch (Exception e) {

            System.out.println("[SellGUI Debug] Nexo API check failed: " + e.getMessage());
            return false;
        }
    }

    private static String getNexoItemId(ItemStack itemStack) {
        try {

            if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
                return null;
            }

            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            java.lang.reflect.Method idFromItemMethod = nexoItemsClass.getMethod("idFromItem", ItemStack.class);
            String itemId = (String) idFromItemMethod.invoke(null, itemStack);

            return itemId;

        } catch (Exception e) {

            System.out.println("[SellGUI Debug] Nexo API getNexoItemId failed: " + e.getMessage());
            return null;
        }
    }
}