package me.aov.sellgui.listeners

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.commands.PriceSetterCommand
import me.aov.sellgui.gui.PriceSetterGUI
import me.aov.sellgui.handlers.InventoryHandler
import me.aov.sellgui.handlers.SoundHandler
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class PriceSetterListener(private val main: SellGUIMain) : Listener {
    @EventHandler fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val gui = InventoryHandler.getTopInventoryHolder(event) as? PriceSetterGUI ?: return
        if (event.clickedInventory?.holder !is PriceSetterGUI) return
        handlePriceSetterGUIClick(event, gui, event.slot, event.currentItem)
    }
    private fun handlePriceSetterGUIClick(event: InventoryClickEvent, gui: PriceSetterGUI, slot: Int, clickedItem: ItemStack?) {
        if (slot == PriceSetterGUI.getItemSlot() && ((event.cursor?.type != Material.AIR) || clickedItem?.type != Material.AIR)) { SoundHandler.playItemPickup(event.whoClicked as Player); event.isCancelled = false; main.server.scheduler.runTaskLater(main, Runnable { gui.updateItemInfo() }, 1L); return }
        if (slot == PriceSetterGUI.getPriceInputSlot()) { event.isCancelled = true; return }
        val action = clickedItem?.takeIf { it.hasItemMeta() }?.itemMeta?.persistentDataContainer?.get(NamespacedKey(main, "price-setter-action"), PersistentDataType.STRING)
        if (action != null) { event.isCancelled = true; handleActionButton(event.whoClicked as Player, gui, action); return }
        event.isCancelled = true
    }
    @EventHandler fun onInventoryDrag(event: InventoryDragEvent) {
        val gui = InventoryHandler.getTopInventoryHolder(event) as? PriceSetterGUI ?: return
        val topSize = InventoryHandler.getTopInventorySize(event)
        val topSlots = event.rawSlots.filter { it < topSize }
        if (topSlots.isNotEmpty() && PriceSetterGUI.getItemSlot() !in topSlots) event.isCancelled = true
        else if (topSlots.isNotEmpty()) main.server.scheduler.runTaskLater(main, Runnable { gui.updateItemInfo() }, 1L)
    }
    @EventHandler fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (event.inventory.holder !is PriceSetterGUI) return
        PriceSetterCommand.removePriceSetterGUI(player)
        event.inventory.getItem(PriceSetterGUI.getItemSlot())?.takeIf { it.type != Material.AIR }?.let { item -> if (player.inventory.firstEmpty() != -1) player.inventory.addItem(item) else { player.world.dropItem(player.location, item); player.sendMessage(color("&eItem dropped on ground as your inventory is full!")) } }
    }
    private fun handleActionButton(player: Player, gui: PriceSetterGUI, action: String) = when (action.lowercase()) {
        "save" -> gui.inventory.getItem(PriceSetterGUI.getItemSlot())?.takeIf { it.type != Material.AIR }?.let { SoundHandler.playUIClick(player); player.sendMessage(color("&aUse &f/sellguiprice <price> &ato set the price for this item.")) } ?: run { SoundHandler.playError(player); player.sendMessage(color("&cNo item found to save price for!")) }
        "cancel" -> { SoundHandler.playChestClose(player); player.closeInventory(); player.sendMessage(color("&cPrice setting cancelled.")) }
        "delete" -> if (gui.deletePrice()) { SoundHandler.playSuccess(player); player.sendMessage(color("&aPrice deleted successfully!")) } else SoundHandler.playError(player)
        "chat" -> gui.inventory.getItem(PriceSetterGUI.getItemSlot())?.takeIf { it.type != Material.AIR }?.let { PriceSetterChatListener.setPlayerItem(player, it.clone()); SoundHandler.playUIClick(player); player.closeInventory(); PriceSetterChatListener.startWaitingForPrice(player) } ?: run { SoundHandler.playError(player); player.sendMessage(color("&cNo item found to set price for!")) }
        else -> player.sendMessage(color("&cUnknown action: $action"))
    }
    private fun color(text: String): String = ChatColor.translateAlternateColorCodes('&', text)
}
