package me.aov.sellgui.api;

import org.bukkit.inventory.ItemStack;

public final class SoldItem {
    private final ItemStack itemStack;
    private final int amount;
    private final double unitPrice;
    private final double totalPrice;

    public SoldItem(ItemStack itemStack, int amount, double unitPrice, double totalPrice) {
        this.itemStack = itemStack == null ? null : itemStack.clone();
        this.amount = amount;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
    }

    public ItemStack getItemStack() {
        return itemStack == null ? null : itemStack.clone();
    }

    public int getAmount() {
        return amount;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getTotalPrice() {
        return totalPrice;
    }
}
