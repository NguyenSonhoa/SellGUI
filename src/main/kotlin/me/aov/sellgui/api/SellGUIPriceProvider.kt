package me.aov.sellgui.api

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

interface SellGUIPriceProvider {
    fun getName(): String

    fun getPriority(): Int = 0

    fun isAvailable(): Boolean = true

    fun getSellPrice(player: Player, itemStack: ItemStack): Double

    fun onItemsSold(player: Player, soldItems: List<SoldItem>, totalPrice: Double) = Unit
}
