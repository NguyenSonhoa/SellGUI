package me.aov.sellgui.listeners

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.gui.PriceEvaluationGUI
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.persistence.PersistentDataType

class PriceEvaluationListener(private val main: SellGUIMain) : Listener {
    @EventHandler fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return; val gui = main.getGUIManager().getActivePriceEvaluationGUI(player) ?: return
        if (event.inventory != gui.inventory) return
        if (gui.isLocked()) { event.isCancelled = true; player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cEvaluation in progress! Please wait...")); return }
        val clicked = event.currentItem
        if (event.clickedInventory == player.inventory) { handlePlayerInventoryClick(event, gui, player, clicked); return }
        if (event.rawSlot >= gui.inventory.size) return
        if (event.rawSlot == gui.getItemSlot()) { if (event.isShiftClick && clicked?.type != Material.AIR) { gui.returnItemToPlayer(); event.isCancelled = true }; return }
        val action = clicked?.takeIf { it.hasItemMeta() }?.itemMeta?.persistentDataContainer?.get(NamespacedKey(main, "sellgui-nbt-id"), PersistentDataType.STRING)
        if (action != null) { event.isCancelled = true; when (action) { "evaluate_button" -> gui.startEvaluation(); "cancel_button" -> player.closeInventory() }; return }
        event.isCancelled = true
    }
    private fun handlePlayerInventoryClick(event: InventoryClickEvent, gui: PriceEvaluationGUI, player: Player, item: org.bukkit.inventory.ItemStack?) {
        if (!event.isShiftClick || item == null || item.type == Material.AIR) return
        if (gui.inventory.getItem(gui.getItemSlot())?.type != Material.AIR) { event.isCancelled = true; return }
        val allowStack = main.configManager.getConfig("config").getBoolean("general.allow-player-evaluation-stack", true)
        if (!allowStack && item.amount > 1) { player.sendMessage(ChatColor.translateAlternateColorCodes('&', main.configManager.getConfig("messages").getString("price-evaluation.drag-stack-not-allowed", "&cYou can't Evaluate more than 1 amount at a time.").orEmpty())); event.isCancelled = true; return }
        gui.inventory.setItem(gui.getItemSlot(), item.clone()); event.currentItem = null; event.isCancelled = true
    }
    @EventHandler fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return; val gui = main.getGUIManager().getActivePriceEvaluationGUI(player) ?: return
        if (event.inventory != gui.inventory) return; if (gui.isLocked()) { event.isCancelled = true; return }
        if (gui.getItemSlot() !in event.rawSlots) { event.isCancelled = true; return }
        event.isCancelled = true
        if (gui.inventory.getItem(gui.getItemSlot())?.type != Material.AIR) return
        val dragged = event.oldCursor ?: return
        if (dragged.type == Material.AIR) return
        if (!main.configManager.getConfig("config").getBoolean("general.allow-player-evaluation-stack", true) && dragged.amount > 1) { player.sendMessage(ChatColor.translateAlternateColorCodes('&', main.configManager.getConfig("messages").getString("price-evaluation.drag-stack-not-allowed", "&cYou can't Evaluate more than 1 amount at a time.").orEmpty())); return }
        gui.inventory.setItem(gui.getItemSlot(), dragged.clone()); player.setItemOnCursor(null)
    }
    @EventHandler fun onInventoryClose(event: InventoryCloseEvent) { val player = event.player as? Player ?: return; val gui = main.getGUIManager().getActivePriceEvaluationGUI(player) ?: return; if (event.inventory == gui.inventory) { gui.cleanup(); gui.returnItemToPlayer(); main.getGUIManager().removePlayer(player) } }
}
