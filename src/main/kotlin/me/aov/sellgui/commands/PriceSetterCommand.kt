package me.aov.sellgui.commands

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.gui.PriceSetterGUI
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PriceSetterCommand(private val main: SellGUIMain) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player ?: run { sender.sendMessage(color(main.langConfig.getString("price-setter-players-only", "&cThis command can only be used by players!"))); return true }
        if (!command.name.equals("sellguiprice", true)) return false
        if (args.isEmpty()) { if (!player.hasPermission("sellgui.setprice")) { player.sendMessage(color(main.langConfig.getString("price-setter-no-permission", "&cYou don't have permission to use this command!"))); return true }; openGUIs[player.uniqueId] = PriceSetterGUI(main, player); return true }
        if (args.size != 1) { player.sendMessage(color("&cUsage: /sellguiprice [price]")); return true }
        val gui = openGUIs[player.uniqueId] ?: (player.openInventory.topInventory.holder as? PriceSetterGUI) ?: run { player.sendMessage(color("&cYou need to have the Price Setter GUI open to use this command!")); return true }
        val price = args[0].toDoubleOrNull() ?: run { player.sendMessage(color("&cInvalid price! Please enter a valid number.")); return true }
        if (price < 0) { player.sendMessage(color("&cPrice cannot be negative!")); return true }
        if (gui.savePrice(price)) player.sendMessage(color("&aPrice set successfully! Click the Save button to confirm."))
        return true
    }
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if (args.size != 1) return emptyList(); val input = args[0].lowercase(); val suggestions = BASE_SUGGESTIONS.toMutableList(); (sender as? Player)?.inventory?.itemInMainHand?.takeIf { it.type != Material.AIR }?.let { suggestions += contextualSuggestions(it) }; return suggestions.distinct().filter { it.lowercase().startsWith(input) }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    private fun contextualSuggestions(item: ItemStack): List<String> = item.type.name.let { name -> when { name.contains("_ORE") || name.contains("COAL") || name.contains("IRON_INGOT") || name.contains("GOLD_INGOT") || name.contains("DIAMOND") || name.contains("EMERALD") -> listOf("1.0", "2.5", "5.0", "10.0"); item.type.isEdible || name.contains("BREAD") || name.contains("MEAT") || name.contains("FISH") || name.contains("APPLE") -> listOf("0.5", "1.0", "2.0", "3.0"); name.contains("_SWORD") || name.contains("_AXE") || name.contains("_PICKAXE") || name.contains("_SHOVEL") || name.contains("_HOE") || name.contains("BOW") || name.contains("CROSSBOW") || name.contains("TRIDENT") -> listOf("10.0", "25.0", "50.0", "100.0"); name.contains("_HELMET") || name.contains("_CHESTPLATE") || name.contains("_LEGGINGS") || name.contains("_BOOTS") || name.contains("SHIELD") -> listOf("15.0", "30.0", "75.0", "150.0"); item.type.isBlock -> listOf("0.1", "0.5", "1.0", "2.0"); else -> emptyList() } }
    private fun color(text: String?): String = ChatColor.translateAlternateColorCodes('&', text ?: "")
    companion object {
        private val openGUIs = ConcurrentHashMap<UUID, PriceSetterGUI>()
        private val BASE_SUGGESTIONS = listOf("0.1", "0.5", "1.0", "5.0", "10.0", "25.0", "50.0", "100.0", "250.0", "500.0", "1000.0", "remove", "delete", "clear", "0")
        @JvmStatic fun getPriceSetterGUI(player: Player): PriceSetterGUI? = openGUIs[player.uniqueId]
        @JvmStatic fun removePriceSetterGUI(player: Player) { openGUIs.remove(player.uniqueId) }
        @JvmStatic fun hasPriceSetterGUI(player: Player): Boolean = openGUIs.containsKey(player.uniqueId)
        @JvmStatic fun getOpenGUIs(): Map<UUID, PriceSetterGUI> = openGUIs
    }
}
