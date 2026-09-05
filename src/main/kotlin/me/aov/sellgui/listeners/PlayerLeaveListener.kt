package me.aov.sellgui.listeners

import me.aov.sellgui.SellGUIMain
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerLeaveListener(private val plugin: SellGUIMain) : Listener {
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val pendingItem = pendingItems.remove(player.uniqueId) ?: return
        if (player.inventory.firstEmpty() != -1) {
            player.inventory.addItem(pendingItem)
        } else {
            player.world.dropItemNaturally(player.location, pendingItem)
        }
        plugin.logger.info("Returned evaluation item to ${player.name} on quit")
    }

    companion object {
        private val pendingItems = ConcurrentHashMap<UUID, ItemStack>()

        @JvmStatic
        fun storePendingItem(playerId: UUID, item: ItemStack?) {
            if (item != null) pendingItems[playerId] = item.clone()
        }

        @JvmStatic
        fun removePendingItem(playerId: UUID) {
            pendingItems.remove(playerId)
        }

        @JvmStatic
        fun hasPendingItem(playerId: UUID): Boolean = pendingItems.containsKey(playerId)
    }
}
