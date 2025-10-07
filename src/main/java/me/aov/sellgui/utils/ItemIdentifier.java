package me.aov.sellgui.utils;

import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemIdentifier {

    private static boolean hasMMOItemsPlugin;
    private static boolean hasNexoPlugin;

    static {
        try {

            Class.forName("io.lumine.mythic.lib.api.item.NBTItem");

            hasMMOItemsPlugin = Bukkit.getPluginManager().getPlugin("MMOItems") != null;
        } catch (ClassNotFoundException e) {
            hasMMOItemsPlugin = false;
        }
        hasNexoPlugin = Bukkit.getPluginManager().getPlugin("Nexo") != null;
    }

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
            if (hasMMOItemsPlugin) {
                NBTItem nbtItem = NBTItem.get(itemStack);
                if (nbtItem.hasTag("MMOITEMS_ITEM_ID")) {
                    return ItemType.MMOITEMS;
                }
            }

            if (hasNexoPlugin && isNexoItem(itemStack)) {
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
            if (type == ItemType.MMOITEMS && hasMMOItemsPlugin) {
                NBTItem nbtItem = NBTItem.get(itemStack);
                String mmoItemType = nbtItem.getType();
                String mmoItemId = nbtItem.getString("MMOITEMS_ITEM_ID");
                if (mmoItemType != null && mmoItemId != null) {
                    return "MMOITEMS:" + mmoItemType.toUpperCase() + "." + mmoItemId.toUpperCase();
                }
            } else if (type == ItemType.NEXO && hasNexoPlugin) {
                String nexoId = getNexoItemId(itemStack);
                if (nexoId != null && !nexoId.isEmpty()) {
                    System.out.println("[SellGUI Debug] Nexo ID found: " + nexoId);
                    return "NEXO:" + nexoId;
                }
            } else if (type == ItemType.VANILLA) {
                return "VANILLA:" + itemStack.getType().name();
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

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            String materialName = itemStack.getType().name().toLowerCase().replace('_', ' ');
            return capitalizeWords(materialName);
        }

        if (meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        try {
            if (meta.hasItemName()) {
                return meta.getItemName();
            }
        } catch (NoSuchMethodError e) {

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

        if (hasMMOItemsPlugin) {
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
                System.out.println("[SellGUI Debug] Error reading NBT (MMOItems present): " + e.getMessage());
            }
        } else {
            System.out.println("[SellGUI Debug] MMOItems not present, skipping NBTItem debug.");
        }

        System.out.println("[SellGUI Debug] === Nexo API Test ===");
        if (hasNexoPlugin) {
            try {
                boolean isNexo = isNexoItem(itemStack);
                String nexoId = getNexoItemId(itemStack);
                System.out.println("[SellGUI Debug] Is Nexo Item: " + isNexo);
                System.out.println("[SellGUI Debug] Nexo ID: " + nexoId);
            } catch (Exception e) {
                System.out.println("[SellGUI Debug] Nexo API error: " + e.getMessage());
            }
        } else {
            System.out.println("[SellGUI Debug] Nexo not present, skipping Nexo API test.");
        }
        System.out.println("[SellGUI Debug] === End Nexo API Test ===");

        System.out.println("[SellGUI Debug] === End Item Debug === ");
    }

    private static boolean isNexoItem(ItemStack itemStack) {
        if (!hasNexoPlugin) {
            return false;
        }
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
        if (!hasNexoPlugin) {
            return null;
        }
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