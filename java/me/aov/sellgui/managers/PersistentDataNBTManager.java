package me.aov.sellgui.managers;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.apache.commons.lang.WordUtils;

public class PersistentDataNBTManager implements ItemNBTManager {

    private final SellGUIMain plugin; // Added to access plugin for NamespacedKey
    private final NamespacedKey sellPriceKey;
    private final NamespacedKey needsEvaluationKey;

    public PersistentDataNBTManager(SellGUIMain plugin) {
        this.plugin = plugin; // Initialize plugin
        this.sellPriceKey = new NamespacedKey(plugin, "sellgui_sell_price");
        this.needsEvaluationKey = new NamespacedKey(plugin, "sellgui_needs_evaluation");
    }

    @Override
    public double getSellPrice(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return 0.0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(sellPriceKey, PersistentDataType.DOUBLE)) {
            return meta.getPersistentDataContainer().get(sellPriceKey, PersistentDataType.DOUBLE);
        }
        return 0.0;
    }

    @Override
    public void setSellPrice(ItemStack item, double price) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(sellPriceKey, PersistentDataType.DOUBLE, price);
            item.setItemMeta(meta);
        }
    }

    @Override
    public boolean needsEvaluation(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(needsEvaluationKey, PersistentDataType.BOOLEAN)) {
            return meta.getPersistentDataContainer().get(needsEvaluationKey, PersistentDataType.BOOLEAN);
        }
        return false;
    }

    @Override
    public void setNeedsEvaluation(ItemStack item, boolean needsEvaluation) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(needsEvaluationKey, PersistentDataType.BOOLEAN, needsEvaluation);
            item.setItemMeta(meta);
        }
    }

    @Override
    public String getItemName(ItemStack item) {
        if (item == null) return "Unknown Item";

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return WordUtils.capitalizeFully(item.getType().name().replace('_', ' '));
        }

        if (meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        if (meta.hasItemName()) {
            return meta.getItemName();
        }

        return WordUtils.capitalizeFully(item.getType().name().replace('_', ' '));
    }

    @Override
    public void addNBTTag(ItemStack item, String key, String value) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey specificKey = new NamespacedKey(plugin, key);
            meta.getPersistentDataContainer().set(specificKey, PersistentDataType.STRING, value);
            item.setItemMeta(meta);
            plugin.getLogger().info("Added NBT Tag: " + key + " -> " + value + " to item " + item.getType().name());
        }
    }

    @Override
    public String getNBTTag(ItemStack item, String key) { // Removed PersistentDataType parameter
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey specificKey = new NamespacedKey(plugin, key);
            if (meta.getPersistentDataContainer().has(specificKey, PersistentDataType.STRING)) {
                String value = meta.getPersistentDataContainer().get(specificKey, PersistentDataType.STRING);
                plugin.getLogger().info("Retrieved NBT Tag: " + key + " -> " + value + " from item " + item.getType().name());
                return value;
            }
        }
        plugin.getLogger().info("NBT Tag not found: " + key + " from item " + item.getType().name());
        return null;
    }
}
