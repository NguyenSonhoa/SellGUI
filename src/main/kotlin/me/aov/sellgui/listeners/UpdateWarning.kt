package me.aov.sellgui.listeners

import me.aov.sellgui.SellGUIMain
import org.bukkit.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class UpdateWarning(@Suppress("unused") private val main: SellGUIMain) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (event.player.isOp) {
            event.player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[SellGUI] An update is available for SellGUI."))
        }
    }
}
