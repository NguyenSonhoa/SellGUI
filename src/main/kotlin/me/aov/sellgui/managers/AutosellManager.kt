package me.aov.sellgui.managers

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.api.SellGUIItemsSoldEvent
import me.aov.sellgui.api.SoldItem
import me.aov.sellgui.gui.SellMenuConfig
import me.aov.sellgui.utils.ColorUtils
import me.aov.sellgui.utils.ItemIdentifier
import me.aov.sellgui.utils.TransactionLogger
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.io.IOException
import java.util.UUID

class AutosellManager(private val plugin: SellGUIMain, private val priceManager: PriceManager) {
    private val autosellTasks = HashMap<UUID, BukkitTask>()
    private val playerAutosellToggles = HashMap<UUID, MutableMap<String, Boolean>>()
    private val globalAutosellStatus = HashMap<UUID, Boolean>()
    private val autosellDataFile = File(plugin.dataFolder, "autosell_data.yml")
    private var autosellDataConfig: org.bukkit.configuration.file.FileConfiguration

    init {
        if (!autosellDataFile.exists()) try { autosellDataFile.createNewFile() } catch (exception: IOException) { plugin.logger.severe("Could not create autosell_data.yml! ${exception.message}") }
        autosellDataConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(autosellDataFile)
    }

    private fun saveAutosellData() = try { autosellDataConfig.save(autosellDataFile) } catch (exception: IOException) { plugin.logger.severe("Could not save autosell_data.yml! ${exception.message}") }
    fun startAutosellTask(player: Player) {
        val uuid = player.uniqueId
        if (autosellTasks.containsKey(uuid)) return
        loadPlayerAutosellData(uuid)
        autosellTasks[uuid] = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable { if (!player.isOnline) stopAutosellTask(player) else processAutosell(player) }, AUTOSELL_DELAY_TICKS, AUTOSELL_DELAY_TICKS)
    }
    fun stopAutosellTask(player: Player) { val uuid = player.uniqueId; autosellTasks.remove(uuid)?.cancel(); savePlayerAutosellData(uuid); playerAutosellToggles.remove(uuid); globalAutosellStatus.remove(uuid) }
    private fun loadPlayerAutosellData(uuid: UUID) {
        globalAutosellStatus[uuid] = autosellDataConfig.getBoolean("$uuid.global_autosell", false)
        playerAutosellToggles[uuid] = autosellDataConfig.getConfigurationSection("$uuid.item_toggles")?.getKeys(false)?.associateWithTo(HashMap()) { autosellDataConfig.getBoolean("$uuid.item_toggles.$it") } ?: HashMap()
    }
    private fun savePlayerAutosellData(uuid: UUID) {
        autosellDataConfig.set("$uuid.global_autosell", globalAutosellStatus[uuid] ?: false)
        playerAutosellToggles[uuid]?.forEach { (key, enabled) -> autosellDataConfig.set("$uuid.item_toggles.$key", enabled) } ?: autosellDataConfig.set("$uuid.item_toggles", null)
        saveAutosellData()
    }

    fun processAutosell(player: Player) {
        if (!isGlobalAutosellEnabled(player.uniqueId)) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline || !isGlobalAutosellEnabled(player.uniqueId)) return@Runnable
            val pendingSales = arrayListOf<Pair<Int, SoldItem>>()
            var rawTotal = 0.0
            val soldSummary = HashMap<String, Int>()
            val configOnly = plugin.config.getString("prices.calculation-method", "auto").equals("config", true)
            for (slot in 0 until player.inventory.size) {
                val item = player.inventory.getItem(slot) ?: continue
                if (item.type.isAir || SellMenuConfig.isExclusiveToAnyMenu(plugin, item)) continue
                val identifier = ItemIdentifier.getItemIdentifier(item) ?: continue
                if ((configOnly && !priceManager.hasPrice(identifier)) || !isAutosellEnabled(player.uniqueId, identifier)) continue
                val pricePerItem = priceManager.getItemPriceWithPlayer(item, player)
                if (!pricePerItem.isFinite() || pricePerItem <= 0) continue
                val amount = item.amount
                pendingSales += slot to SoldItem(item, amount, pricePerItem, pricePerItem * amount)
                rawTotal += priceManager.getItemPrice(item) * amount
                soldSummary.merge(ItemIdentifier.getItemDisplayName(item), amount, Int::plus)
            }
            val soldItems = pendingSales.map { it.second }
            val totalEarned = soldItems.sumOf { it.getTotalPrice() }
            if (!totalEarned.isFinite() || totalEarned <= 0) return@Runnable
            val response = plugin.getEcon()?.depositPlayer(player, totalEarned)
            if (response?.transactionSuccess() != true) {
                player.sendMessage(ColorUtils.color(plugin.getMessagesConfig().getString(
                    "sell.transaction_failed", "&cTransaction failed. Please try again.",
                )).orEmpty())
                return@Runnable
            }
            pendingSales.forEach { (slot, _) -> player.inventory.setItem(slot, null) }
            TransactionLogger.log(plugin, "AUTOSELL", player, soldItems)
            Bukkit.getPluginManager().callEvent(SellGUIItemsSoldEvent(player, totalEarned))
            plugin.getSellGUIAPI().notifyItemsSold(player, soldItems, totalEarned)
            val multiplier = if (rawTotal > 0) totalEarned / rawTotal else 1.0
            val bonusText = if (multiplier > 1.0001) " &7(x&a${"%.2f".format(multiplier)}&7)" else ""
            val message = plugin.configManager.messagesConfig.getString("autosell.sold", "Autosell: +$%amount%%bonus%") ?: "Autosell: +$%amount%%bonus%"
            player.sendMessage(ColorUtils.color(message.replace("%amount%", plugin.configManager.moneyFormat.format(totalEarned)).replace("%bonus%", bonusText)).orEmpty())
            if (plugin.config.getBoolean("autosell.show-item-summary", true)) soldSummary.forEach { (name, amount) -> player.sendMessage(ColorUtils.color(" &7- ${amount}x $name").orEmpty()) }
        })
    }

    fun isAutosellEnabled(playerUUID: UUID, itemIdentifier: String): Boolean = playerAutosellToggles[playerUUID]?.get(itemIdentifier) ?: false
    fun setAutosellEnabled(playerUUID: UUID, itemIdentifier: String, enabled: Boolean) { playerAutosellToggles.getOrPut(playerUUID) { HashMap() }[itemIdentifier] = enabled; savePlayerAutosellData(playerUUID) }
    fun isGlobalAutosellEnabled(playerUUID: UUID): Boolean = globalAutosellStatus[playerUUID] ?: false
    fun setGlobalAutosellEnabled(playerUUID: UUID, enabled: Boolean) { globalAutosellStatus[playerUUID] = enabled; savePlayerAutosellData(playerUUID) }
    fun shutdown() { autosellTasks.keys.toList().forEach { Bukkit.getPlayer(it)?.let(::stopAutosellTask) } }

    private companion object { const val AUTOSELL_DELAY_TICKS = 100L }
}
