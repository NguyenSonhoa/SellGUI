package me.aov.sellgui.managers;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public interface ItemNBTManager {
    double getSellPrice(ItemStack item);
    void setSellPrice(ItemStack item, double price);
    boolean needsEvaluation(ItemStack item);
    void setNeedsEvaluation(ItemStack item, boolean needsEvaluation);
    String getItemName(ItemStack item);

    // New methods for NBT tag manipulation
    void addNBTTag(ItemStack itemStack, String key, String value);
    String getNBTTag(ItemStack itemStack, String key); // Removed PersistentDataType parameter
}
