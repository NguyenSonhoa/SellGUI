package me.aov.sellgui.managers

import me.aov.sellgui.SellGUIMain
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.Random

class RandomPriceManager(private val plugin: SellGUIMain) {
    private val random = Random()
    fun setRandomPriceRange(item: ItemStack?, minPrice: Double, maxPrice: Double, playerName: String) {
        item ?: return; val itemKey = getItemKey(item); val config = randomPricesConfig ?: return
        config.set("prices.$itemKey.min_price", minPrice); config.set("prices.$itemKey.max_price", maxPrice); config.set("prices.$itemKey.last_updated", System.currentTimeMillis()); config.set("prices.$itemKey.set_by", playerName); config.set("prices.$itemKey.item_type", getItemType(item)); saveConfig()
        plugin.logger.info("Set random price range for $itemKey: $$minPrice - $$maxPrice")
    }
    fun getRandomPrice(item: ItemStack?): Double { item ?: return 0.0; val config = randomPricesConfig ?: return 0.0; val path = "prices.${getItemKey(item)}"; if (!config.contains(path)) return 0.0; val min = config.getDouble("$path.min_price", 0.0); val max = config.getDouble("$path.max_price", 0.0); return if (min >= max) min else min + random.nextDouble() * (max - min) }
    fun getPriceRange(item: ItemStack?): DoubleArray? { item ?: return null; val config = randomPricesConfig ?: return null; val path = "prices.${getItemKey(item)}"; return if (config.contains(path)) doubleArrayOf(config.getDouble("$path.min_price", 0.0), config.getDouble("$path.max_price", 0.0)) else null }
    fun hasRandomPrice(item: ItemStack?): Boolean = item != null && randomPricesConfig?.contains("prices.${getItemKey(item)}") == true
    fun removeRandomPrice(item: ItemStack?) { item ?: return; randomPricesConfig?.set("prices.${getItemKey(item)}", null); saveConfig(); plugin.logger.info("Removed random price range for ${getItemKey(item)}") }
    private fun getItemKey(item: ItemStack): String = if (hasNexo()) getNexoId(item)?.let { "nexo:$it" } ?: item.type.name else item.type.name
    private fun getItemType(item: ItemStack): String = if (hasNexo() && getNexoId(item) != null) "NEXO" else "VANILLA"
    private fun hasNexo(): Boolean = try { plugin.server.pluginManager.getPlugin("Nexo") != null } catch (_: Exception) { false }
    private fun getNexoId(item: ItemStack): String? = try { Class.forName("com.nexomc.nexo.api.NexoItems").getMethod("idFromItem", ItemStack::class.java).invoke(null, item)?.toString() } catch (_: Exception) { null }
    private val randomPricesConfig get() = plugin.configManager?.getConfig("random-prices")
    private fun saveConfig() { plugin.configManager?.saveConfig("random-prices") }
    fun cleanupOldEntries() { val config = randomPricesConfig ?: return; if (!config.getBoolean("config.auto_cleanup", true)) return; val cutoff = System.currentTimeMillis() - config.getInt("config.cleanup_days", 30) * 86_400_000L; config.getConfigurationSection("prices")?.getKeys(false)?.forEach { key -> if (config.getLong("prices.$key.last_updated", 0) < cutoff) { config.set("prices.$key", null); plugin.logger.info("Cleaned up old random price entry: $key") } }; saveConfig() }
    fun getStoredPricesCount(): Int = randomPricesConfig?.getConfigurationSection("prices")?.getKeys(false)?.size ?: 0
    fun requiresEvaluation(item: ItemStack?): Boolean = hasRandomPrice(item)
    fun isEvaluated(item: ItemStack?): Boolean {
        if (item == null || !item.hasItemMeta()) return false
        val container = item.itemMeta.persistentDataContainer
        val hasPrice = container.has(NamespacedKey(plugin, "worth"), PersistentDataType.DOUBLE) || (container.get(NamespacedKey(plugin, "current_price"), PersistentDataType.DOUBLE) ?: 0.0) > 0 || container.has(NamespacedKey(plugin, "evaluated"), PersistentDataType.BYTE)
        return hasPrice || (plugin.getNBTPriceManager()?.getPriceFromNBT(item) ?: 0.0) > 0
    }
    fun canBeSold(item: ItemStack?): Boolean = !requiresEvaluation(item) || isEvaluated(item)
}
