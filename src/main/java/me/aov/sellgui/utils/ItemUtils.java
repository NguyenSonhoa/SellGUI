package me.aov.sellgui.utils;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

public class ItemUtils {

    public static void applyConfig(ItemStack item, ConfigurationSection config) {
        if (item == null || config == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (config.contains("name")) {
            meta.setDisplayName(ColorUtils.color(config.getString("name")));
        }

        if (config.contains("lore")) {
            List<String> lore = config.getStringList("lore").stream()
                    .map(ColorUtils::color)
                    .collect(Collectors.toList());
            meta.setLore(lore);
        }

        if (config.contains("custom-model-data")) {
            meta.setCustomModelData(config.getInt("custom-model-data"));
        }

        if (config.contains("item-model")) {
            String model = config.getString("item-model");
            NamespacedKey key = parseKey(model);
            if (key != null) {
                invokeMethod(meta, "setItemModel", new Class<?>[]{NamespacedKey.class}, new Object[]{key});
            }
        }

        if (config.contains("hide-tool-tip")) {
            invokeMethod(meta, "setHideTooltip", new Class<?>[]{boolean.class}, new Object[]{config.getBoolean("hide-tool-tip")});
        }

        if (config.contains("tooltip-style")) {
            String style = config.getString("tooltip-style");
            NamespacedKey key = parseKey(style);
            if (key != null) {
                invokeMethod(meta, "setTooltipStyle", new Class<?>[]{NamespacedKey.class}, new Object[]{key});
            }
        }

        item.setItemMeta(meta);
    }

    private static NamespacedKey parseKey(String s) {
        if (s == null || s.isEmpty()) return null;
        s = s.toLowerCase().trim();
        try {
            if (s.contains(":")) {
                return NamespacedKey.fromString(s);
            } else {
                return NamespacedKey.minecraft(s);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void invokeMethod(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {

            Method method = ItemMeta.class.getMethod(methodName, parameterTypes);
            method.invoke(target, args);
        } catch (NoSuchMethodException e) {

        } catch (Exception e) {
            SellGUIMain.getInstance().getLogger().warning("Failed to invoke " + methodName + ": " + e.getMessage());
        }
    }

    public static ItemStack createItem(ConfigurationSection config, Material defaultMaterial) {
        if (config == null) return new ItemStack(defaultMaterial);
        Material material = Material.matchMaterial(config.getString("material", defaultMaterial.name()));
        if (material == null) material = defaultMaterial;

        ItemStack item = new ItemStack(material);
        applyConfig(item, config);
        return item;
    }
}

