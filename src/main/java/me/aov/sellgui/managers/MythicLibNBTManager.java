package me.aov.sellgui.managers;

import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class MythicLibNBTManager implements ItemNBTManager {

    private static final String SELL_PRICE_TAG = "sellPrice";
    private static final String NEEDS_EVALUATION_TAG = "needsEvaluation";

    @Override
    public double getSellPrice(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return 0.0;
        }
        NBTItem nbtItem = NBTItem.get(item);
        return nbtItem.getDouble(SELL_PRICE_TAG);
    }

    @Override
    public void setSellPrice(ItemStack item, double price) {
        if (item == null || item.getItemMeta() == null) {
            return;
        }
        NBTItem nbtItem = NBTItem.get(item);
        nbtItem.setDouble(SELL_PRICE_TAG, price);
        // MythicLib's NBTItem automatically updates the ItemStack when modified
    }

    @Override
    public boolean needsEvaluation(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }
        NBTItem nbtItem = NBTItem.get(item);
        return nbtItem.getBoolean(NEEDS_EVALUATION_TAG);
    }

    @Override
    public void setNeedsEvaluation(ItemStack item, boolean needsEvaluation) {
        if (item == null || item.getItemMeta() == null) {
            return;
        }
        NBTItem nbtItem = NBTItem.get(item);
        nbtItem.setBoolean(NEEDS_EVALUATION_TAG, needsEvaluation);
        // MythicLib's NBTItem automatically updates the ItemStack when modified
    }

    @Override
    public String getItemName(ItemStack item) {
        if (item == null) {
            return "Unknown Item";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        return item.getType().name().replace("_", " ").toLowerCase();
    }

    @Override
    public void addNBTTag(ItemStack itemStack, String key, String value) {
        if (itemStack == null) return;
        NBTItem nbtItem = NBTItem.get(itemStack);
        nbtItem.setString(key, value);
        // MythicLib's NBTItem automatically updates the ItemStack when modified
    }

    @Override
    public String getNBTTag(ItemStack itemStack, String key) {
        if (itemStack == null) return null;
        NBTItem nbtItem = NBTItem.get(itemStack);
        return nbtItem.getString(key);
    }
}