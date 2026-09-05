package me.aov.sellgui.api

import me.aov.sellgui.SellGUI
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

open class SellGUIEvents(private val player: Player, private val sellGUI: SellGUI) : Event() {
    fun getPlayer(): Player = player

    fun getSellGUI(): SellGUI = sellGUI

    override fun getHandlers(): HandlerList = handlers

    companion object {
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
}
