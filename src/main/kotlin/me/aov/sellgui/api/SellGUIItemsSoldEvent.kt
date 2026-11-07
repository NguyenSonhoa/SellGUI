package me.aov.sellgui.api

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class SellGUIItemsSoldEvent(private val player: Player, private val totalSoldValue: Double) : Event() {
    fun getPlayer(): Player = player

    fun getTotalSoldValue(): Double = totalSoldValue

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
