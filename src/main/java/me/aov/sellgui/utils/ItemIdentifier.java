package me.aov.sellgui.utils;

import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ItemIdentifier {

    private static boolean hasMMOItemsPlugin;
    private static boolean hasNexoPlugin;
    private static NamespacedKey NEXO_ID_KEY;

    static {
        hasMMOItemsPlugin = Bukkit.getPluginManager().getPlugin("MMOItems") != null;
        hasNexoPlugin = Bukkit.getPluginManager().getPlugin("Nexo") != null;

        // Lấy key "nexo:id" từ NexoItems.ITEM_ID (cách chính xác nhất)
        if (hasNexoPlugin) {
            try {
                Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
                Method getItemIdMethod = nexoItemsClass.getMethod("getITEM_ID");
                NEXO_ID_KEY = (NamespacedKey) getItemIdMethod.invoke(null);
            } catch (Exception e) {
                // Fallback nếu không lấy được
                NEXO_ID_KEY = new NamespacedKey("nexo", "id");
            }
        }
    }

    public enum ItemType {
        VANILLA, MMOITEMS, NEXO, UNKNOWN
    }

    // =====================================================================
    // LẤY LOẠI ITEM
    // =====================================================================
    public static ItemType getItemType(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return ItemType.UNKNOWN;

        if (hasMMOItemsPlugin) {
            try {
                NBTItem nbt = NBTItem.get(item);
                if (nbt.hasTag("MMOITEMS_ITEM_ID")) return ItemType.MMOITEMS;
            } catch (Exception ignored) {}
        }

        if (hasNexoPlugin && isNexoItem(item)) return ItemType.NEXO;

        return ItemType.VANILLA;
    }

    // =====================================================================
    // LẤY ID ĐỂ LƯU CONFIG
    // =====================================================================
    public static String getItemIdentifier(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        ItemType type = getItemType(item);

        try {
            if (type == ItemType.MMOITEMS && hasMMOItemsPlugin) {
                NBTItem nbt = NBTItem.get(item);
                String t = nbt.getString("MMOITEMS_ITEM_TYPE");
                String i = nbt.getString("MMOITEMS_ITEM_ID");
                if (t != null && i != null && !t.isEmpty() && !i.isEmpty()) {
                    return "MMOITEMS:" + t.toUpperCase() + "." + i.toUpperCase();
                }
            } else if (type == ItemType.NEXO && hasNexoPlugin) {
                String id = getNexoItemId(item);
                if (id != null && !id.isEmpty()) {
                    return "NEXO:" + id.toUpperCase();
                }
            } else if (type == ItemType.VANILLA) {
                return "VANILLA:" + item.getType().name();
            }
        } catch (Exception ignored) {}

        return type == ItemType.VANILLA ? "VANILLA:" + item.getType().name() : null;
    }

    // =====================================================================
    // LẤY TÊN HIỂN THỊ THẬT (có màu, ký tự đặc biệt)
    // =====================================================================
    public static String getItemDisplayName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "Unknown Item";

        // MMOItems
        if (hasMMOItemsPlugin) {
            try {
                NBTItem nbt = NBTItem.get(item);
                if (nbt.hasTag("display.Name")) {
                    String json = nbt.getString("display.Name");
                    // Chuyển JSON → legacy color (&a, &l,...)
                    return ChatColor.translateAlternateColorCodes('&',
                            net.md_5.bungee.api.ChatColor.of("#").toString() + // dummy để parse
                                    net.md_5.bungee.chat.ComponentSerializer.toString(
                                            net.md_5.bungee.chat.ComponentSerializer.parse(json)[0]));
                }
                if (nbt.hasTag("MMOITEMS_NAME")) {
                    return ChatColor.translateAlternateColorCodes('&', nbt.getString("MMOITEMS_NAME"));
                }
            } catch (Exception ignored) {}
        }

        // Nexo
        if (hasNexoPlugin) {
            try {
                Class<?> clazz = Class.forName("com.nexomc.nexo.api.NexoItems");
                Method m = clazz.getMethod("getDisplayName", ItemStack.class);
                String name = (String) m.invoke(null, item);
                if (name != null && !name.isEmpty()) return name;
            } catch (Exception ignored) {}
        }

        // Vanilla
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) return meta.getDisplayName();
            try {
                if (meta.hasItemName()) return meta.getItemName();
            } catch (Throwable ignored) {}
        }

        String name = item.getType().name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    // =====================================================================
    // TẠO ITEM TỪ ID (WITHDRAW)
    // =====================================================================
    public static ItemStack getItemStackFromIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) return null;

        ItemType type = getItemTypeFromString(identifier);
        String[] parts = identifier.split(":", 2);
        if (parts.length < 2) return null;
        String id = parts[1].toUpperCase();

        return switch (type) {
            case VANILLA -> {
                try {
                    yield new ItemStack(Material.valueOf(id));
                } catch (Exception e) {
                    yield null;
                }
            }
            case MMOITEMS -> {
                if (!hasMMOItemsPlugin) yield placeholder("MMOItems Not Loaded", id);
                try {
                    Class<?> clazz = Class.forName("net.Indyuce.mmoitems.MMOItems");
                    Object plugin = clazz.getDeclaredField("plugin").get(null);
                    String[] mmo = id.split("\\.", 2);
                    if (mmo.length != 2) yield placeholder("Invalid MMOItems ID", id);
                    Object result = clazz.getMethod("getItem", String.class, String.class)
                            .invoke(plugin, mmo[0], mmo[1]);
                    yield result instanceof ItemStack stack ? stack : placeholder("MMOItem Not Found", id);
                } catch (Exception e) {
                    e.printStackTrace();
                    yield placeholder("MMOItems Error", id);
                }
            }
            case NEXO -> {
                if (!hasNexoPlugin) yield placeholder("Nexo Not Loaded", id);
                try {
                    // SỬA LỖI: Nexo dùng itemFromId(String) static method → trả về ItemBuilder
                    // Sau đó gọi build() để lấy ItemStack
                    Class<?> clazz = Class.forName("com.nexomc.nexo.api.NexoItems");
                    Method itemFromIdMethod = clazz.getMethod("itemFromId", String.class);
                    Object builder = itemFromIdMethod.invoke(null, id.toLowerCase()); // ID thường lowercase trong Nexo

                    if (builder != null) {
                        // Gọi build() trên ItemBuilder để lấy ItemStack
                        Method buildMethod = builder.getClass().getMethod("build");
                        yield (ItemStack) buildMethod.invoke(builder);
                    } else {
                        yield placeholder("Nexo Item Not Found", id);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    yield placeholder("Nexo Error", id);
                }
            }
            default -> null;
        };
    }

    // =====================================================================
    // NEXO MỚI: DÙNG PDC KEY "nexo:id"
    // =====================================================================
    private static boolean isNexoItem(ItemStack item) {
        if (!hasNexoPlugin || item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(NEXO_ID_KEY, PersistentDataType.STRING);
    }

    private static String getNexoItemId(ItemStack item) {
        if (!hasNexoPlugin || item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(NEXO_ID_KEY, PersistentDataType.STRING);
    }

    // =====================================================================
    // UTILS
    // =====================================================================
    public static ItemType getItemTypeFromString(String id) {
        if (id == null || !id.contains(":")) return ItemType.UNKNOWN;
        try {
            return ItemType.valueOf(id.split(":", 2)[0].toUpperCase());
        } catch (Exception e) {
            return ItemType.UNKNOWN;
        }
    }

    private static ItemStack placeholder(String title, String subtitle) {
        ItemStack i = new ItemStack(Material.PAPER);
        ItemMeta m = i.getItemMeta();
        if (m != null) {
            m.setDisplayName("§c" + title);
            m.setLore(List.of("§7" + subtitle));
            i.setItemMeta(m);
        }
        return i;
    }

    // =====================================================================
    // DEBUG TOOL (giữ lại để PriceSetterGUI gọi được)
    // =====================================================================
    public static void debugItemNBT(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            System.out.println("[SellGUI Debug] Item is null or AIR");
            return;
        }

        System.out.println("§6=== SellGUI Item Debug ===");
        System.out.println("Material: " + item.getType());
        System.out.println("Identifier: " + getItemIdentifier(item));
        System.out.println("Display Name: " + getItemDisplayName(item));
        System.out.println("Type: " + getItemType(item));

        if (hasMMOItemsPlugin) {
            try {
                NBTItem nbt = NBTItem.get(item);
                System.out.println("MMOITEMS_ITEM_TYPE: " + nbt.getString("MMOITEMS_ITEM_TYPE"));
                System.out.println("MMOITEMS_ITEM_ID: " + nbt.getString("MMOITEMS_ITEM_ID"));
            } catch (Exception ignored) {}
        }

        if (hasNexoPlugin && isNexoItem(item)) {
            System.out.println("Nexo ID: " + getNexoItemId(item));
        }

        System.out.println("§6=== End Debug ===\n");
    }
}