package me.aov.sellgui.commands

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.gui.SellMenuConfig
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.util.StringUtil
import java.util.Collections

class SellGUITabCompleter(private val plugin: SellGUIMain) : TabCompleter {
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String>? {
        if (!command.name.equals("sellgui", ignoreCase = true)) return null
        return when (args.size) {
            1 -> getFirstArgumentCompletions(sender, args[0])
            2 -> getSecondArgumentCompletions(sender, args[0], args[1])
            3 -> getThirdArgumentCompletions(sender, args[0], args[1], args[2])
            else -> getFurtherArgumentCompletions(sender, args)
        }
    }

    private fun getFirstArgumentCompletions(sender: CommandSender, input: String): List<String> {
        val completions = arrayListOf<String>()
        val possible = arrayListOf<String>()
        if (sender.hasPermission("sellgui.reload")) possible += "reload"
        if (sender.hasPermission("sellgui.setprice")) possible += listOf("setprice", "setrange")
        if (sender.hasPermission("sellgui.evaluate")) possible += "evaluate"
        if (sender.hasPermission("sellgui.autosell")) possible += "autosell"
        if (sender is Player && sender.hasPermission("sellgui.use")) possible += SellMenuConfig.getMenuIds(plugin)
        possible += "help"
        if (sender.hasPermission("sellgui.others")) Bukkit.getOnlinePlayers().forEach { possible += it.name }
        StringUtil.copyPartialMatches(input, possible, completions)
        return completions.sorted()
    }

    private fun getSecondArgumentCompletions(sender: CommandSender, firstArg: String, input: String): List<String> {
        val completions = arrayListOf<String>()
        when (firstArg.lowercase()) {
            "setprice" -> if (sender.hasPermission("sellgui.setprice")) {
                val options = ArrayList(commonPrices + priceCommands)
                (sender as? Player)?.let { addContextualPrices(options, it) }
                StringUtil.copyPartialMatches(input, options, completions)
            }
            "setrange" -> if (sender.hasPermission("sellgui.setprice")) StringUtil.copyPartialMatches(input, listOf("0.1", "0.5", "1.0", "5.0", "10.0", "25.0", "50.0", "100.0"), completions)
            "debug" -> if (sender.hasPermission("sellgui.admin")) StringUtil.copyPartialMatches(input, debugCommands, completions)
            else -> if (sender.hasPermission("sellgui.others") && Bukkit.getPlayer(firstArg) != null) StringUtil.copyPartialMatches(input, SellMenuConfig.getMenuIds(plugin), completions)
        }
        return completions.sorted()
    }

    private fun getThirdArgumentCompletions(sender: CommandSender, firstArg: String, secondArg: String, input: String): List<String> {
        val completions = arrayListOf<String>()
        if (firstArg.equals("setrange", ignoreCase = true) && sender.hasPermission("sellgui.setprice")) {
            val min = secondArg.toDoubleOrNull()
            val options = min?.let { listOf("${it * 1.5}", "${it * 2.0}", "${it * 3.0}", "${it * 5.0}", "${it * 10.0}") }
                ?: listOf("2.0", "5.0", "10.0", "25.0", "50.0", "100.0", "500.0", "1000.0")
            StringUtil.copyPartialMatches(input, options, completions)
        }
        return completions.sorted()
    }

    private fun getFurtherArgumentCompletions(sender: CommandSender, args: Array<String>): List<String> {
        val completions = arrayListOf<String>()
        if (args.isNotEmpty() && args[0].equals("placeholder", ignoreCase = true) && sender.hasPermission("sellgui.admin")) {
            StringUtil.copyPartialMatches(args.last(), listOf("%player%", "%vault_eco_balance%", "%player_world%", "%time%", "%date%", "%server_online%", "%sellgui_version%", "%player_level%"), completions)
        }
        return completions.sorted()
    }

    private fun addContextualPrices(prices: MutableList<String>, player: Player) {
        val heldItem = player.inventory.itemInMainHand
        if (heldItem.type == Material.AIR) return
        val material = heldItem.type
        val name = material.name
        when {
            name.contains("_ORE") || name.contains("DIAMOND") || name.contains("EMERALD") -> prices += listOf("2.0", "5.0", "10.0", "20.0")
            material.isEdible -> prices += listOf("0.5", "1.0", "2.0", "3.0")
            name.contains("_SWORD") || name.contains("_AXE") || name.contains("_PICKAXE") -> prices += listOf("15.0", "30.0", "75.0", "150.0")
            name.contains("_HELMET") || name.contains("_CHESTPLATE") || name.contains("_LEGGINGS") || name.contains("_BOOTS") -> prices += listOf("10.0", "25.0", "50.0", "100.0")
            name.contains("NETHERITE") || name.contains("ELYTRA") -> prices += listOf("100.0", "250.0", "500.0", "1000.0")
        }
    }

    companion object {
        private val commonPrices = listOf("0.1", "0.5", "1.0", "2.5", "5.0", "10.0", "25.0", "50.0", "100.0", "250.0", "500.0", "1000.0")
        private val priceCommands = listOf("remove", "delete", "clear", "0")
        private val debugCommands = listOf("info", "config", "economy", "placeholders", "sounds")
    }
}
