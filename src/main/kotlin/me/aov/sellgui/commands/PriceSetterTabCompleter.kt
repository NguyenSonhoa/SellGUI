package me.aov.sellgui.commands

import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.util.StringUtil

class PriceSetterTabCompleter : TabCompleter {
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String>? {
        if (!command.name.equals("sellguiprice", true)) return null
        val player = sender as? Player ?: return emptyList()
        val completions = ArrayList<String>()
        if (args.size == 1) {
            val suggestions = ArrayList(COMMON_PRICES).apply { addAll(SPECIAL_COMMANDS); addContextualSuggestions(this, player) }
            StringUtil.copyPartialMatches(args[0], suggestions, completions)
        }
        return completions.sorted()
    }

    private fun addContextualSuggestions(suggestions: MutableList<String>, player: Player) {
        val material = player.inventory.itemInMainHand.type
        if (material == Material.AIR) return
        suggestions += when {
            isOre(material) -> listOf("1.5", "3.0", "7.5", "15.0")
            isFood(material) -> listOf("0.25", "0.75", "1.5", "2.25")
            isTool(material) -> listOf("12.5", "37.5", "75.0", "150.0")
            isArmor(material) -> listOf("8.0", "20.0", "40.0", "80.0")
            isRare(material) -> listOf("75.0", "200.0", "500.0", "1500.0")
            else -> emptyList()
        }
    }

    private fun isOre(material: Material): Boolean = material.name.let { it.contains("_ORE") || it.contains("COAL") || it.contains("IRON_INGOT") || it.contains("GOLD_INGOT") || it.contains("DIAMOND") || it.contains("EMERALD") }
    private fun isFood(material: Material): Boolean = material.isEdible || material.name.contains("BREAD") || material.name.contains("MEAT") || material.name.contains("FISH")
    private fun isTool(material: Material): Boolean = material.name.let { it.contains("_SWORD") || it.contains("_AXE") || it.contains("_PICKAXE") || it.contains("_SHOVEL") || it.contains("_HOE") || it.contains("BOW") }
    private fun isArmor(material: Material): Boolean = material.name.let { it.contains("_HELMET") || it.contains("_CHESTPLATE") || it.contains("_LEGGINGS") || it.contains("_BOOTS") }
    private fun isRare(material: Material): Boolean = material.name.let { it.contains("NETHERITE") || it.contains("ELYTRA") || it.contains("TOTEM") || it.contains("DRAGON") || it == "BEACON" || it.contains("SHULKER") }

    private companion object {
        val COMMON_PRICES = listOf("0.1", "0.5", "1.0", "2.5", "5.0", "10.0", "25.0", "50.0", "100.0", "250.0", "500.0", "1000.0")
        val SPECIAL_COMMANDS = listOf("remove", "delete", "clear", "0")
    }
}
