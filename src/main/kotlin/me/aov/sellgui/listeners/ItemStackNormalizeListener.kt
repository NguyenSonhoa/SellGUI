package me.aov.sellgui.listeners

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.utils.ItemStackNormalizer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.FurnaceExtractEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class ItemStackNormalizeListener(private val plugin: SellGUIMain) : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (isEnabled("stacking.normalize-on-join", true)) normalizeLater(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (isEnabled("stacking.normalize-on-quit", true)) ItemStackNormalizer.normalizePlayerInventory(plugin, event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFurnaceSmelt(event: FurnaceSmeltEvent) {
        event.result?.let { result ->
            if (isEnabled("stacking.normalize-smelt-results", true)) {
                ItemStackNormalizer.normalizedCopy(plugin, result)?.let { event.result = it }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFurnaceExtract(event: FurnaceExtractEvent) {
        if (isEnabled("stacking.normalize-after-furnace-extract", true)) normalizeLater(event.player)
    }

    private fun normalizeLater(player: Player) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable { ItemStackNormalizer.normalizePlayerInventory(plugin, player) }, 1L)
    }

    private fun isEnabled(path: String, defaultValue: Boolean): Boolean =
        plugin.config.getBoolean("stacking.enabled", true) && plugin.config.getBoolean(path, defaultValue)
}
