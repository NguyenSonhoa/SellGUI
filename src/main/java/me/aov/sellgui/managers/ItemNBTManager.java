package me.aov.sellgui.managers;

import org.bukkit.inventory.ItemStack;

public interface ItemNBTManager {
    double getSellPrice(ItemStack item);
    void setSellPrice(ItemStack item, double price);
    boolean needsEvaluation(ItemStack item);
    void setNeedsEvaluation(ItemStack item, boolean needsEvaluation);
    String getItemName(ItemStack item);
}