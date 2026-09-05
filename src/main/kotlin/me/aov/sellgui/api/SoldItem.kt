package me.aov.sellgui.api

import org.bukkit.inventory.ItemStack

class SoldItem(itemStack: ItemStack?, private val amount: Int, private val unitPrice: Double, private val totalPrice: Double) {
    private val itemStack: ItemStack? = itemStack?.clone()

    fun getItemStack(): ItemStack? = itemStack?.clone()

    fun getAmount(): Int = amount

    fun getUnitPrice(): Double = unitPrice

    fun getTotalPrice(): Double = totalPrice
}
