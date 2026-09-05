package me.aov.sellgui.listeners

import me.aov.sellgui.managers.AutosellManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class AutosellPlayerListener(private val autosellManager: AutosellManager) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        autosellManager.startAutosellTask(event.player)
    }
}
