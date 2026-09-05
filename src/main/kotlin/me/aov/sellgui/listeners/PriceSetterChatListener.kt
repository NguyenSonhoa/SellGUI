package me.aov.sellgui.listeners

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.gui.PriceSetterGUI
import me.aov.sellgui.handlers.SoundHandler
import me.aov.sellgui.managers.PriceManager
import me.aov.sellgui.utils.ItemIdentifier
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PriceSetterChatListener(private val main: SellGUIMain) : Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        if (!waitingForPrice.remove(player.uniqueId)) return
        event.isCancelled = true
        main.server.scheduler.runTask(main, Runnable { handlePriceInput(player, event.message.trim()) })
    }
    private fun handlePriceInput(player: Player, input: String) {
        val playerId = player.uniqueId
        val item = playerItems[playerId]
        if (input.equals("cancel", true) || input.equals("c", true)) { player.sendMessage(color("&cPrice input cancelled.")); playerItems.remove(playerId); PriceSetterGUI(main, player); return }
        if (item == null) { player.sendMessage(color("&cNo item found! Please try again.")); playerItems.remove(playerId); return }
        val price = input.toDoubleOrNull()
        if (price == null || price < 0) { SoundHandler.playError(player); player.sendMessage(color(if (price == null) "&cInvalid price! Please enter a valid number or 'cancel' to cancel." else "&cPrice cannot be negative! Please enter a valid price or 'cancel' to cancel.")); startWaitingForPrice(player); return }
        if (PriceManager(main).setItemPrice(item, price)) {
            SoundHandler.playSuccess(player)
            val itemName = ItemIdentifier.getItemDisplayName(item); val itemType = ItemIdentifier.getItemType(item).name
            player.sendMessage(color(if (price == 0.0) "&aSuccessfully removed price for &f$itemName &7($itemType)" else "&aSuccessfully set price for &f$itemName &7($itemType) &ato &e$${"%.2f".format(price)}"))
            playerItems.remove(playerId)
            main.server.scheduler.runTaskLater(main, Runnable { PriceSetterGUI(main, player).also { gui -> gui.inventory.setItem(PriceSetterGUI.getItemSlot(), item); gui.updateItemInfo() } }, 1L)
        } else { SoundHandler.playError(player); player.sendMessage(color("&cFailed to set price! Check console for errors.")); playerItems.remove(playerId) }
    }
    companion object {
        private val waitingForPrice = ConcurrentHashMap.newKeySet<UUID>()
        private val playerItems = ConcurrentHashMap<UUID, ItemStack>()
        @JvmStatic fun startWaitingForPrice(player: Player) { waitingForPrice.add(player.uniqueId); player.sendMessage(color("&eEnter the price in chat (or type 'cancel' to cancel):")) }
        @JvmStatic fun stopWaitingForPrice(player: Player) { waitingForPrice.remove(player.uniqueId); playerItems.remove(player.uniqueId) }
        @JvmStatic fun isWaitingForPrice(player: Player): Boolean = waitingForPrice.contains(player.uniqueId)
        @JvmStatic fun setPlayerItem(player: Player, item: ItemStack) { playerItems[player.uniqueId] = item }
        @JvmStatic fun getPlayerItem(player: Player): ItemStack? = playerItems[player.uniqueId]
        @JvmStatic private fun color(text: String): String = ChatColor.translateAlternateColorCodes('&', text)
    }
}
