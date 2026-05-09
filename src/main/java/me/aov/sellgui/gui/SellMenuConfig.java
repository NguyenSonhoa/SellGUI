package me.aov.sellgui.gui;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.utils.ItemIdentifier;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SellMenuConfig {
    public static final String DEFAULT_MENU_ID = "default";

    private final SellGUIMain plugin;
    private final String id;
    private final ConfigurationSection section;
    private final Set<String> allowedItems;
    private final Set<String> deniedItems;
    private final boolean exclusiveItems;

    private SellMenuConfig(SellGUIMain plugin, String id, ConfigurationSection section) {
        this.plugin = plugin;
        this.id = normalizeMenuId(id);
        this.section = section;
        this.allowedItems = loadItemSet(section, "item-filter.allowed-items", "allowed-items");
        this.deniedItems = loadItemSet(section, "item-filter.denied-items", "denied-items", "item-filter.blocked-items", "blocked-items");
        this.exclusiveItems = section.getBoolean("item-filter.exclusive", section.getBoolean("exclusive-items", false));
    }

    public static SellMenuConfig load(SellGUIMain plugin, String requestedId) {
        FileConfiguration guiConfig = plugin.getConfigManager().getGUIConfig();
        if (guiConfig == null) {
            return null;
        }

        String id = normalizeMenuId(requestedId);
        ConfigurationSection section = guiConfig.getConfigurationSection("sell_menus." + id);
        if (section == null && DEFAULT_MENU_ID.equals(id)) {
            section = guiConfig.getConfigurationSection("sell_gui");
        }

        return section == null ? null : new SellMenuConfig(plugin, id, section);
    }

    public static List<String> getMenuIds(SellGUIMain plugin) {
        FileConfiguration guiConfig = plugin.getConfigManager().getGUIConfig();
        if (guiConfig == null) {
            return Collections.singletonList(DEFAULT_MENU_ID);
        }

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ConfigurationSection menus = guiConfig.getConfigurationSection("sell_menus");
        if (menus != null) {
            for (String key : menus.getKeys(false)) {
                if (menus.isConfigurationSection(key)) {
                    ids.add(normalizeMenuId(key));
                }
            }
        }

        if (ids.isEmpty() && guiConfig.isConfigurationSection("sell_gui")) {
            ids.add(DEFAULT_MENU_ID);
        }

        if (ids.isEmpty()) {
            ids.add(DEFAULT_MENU_ID);
        }

        return new ArrayList<>(ids);
    }

    public static boolean menuExists(SellGUIMain plugin, String requestedId) {
        return load(plugin, requestedId) != null;
    }

    public static String normalizeMenuId(String rawId) {
        if (rawId == null || rawId.trim().isEmpty()) {
            return DEFAULT_MENU_ID;
        }
        return rawId.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeItemKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        String key = raw.trim().toUpperCase(Locale.ROOT);
        if (!key.contains(":")) {
            key = "VANILLA:" + key;
        }
        return key;
    }

    public static boolean isExclusiveToAnyMenu(SellGUIMain plugin, ItemStack item) {
        for (String menuId : getMenuIds(plugin)) {
            SellMenuConfig menuConfig = load(plugin, menuId);
            if (menuConfig != null && menuConfig.isExclusiveItems() && menuConfig.matchesAllowedItem(item)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowsItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (matchesDeniedItem(item)) {
            return false;
        }

        if (!allowedItems.isEmpty()) {
            return matchesAllowedItem(item);
        }

        return !isExclusiveToAnotherMenu(item);
    }

    private boolean isExclusiveToAnotherMenu(ItemStack item) {
        for (String menuId : getMenuIds(plugin)) {
            if (id.equals(menuId)) {
                continue;
            }

            SellMenuConfig menuConfig = load(plugin, menuId);
            if (menuConfig != null && menuConfig.isExclusiveItems() && menuConfig.matchesAllowedItem(item)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesAllowedItem(ItemStack item) {
        return matchesItemSet(item, allowedItems);
    }

    public boolean matchesDeniedItem(ItemStack item) {
        return matchesItemSet(item, deniedItems);
    }

    private boolean matchesItemSet(ItemStack item, Set<String> itemSet) {
        if (item == null || item.getType() == Material.AIR || itemSet.isEmpty()) {
            return false;
        }

        String identifier = normalizeItemKey(ItemIdentifier.getItemIdentifier(item));
        String material = normalizeItemKey(item.getType().name());
        return (identifier != null && itemSet.contains(identifier)) || itemSet.contains(material);
    }

    private static Set<String> loadItemSet(ConfigurationSection section, String... paths) {
        Set<String> items = new HashSet<>();
        for (String path : paths) {
            for (String raw : section.getStringList(path)) {
                String normalized = normalizeItemKey(raw);
                if (normalized != null) {
                    items.add(normalized);
                }
            }
        }
        return items;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return section.getString("name", id);
    }

    public String getPermission() {
        return section.getString("permission", "");
    }

    public String getString(String path, String fallback) {
        return section.getString(path, fallback);
    }

    public int getInt(String path, int fallback) {
        return section.getInt(path, fallback);
    }

    public boolean getBoolean(String path, boolean fallback) {
        return section.getBoolean(path, fallback);
    }

    public boolean contains(String path) {
        return section.contains(path);
    }

    public List<Integer> getIntegerList(String path) {
        return section.getIntegerList(path);
    }

    public List<String> getStringList(String path) {
        return section.getStringList(path);
    }

    public boolean isExclusiveItems() {
        return exclusiveItems;
    }
}
