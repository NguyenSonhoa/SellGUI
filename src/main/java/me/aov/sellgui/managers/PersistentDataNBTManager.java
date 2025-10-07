package me.aov.sellgui.managers;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.apache.commons.lang.WordUtils;

public class PersistentDataNBTManager implements ItemNBTManager {

    private final NamespacedKey sellPriceKey;
    private final NamespacedKey needsEvaluationKey;

    public PersistentDataNBTManager(SellGUIMain plugin) {
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
}