package me.aov.sellgui.commands

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.api.SellGUIItemsSoldEvent
import me.aov.sellgui.api.SoldItem
import me.aov.sellgui.gui.SellMenuConfig
import me.aov.sellgui.handlers.SoundHandler
import me.aov.sellgui.utils.ItemIdentifier
import me.aov.sellgui.utils.TransactionLogger
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class SellAllCommand(private val main: SellGUIMain) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player ?: run { sender.sendMessage(color("&cThis command can only be used by players!")); return true }
        if (!player.hasPermission("sellgui.sellall")) { player.sendMessage(color(main.getMessagesConfig().getString("general.no_permission", "&c❌ You don't have permission to use this command!").orEmpty())); return true }
        if (main.getEcon() == null) { player.sendMessage(color("&cEconomy system not available!")); return true }
        main.getPriceCache()?.clearCache()
        val total = getTotal(player.inventory, player)
        val evaluationRequired = player.inventory.contents.filterNotNull().filter { it.type != Material.AIR }.sumOf { item -> if (main.getRandomPriceManager()?.requiresEvaluation(item) == true && main.getRandomPriceManager()?.isEvaluated(item) == false) item.amount else 0 }
        if (evaluationRequired > 0) player.sendMessage(color(main.getMessagesConfig().getString("sellall.evaluation_required", "&e⚠️ %count% items require evaluation before selling. Use /sellgui evaluate").orEmpty().replace("%count%", evaluationRequired.toString())))
        if (main.config.getBoolean("general.debug", false)) debug(player, total, evaluationRequired)
        if (total <= 0) { player.sendMessage(color(main.getMessagesConfig().getString("sellall.no-items", "&c❌ No items to sell in your inventory!").orEmpty())); return true }
        if (args.size == 1 && args[0].equals("confirm", true)) sellItems(player.inventory, player) else showConfirmation(player, total)
        return true
    }

    private fun debug(player: Player, total: Double, evaluationRequired: Int) {
        main.logger.info("SellAll debug - Player: ${player.name}, Total: $$total"); main.logger.info("  Items needing evaluation: $evaluationRequired")
        val count = player.inventory.contents.filterNotNull().count { it.type != Material.AIR && getPrice(it, player) > 0 }
        main.logger.info("  Total sellable items: $count")
    }

    private fun showConfirmation(player: Player, total: Double) {
        val messages = main.getMessagesConfig()
        player.sendMessage(color(messages.getString("sellall.section-confirm", "&6&l=== SellAll Confirmation === ").orEmpty()))
        player.sendMessage(color(messages.getString("sellall.confirm-message", "&e⚠️ You will receive &a$%total% &efor selling all items.").orEmpty().replace("%total%", total.format())))
        player.sendMessage("")
        if (!main.config.getBoolean("sellall-show-preview", true)) return
        player.sendMessage(color(messages.getString("sellall.item-to-be-sold", "&7Items to be sold:").orEmpty())); player.sendMessage(" ")
        var shown = 0
        player.inventory.contents.filterNotNull().filter { it.type != Material.AIR && getPrice(it, player) > 0 }.take(5).forEach { item ->
            val amount = getPrice(item, player) * item.amount
            val line = messages.getString("sellall.sell-all-format", "&8- &f%item_name% &7x%item_amount% &8= &e$%price%").orEmpty()
                .replace("%item_name%", ItemIdentifier.getItemDisplayName(item)).replace("%item_amount%", item.amount.toString()).replace("%price%", amount.format())
            player.sendMessage(color(line)); player.sendMessage(""); shown++
        }
        if (shown >= 5) { player.sendMessage(color(messages.getString("sellall.preview-more", "&8... and more").orEmpty())); player.sendMessage("") }
        val separator = messages.getString("sellall.separator", "&6&l=========================").orEmpty()
        player.sendMessage(color(separator)); player.sendMessage(color(messages.getString("sellall.confirm-proceed", "&a&l✓ &f/sellall confirm &7- Proceed with sale").orEmpty())); player.sendMessage(color(messages.getString("sellall.confirm-cancel", "&c&l✗ &7Any other action - Cancel").orEmpty())); player.sendMessage(color(separator))
    }

    fun getPrice(item: ItemStack?, player: Player): Double {
        if (item == null || item.type == Material.AIR) return 0.0
        if (!isShulkerBox(item)) return basePrice(item, player)
        val contents = ((item.itemMeta as? org.bukkit.inventory.meta.BlockStateMeta)?.blockState as? org.bukkit.block.ShulkerBox)?.inventory?.let { getTotal(it, player) } ?: 0.0
        return basePrice(item, player) + contents
    }

    private fun basePrice(item: ItemStack, player: Player): Double {
        if (SellMenuConfig.isExclusiveToAnyMenu(main, item) || main.getRandomPriceManager()?.canBeSold(item) == false) return 0.0
        if (!main.config.getBoolean("sell-all-command-sell-enchanted") && item.enchantments.isNotEmpty()) return 0.0
        val manager = main.getPriceManager()
        var price = try { manager?.getItemPriceWithPlayer(item, player) ?: 0.0 } catch (exception: Exception) { main.logger.warning("SellAll price lookup failed: ${exception.message}"); 0.0 }
        if (manager != null) return price
        if (price <= 0) price = main.getNBTPriceManager()?.getPriceFromNBT(item) ?: 0.0
        if (price <= 0) price = main.getRandomPriceManager()?.getRandomPrice(item) ?: 0.0
        if (price <= 0 && main.hasEssentials() && main.config.getBoolean("use-essentials-price")) main.essentialsHolder?.getPrice(item)?.let { price = it.toDouble() }
        if (price <= 0) ItemIdentifier.getItemIdentifier(item)?.takeIf { !it.startsWith("VANILLA:") && main.itemPricesConfig.contains(it) }?.let { price = main.itemPricesConfig.getDouble(it) }
        if (price <= 0 && main.itemPricesConfig.contains(item.type.name)) price = main.itemPricesConfig.getDouble(item.type.name)
        if (price > 0) price = applyBonuses(item, player, price)
        return price
    }

    private fun applyBonuses(item: ItemStack, player: Player, initial: Double): Double {
        var price = initial
        val config = main.itemPricesConfig
        item.enchantments.forEach { (enchantment, _) ->
            val level = item.getEnchantmentLevel(enchantment).toString()
            config.getStringList("flat-enchantment-bonus").forEach { entry -> entry.split(":").takeIf { it.size >= 3 && it[0].equals(enchantment.key.key, true) && it[1].equals(level, true) }?.get(2)?.toDoubleOrNull()?.let { price += it } }
            config.getStringList("multiplier-enchantment-bonus").forEach { entry -> entry.split(":").takeIf { it.size >= 3 && it[0].equals(enchantment.key.key, true) && it[1].equals(level, true) }?.get(2)?.toDoubleOrNull()?.let { price *= it } }
        }
        player.effectivePermissions.filter { it.permission.startsWith("sellgui.bonus.") }.forEach { info -> info.permission.removePrefix("sellgui.bonus.").toDoubleOrNull()?.let { price += it } }
        return price
    }

    private fun isShulkerBox(item: ItemStack?): Boolean = item?.type?.name?.endsWith("SHULKER_BOX") == true
    fun getTotal(inventory: Inventory, player: Player): Double = inventory.contents.filterNotNull().filter { it.type != Material.AIR }.sumOf { item -> getPrice(item, player).let { if (it > 0) if (isShulkerBox(item)) it else it * item.amount else 0.0 } }

    fun sellItems(inventory: Inventory, player: Player) {
        val pendingSales = arrayListOf<Pair<Int, SoldItem>>()
        for (slot in 0 until inventory.size) {
            val item = inventory.getItem(slot) ?: continue
            val unitPrice = getPrice(item, player)
            if (!unitPrice.isFinite() || unitPrice <= 0) continue
            pendingSales += slot to SoldItem(item, item.amount, unitPrice, if (isShulkerBox(item)) unitPrice else unitPrice * item.amount)
        }
        val sold = pendingSales.map { it.second }
        val total = sold.sumOf { it.getTotalPrice() }
        if (!total.isFinite() || total <= 0) { player.sendMessage(color("&cNo items to sell!")); return }
        val response = main.getEcon()?.depositPlayer(player, total)
        if (response?.transactionSuccess() != true) {
            player.sendMessage(color(main.getMessagesConfig().getString("sell.transaction_failed", "&cTransaction failed. Please try again.").orEmpty()))
            return
        }
        pendingSales.forEach { (slot, _) -> inventory.setItem(slot, null) }
        TransactionLogger.log(main, "SELLALL", player, sold)
        main.server.pluginManager.callEvent(SellGUIItemsSoldEvent(player, total))
        main.getSellGUIAPI().notifyItemsSold(player, sold, total)
        val count = sold.sumOf { it.getAmount() }
        player.sendMessage(color(main.getMessagesConfig().getString("sellall.sold-message", "&a✅ Sold %count% items for &e$%total%!").orEmpty().replace("%total%", total.format()).replace("%count%", count.toString())))
        try { SoundHandler.playSuccess(player) } catch (_: Exception) { }
    }

    fun logSellAll(item: ItemStack?, player: Player) {
        item ?: return
        val unitPrice = getPrice(item, player)
        TransactionLogger.log(main, "SELLALL", player, listOf(
            SoldItem(item, item.amount, unitPrice, if (isShulkerBox(item)) unitPrice else unitPrice * item.amount),
        ))
    }

    private fun color(value: String): String = ChatColor.translateAlternateColorCodes('&', value)
    private fun Double.format(): String = "%.2f".format(this)
}
