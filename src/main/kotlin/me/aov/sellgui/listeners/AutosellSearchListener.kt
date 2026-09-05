package me.aov.sellgui.listeners

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.gui.AutosellSettingsGUI
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AutosellSearchListener(private val plugin: SellGUIMain) : Listener {
    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val eventPlayer = event.player
        if (searchingPlayers.remove(eventPlayer.uniqueId)) {
            event.isCancelled = true
            val message = event.message
            object : BukkitRunnable() {
                override fun run() {
                    val searchResultGUI = AutosellSettingsGUI(plugin, eventPlayer, message)
                    eventPlayer.openInventory(searchResultGUI.inventory)
                }
            }.runTask(plugin)
        }
    }

    companion object {
        private val searchingPlayers = ConcurrentHashMap.newKeySet<UUID>()

        @JvmStatic
        fun addSearchingPlayer(uuid: UUID) {
            searchingPlayers.add(uuid)
        }
    }
}
