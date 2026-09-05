package me.aov.sellgui.listeners

import me.aov.sellgui.SellGUI
import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.commands.SellCommand
import me.aov.sellgui.handlers.SoundHandler
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class InventoryListeners(private val main: SellGUIMain) : Listener {
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val sellGUI = SellCommand.getSellGUI(player) ?: return
        if (event.inventory != sellGUI.getMenu()) return
        dropItems(event.inventory, player)
        sellGUI.cleanup()
        SellCommand.getSellGUIs().remove(sellGUI)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val sellGUI = SellCommand.getSellGUI(player) ?: return
        if (event.view.topInventory != sellGUI.getMenu()) return
        for (slot in event.rawSlots) {
            if (slot < event.view.topInventory.size) {
                val item = event.view.topInventory.getItem(slot)
                if (isGUIControlItem(item) || isCustomMenuItem(item)) {
                    event.isCancelled = true
                    return
                }
            }
        }
        scheduleMenuRefresh(player, sellGUI)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val currentItem = event.currentItem
        val sellGUI = SellCommand.getSellGUI(player) ?: return
        if (event.view.topInventory != sellGUI.getMenu()) return
        if (event.action == InventoryAction.COLLECT_TO_CURSOR) {
            event.isCancelled = true
            return
        }
        if (sellGUI.isConfirmMode()) {
            val action = currentItem?.itemMeta?.persistentDataContainer?.get(NamespacedKey(main, "guiAction"), PersistentDataType.STRING)
            val isConfirmButton = isGUIControlItem(currentItem) && action == "confirm"
            if (!isConfirmButton) {
                if (isGUIControlItem(currentItem) || isCustomMenuItem(currentItem)) {
                    event.isCancelled = true
                    return
                }
                sellGUI.setSellItem()
            }
        }
        when {
            isGUIControlItem(currentItem) -> {
                event.isCancelled = true
                when (currentItem?.itemMeta?.persistentDataContainer?.get(NamespacedKey(main, "guiAction"), PersistentDataType.STRING)) {
                    "confirm" -> {
                        if (sellGUI.hasUnevaluatedItems()) {
                            player.closeInventory()
                            val message = main.getMessagesConfig().getString("messages.evaluation-required", "&cYou have items that need to be evaluated before selling.")
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message.orEmpty()))
                            return
                        }
                        SoundHandler.playConfigSound(player, "sounds.ui.confirm")
                        sellGUI.returnInvalidItems()
                        sellGUI.sellItems(sellGUI.getMenu())
                    }
                    "sell" -> {
                        sellGUI.returnInvalidItems()
                        if (sellGUI.getTotal(sellGUI.getMenu()) <= 0) {
                            SoundHandler.playConfigSound(player, "sounds.feedback.fail")
                            return
                        }
                        SoundHandler.playUIClick(player)
                        sellGUI.updateButtonState()
                    }
                }
            }
            isCustomMenuItem(currentItem) -> {
                event.isCancelled = true
                handleCustomMenuItemClick(player, currentItem!!)
            }
            else -> scheduleMenuRefresh(player, sellGUI)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (main.config.getBoolean("general.add-worth-lore", false)) Bukkit.getScheduler().runTaskLater(main, event.player::updateInventory, 1L)
    }

    private fun scheduleMenuRefresh(player: Player, sellGUI: SellGUI) {
        Bukkit.getScheduler().runTaskLater(main, Runnable {
            SellCommand.getSellGUI(player)?.takeIf { it === sellGUI }?.let {
                it.returnInvalidItems()
                it.updateSellItemTotal()
            }
        }, 1L)
    }

    private fun isGUIControlItem(item: ItemStack?): Boolean = item?.takeIf { it.hasItemMeta() }?.itemMeta
        ?.persistentDataContainer?.has(NamespacedKey(main, "sellgui"), PersistentDataType.BYTE) == true

    private fun handleCustomMenuItemClick(player: Player, item: ItemStack) {
        val data = item.itemMeta.persistentDataContainer
        val commands = data.get(NamespacedKey(main, "custom-menu-item"), PersistentDataType.STRING)
        val sender = data.get(NamespacedKey(main, "custom-menu-item-sender"), PersistentDataType.STRING)
        val closeMenu = data.get(NamespacedKey(main, "custom-menu-item-close-menu"), PersistentDataType.BYTE)
        if (commands.isNullOrEmpty()) return
        if (closeMenu == 1.toByte()) {
            player.closeInventory()
            Bukkit.getScheduler().runTask(main, Runnable { executeCustomMenuCommands(player, commands, sender) })
        } else {
            executeCustomMenuCommands(player, commands, sender)
        }
    }

    private fun executeCustomMenuCommands(player: Player, commands: String, sender: String?) {
        commands.split(';').map(String::trim).filter(String::isNotEmpty).forEach { command ->
            val finalCommand = command.removePrefix("/")
            when (sender?.lowercase() ?: "console") {
                "player" -> player.performCommand(finalCommand)
                "op" -> {
                    val wasOp = player.isOp
                    try {
                        player.isOp = true
                        player.performCommand(finalCommand)
                    } finally {
                        player.isOp = wasOp
                    }
                }
                else -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
            }
        }
    }

    private fun dropItems(inventory: Inventory, player: Player) {
        inventory.contents.forEach { item ->
            if (item == null || item.type == Material.AIR || isGUIControlItem(item) || isCustomMenuItem(item)) return@forEach
            if (main.config.getBoolean("drop-items-on-close", false)) {
                player.world.dropItem(player.location, item)
            } else {
                player.inventory.addItem(item).values.forEach { player.world.dropItem(player.location, it) }
            }
        }
    }

    private fun isCustomMenuItem(item: ItemStack?): Boolean = item?.takeIf { it.hasItemMeta() }?.itemMeta
        ?.persistentDataContainer?.has(NamespacedKey(main, "custom-menu-item"), PersistentDataType.STRING) == true
}
