package me.aov.sellgui.api

import me.aov.sellgui.SellGUI
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class PlayerSellItemsEvent(
    player: Player,
    sellGUI: SellGUI,
    private val soldItems: List<ItemStack>,
    private val totalPrice: Double,
) : SellGUIEvents(player, sellGUI) {
    fun getSoldItems(): List<ItemStack> = soldItems

    fun getTotalPrice(): Double = totalPrice
}
