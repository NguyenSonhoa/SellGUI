package me.aov.sellgui.commands

import me.aov.sellgui.SellGUI
import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.gui.PriceEvaluationGUI
import me.aov.sellgui.gui.SellMenuConfig
import me.aov.sellgui.managers.PriceManager
import me.aov.sellgui.utils.ColorUtils
import me.aov.sellgui.utils.ItemIdentifier
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class SellCommand(private val main: SellGUIMain) : CommandExecutor, TabCompleter {
    init { sellGUIS = arrayListOf() }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            (sender as? Player)?.let { openSellMenu(sender, it, SellMenuConfig.DEFAULT_MENU_ID) }
                ?: sender.sendMessage(color("&cUsage: /sellgui <player> [menu]"))
            return true
        }
        when (args[0].lowercase()) {
            "reload" -> {
                if (!sender.hasPermission("sellgui.reload")) sender.sendMessage(color("&cYou do not have permission."))
                else { main.reload(); sender.sendMessage(color("&aSellGUI configs and GUIs have been reloaded.")) }
                return true
            }
            "evaluate" -> {
                val player = sender as? Player ?: run { sender.sendMessage(color("&cOnly players can use this command.")); return true }
                if (!sender.hasPermission("sellgui.evaluate")) sender.sendMessage(color("&cYou do not have permission.")) else main.getGUIManager().openPriceEvaluationGUI(player)
                return true
            }
            "setprice" -> {
                val player = sender as? Player ?: run { sender.sendMessage(color("&cOnly players can use this command.")); return true }
                if (!sender.hasPermission("sellgui.setprice")) { sender.sendMessage(color("&cYou do not have permission.")); return true }
                if (args.size != 2) { sender.sendMessage(color("&cUsage: /sellgui setprice <amount>")); return true }
                return handleSetPriceInHand(player, args[1])
            }
            "setrange" -> {
                val player = sender as? Player ?: run { sender.sendMessage(color("&cOnly players can use this command.")); return true }
                if (!sender.hasPermission("sellgui.setrange")) { sender.sendMessage(color("&cYou do not have permission.")); return true }
                if (args.size != 3) { sender.sendMessage(color("&cUsage: /sellgui setrange <min> <max>")); return true }
                return handleSetRandomPrice(player, args[1], args[2])
            }
            "autosell" -> {
                val player = sender as? Player ?: run { sender.sendMessage(color("&cOnly players can use this command.")); return true }
                if (!sender.hasPermission("sellgui.autosell")) sender.sendMessage(color("&cYou do not have permission.")) else main.getGUIManager().openAutosellSettingsGUI(player)
                return true
            }
            "help" -> return handleHelpCommand(sender)
            else -> {
                if (args.size == 1 && sender is Player && SellMenuConfig.menuExists(main, args[0])) return openSellMenu(sender, sender, args[0])
                if (args.size == 1) {
                    if (sender.hasPermission("sellgui.others") || sender !is Player) {
                        main.server.getPlayer(args[0])?.let { openSellMenu(sender, it, SellMenuConfig.DEFAULT_MENU_ID) }
                            ?: sender.sendMessage(color("&cPlayer '${args[0]}' not found or is not online."))
                    } else sender.sendMessage(color("&cYou do not have permission to open SellGUI for other players."))
                    return true
                }
                if (args.size == 2) {
                    if (!sender.hasPermission("sellgui.others") && sender is Player) {
                        sender.sendMessage(color("&cYou do not have permission to open SellGUI for other players.")); return true
                    }
                    main.server.getPlayer(args[0])?.let { openSellMenu(sender, it, args[1]) }
                        ?: sender.sendMessage(color("&cPlayer '${args[0]}' not found or is not online."))
                    return true
                }
            }
        }
        sender.sendMessage(color("&cInvalid command usage. Try: /$label help"))
        return true
    }

    private fun openSellMenu(sender: CommandSender, target: Player, requestedMenuId: String): Boolean {
        val menuConfig = SellMenuConfig.load(main, requestedMenuId)
        if (menuConfig == null) {
            sender.sendMessage(color("&cUnknown sell menu: &f$requestedMenuId"))
            sender.sendMessage(color("&7Available menus: &f${SellMenuConfig.getMenuIds(main).joinToString(", ")}"))
            return true
        }
        val openingSelf = sender is Player && sender.uniqueId == target.uniqueId
        if (openingSelf) {
            if (!target.hasPermission("sellgui.use")) { target.sendMessage(color("&cYou do not have permission to use this command.")); return true }
            menuConfig.getPermission()?.takeIf { it.isNotBlank() && !target.hasPermission(it) }?.let { target.sendMessage(color("&cYou do not have permission to open this sell menu.")); return true }
        } else if (sender is Player && !sender.hasPermission("sellgui.others")) {
            sender.sendMessage(color("&cYou do not have permission to open SellGUI for other players.")); return true
        }
        getSellGUI(target)?.let { existing -> target.closeInventory(); existing.cleanup(); sellGUIS.remove(existing) }
        sellGUIS += SellGUI(main, target, main.itemNBTManager, menuConfig.getId())
        if (!openingSelf) sender.sendMessage(color("&aOpened SellGUI menu &f${menuConfig.getId()} &afor ${target.name}."))
        return true
    }

    private fun handleSetPriceInHand(player: Player, priceString: String): Boolean {
        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) { player.sendMessage(color("&cYou must be holding an item to set its price!")); return true }
        val price = priceString.toDoubleOrNull() ?: run { player.sendMessage(color("&cInvalid price! Please enter a valid number.")); return true }
        if (price < 0) { player.sendMessage(color("&cPrice cannot be negative!")); return true }
        if (PriceManager(main).setItemPrice(item, price)) {
            val action = if (price == 0.0) "&aSuccessfully removed price for &f${ItemIdentifier.getItemDisplayName(item)} &7(${ItemIdentifier.getItemType(item).name})" else "&aSuccessfully set price for &f${ItemIdentifier.getItemDisplayName(item)} &7(${ItemIdentifier.getItemType(item).name}) &ato &e$${"%.2f".format(price)}"
            player.sendMessage(color(action))
        } else player.sendMessage(color("&cFailed to set price! Check console for errors."))
        return true
    }

    private fun handleSetRandomPrice(player: Player, minString: String, maxString: String): Boolean {
        val min = minString.toDoubleOrNull(); val max = maxString.toDoubleOrNull()
        if (min == null || max == null) { player.sendMessage(color("&cInvalid price format! Use numbers only.")); return true }
        if (min < 0 || max < 0) { player.sendMessage(color("&cPrices cannot be negative!")); return true }
        if (min >= max) { player.sendMessage(color("&cMinimum price must be less than maximum price!")); return true }
        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) { player.sendMessage(color("&cYou must be holding an item to set its price range.")); return true }
        val identifier = ItemIdentifier.getItemIdentifier(item) ?: run { player.sendMessage(color("&cCould not identify the item you are holding.")); return true }
        val config = main.configManager.randomPricesConfig
        config.set("$identifier.min_price", min); config.set("$identifier.max_price", max); config.set("$identifier.last_updated", System.currentTimeMillis()); config.set("$identifier.set_by", player.name); config.set("$identifier.item_type", ItemIdentifier.getItemType(item).name)
        main.configManager.saveConfig("random-prices")
        player.sendMessage(color("&aRandom price range for &e${ItemIdentifier.getItemDisplayName(item)} &aset to &e$${"%.2f".format(min)} &ato &e$${"%.2f".format(max)}"))
        (main.getGUIManager().getActivePriceEvaluationGUI(player) as? PriceEvaluationGUI)?.setRandomPrice(min, max)
        return true
    }

    private fun handleHelpCommand(sender: CommandSender): Boolean {
        listOf("&6&l=== SellGUI Help ====", "", "&e/sellgui &7- Open the sell GUI", "&e/sellgui <menu> &7- Open a configured sell menu", "&e/sellgui help &7- Show this help message", "&7Menus: &f${SellMenuConfig.getMenuIds(main).joinToString(", ")}").forEach { sender.sendMessage(color(it)) }
        if (sender.hasPermission("sellgui.evaluate")) sender.sendMessage(color("&e/sellgui evaluate &7- Open the Price Evaluation GUI."))
        if (sender.hasPermission("sellgui.setprice")) sender.sendMessage(color("&e/sellgui setprice <amount> &7- Set fixed price in Evaluation GUI."))
        if (sender.hasPermission("sellgui.setrange")) sender.sendMessage(color("&e/sellgui setrange <min> <max> &7- Set random range in Evaluation GUI."))
        if (sender.hasPermission("sellgui.autosell")) sender.sendMessage(color("&e/sellgui autosell &7- Open the autosell settings menu."))
        if (sender.hasPermission("sellgui.reload")) sender.sendMessage(color("&c/sellgui reload &7- Reload plugin configuration."))
        sender.sendMessage(color("&6&l=================="))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String> {
        val completions = arrayListOf<String>()
        when (args.size) {
            1 -> {
                if (sender.hasPermission("sellgui.reload")) completions += "reload"
                if (sender.hasPermission("sellgui.evaluate")) completions += "evaluate"
                if (sender.hasPermission("sellgui.setprice")) completions += "setprice"
                if (sender.hasPermission("sellgui.setrange")) completions += "setrange"
                if (sender.hasPermission("sellgui.autosell")) completions += "autosell"
                if (sender is Player && sender.hasPermission("sellgui.use")) completions += SellMenuConfig.getMenuIds(main)
                completions += "help"
                return completions.filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> if (args[0].equals("setprice", true) || args[0].equals("setrange", true)) return listOf("0", "10", "100").filter { it.startsWith(args[1]) }
            3 -> if (args[0].equals("setrange", true)) return listOf("10", "100", "1000").filter { it.startsWith(args[2]) }
        }
        return completions
    }

    private fun color(value: String): String = ColorUtils.color(value).orEmpty()

    companion object {
        private var sellGUIS = arrayListOf<SellGUI>()
        @JvmStatic fun getSellGUIs(): ArrayList<SellGUI> = sellGUIS
        @JvmStatic fun isSellGUI(inventory: Inventory): Boolean = sellGUIS.any { it.getMenu() == inventory }
        @JvmStatic fun getSellGUI(player: Player): SellGUI? = sellGUIS.firstOrNull { it.getPlayer() == player }
        @JvmStatic fun openSellGUI(player: Player): Boolean = sellGUIS.any { it.getPlayer() == player }
    }
}
