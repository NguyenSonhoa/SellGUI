package me.aov.sellgui.listeners

import me.aov.sellgui.SellGUIMain
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerListener(private val plugin: SellGUIMain) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.autosellManager.startAutosellTask(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.autosellManager.stopAutosellTask(event.player)
    }
}
