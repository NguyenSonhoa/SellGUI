package me.aov.sellgui.managers

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.utils.ItemIdentifier
import net.brcdev.shopgui.ShopGuiPlusApi
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.io.File
import java.io.IOException
import java.util.UUID

class PriceManager(private val main: SellGUIMain) {
    private val customItemPrices = hashMapOf<ItemStack, Double>()
    init { loadCustomItemPrices() }
    fun loadCustomItemPrices() {
        customItemPrices.clear(); main.getCustomItemsConfig().getConfigurationSection("custom-items")?.getKeys(false)?.forEach { key ->
            main.getCustomItemsConfig().getItemStack("custom-items.$key.item")?.let { item -> main.getCustomItemsConfig().getDouble("custom-items.$key.price").takeIf { it > 0 }?.let { customItemPrices[item] = it } }
        }
    }
    private fun shopGUIPlusPrice(item: ItemStack, player: Player?): Double {
        if (!main.hasShopGUIPlus) return 0.0
        return try { val price = ShopGuiPlusApi.getItemStackPriceSell(player, item).takeIf { it > 0 } ?: ShopGuiPlusApi.getItemStackPriceSell(item); if (price > 0) price / item.amount else 0.0 } catch (throwable: Throwable) { if (main.config.getBoolean("general.debug", false)) main.logger.warning("Error getting ShopGUI+ price: ${throwable.message}"); 0.0 }
    }
    fun setItemPrice(item: ItemStack?, price: Double): Boolean {
        item ?: return false
        return try { when (ItemIdentifier.getItemType(item)) { ItemIdentifier.ItemType.MMOITEMS -> setMMOItemPrice(item, price); ItemIdentifier.ItemType.NEXO -> setNexoPrice(item, price); ItemIdentifier.ItemType.VANILLA -> setVanillaPrice(item, price); else -> false } } catch (exception: Exception) { main.logger.warning("Failed to set price for item: ${ItemIdentifier.getItemIdentifier(item)} - ${exception.message}"); false }
    }
    fun getItemPrice(item: ItemStack?): Double {
        item ?: return 0.0
        item.itemMeta?.persistentDataContainer?.get(NamespacedKey(main, "worth"), PersistentDataType.DOUBLE)?.takeIf { main.config.getBoolean("prices.nbt-pricing", true) && it > 0 }?.let { return it }
        val method = main.config.getString("prices.calculation-method", "auto").orEmpty()
        if (!method.equals("auto", true)) return randomVariation(specificMethodPrice(item, method, null))
        main.getRandomPriceManager()?.takeIf { it.hasRandomPrice(item) }?.getRandomPrice(item)?.takeIf { it > 0 }?.let { return it }
        main.getNBTPriceManager()?.getSellPrice(item)?.takeIf { it > 0 }?.let { return it }
        customItemPrice(item).takeIf { it > 0 }?.let { return randomVariation(it) }
        if (main.hasEssentials()) essentialsPrice(item).takeIf { it > 0 }?.let { return randomVariation(it) }
        return randomVariation(configPrice(item))
    }
    fun getPrice(identifier: String?): Double {
        identifier ?: return 0.0
        return when (ItemIdentifier.getItemTypeFromString(identifier)) {
            ItemIdentifier.ItemType.MMOITEMS -> main.getLoadedMMOItemPrices()[identifier.removePrefix("MMOITEMS:")] ?: 0.0
            ItemIdentifier.ItemType.NEXO -> main.getLoadedNexoPrices()[identifier.removePrefix("NEXO:")] ?: 0.0
            ItemIdentifier.ItemType.VANILLA -> main.itemPricesConfig.getDouble(identifier.removePrefix("VANILLA:"), 0.0)
            else -> 0.0
        }
    }
    fun hasPrice(identifier: String?): Boolean = getPrice(identifier) > 0
    fun getItemPriceWithPlayer(item: ItemStack?, player: Player?): Double {
        item ?: return 0.0
        val method = main.config.getString("prices.calculation-method", "auto").orEmpty()
        if (method.equals("addon", true) || method.equals("addons", true) || method.equals("external", true)) return addonPrice(item, player).takeIf { it > 0 }?.let { applyPlayerMultiplier(it, player) } ?: 0.0
        if (method.equals("shopguiplus", true)) return (addonPrice(item, player).takeIf { it > 0 } ?: specificMethodPrice(item, "shopguiplus", player)).coerceAtLeast(0.0)
        addonPrice(item, player).takeIf { it > 0 }?.let { return applyPlayerMultiplier(it, player) }
        var base = getItemPrice(item)
        if (base == 0.0 && player != null && main.config.getBoolean("use-shopguiplus-price") && main.hasShopGUIPlus) base = shopGUIPlusPrice(item, player)
        return if (base <= 0 || player == null) base else applyPlayerMultiplier(base, player)
    }
    private fun specificMethodPrice(item: ItemStack, method: String, player: Player?): Double = when (method.lowercase()) { "addon", "addons", "external" -> addonPrice(item, player); "config" -> configPrice(item); "essentials" -> essentialsPrice(item); "nbt" -> main.getNBTPriceManager()?.getSellPrice(item) ?: 0.0; "shopguiplus" -> shopGUIPlusPrice(item, player); else -> 0.0 }
    private fun addonPrice(item: ItemStack, player: Player?): Double = main.getSellGUIAPI()?.getProviderPrice(item, player) ?: 0.0
    private fun applyPlayerMultiplier(price: Double, player: Player?): Double = if (price <= 0 || player == null) price else price * getPlayerMultiplier(player)
    private fun configPrice(item: ItemStack): Double = when (ItemIdentifier.getItemType(item)) { ItemIdentifier.ItemType.MMOITEMS -> mmoPrice(item); ItemIdentifier.ItemType.NEXO -> nexoPrice(item); ItemIdentifier.ItemType.VANILLA -> vanillaPrice(item); else -> main.config.getDouble("prices.default-price", 0.0) }
    private fun essentialsPrice(item: ItemStack): Double = if (main.hasEssentials()) main.essentialsHolder?.getPrice(item)?.toDouble() ?: 0.0 else 0.0
    fun getPlayerMultiplier(player: Player?): Double {
        player ?: return 1.0; if (!main.config.getBoolean("prices.multipliers.permission-based", true)) return main.config.getDouble("prices.multipliers.default-multiplier", 1.0)
        val max = main.config.getDouble("prices.multipliers.max-multiplier", 5.0); var multiplier = 1.0; var test = .1
        while (test <= max) { if (player.hasPermission("sellgui.multiplier.${"%.1f".format(test).replace(',', '.')}")) multiplier = maxOf(multiplier, test); test += .1 }
        return minOf(multiplier, max)
    }
    private fun randomVariation(price: Double): Double = if (!main.config.getBoolean("prices.random-pricing.enabled", false)) price else price * (1 + (Math.random() - .5) * 2 * main.config.getDouble("prices.random-pricing.variation-percent", 10.0) / 100)
    fun removeItemPrice(item: ItemStack?): Boolean = setItemPrice(item, 0.0)
    private fun setVanillaPrice(item: ItemStack, price: Double): Boolean {
        if (item.hasItemMeta() && (item.itemMeta.hasDisplayName() || item.itemMeta.hasLore())) { try { main.getNBTPriceManager()?.setSellPrice(item, price) } catch (exception: Exception) { main.logger.warning("Failed to save custom item price to NBT: ${exception.message}") }; saveCustomItemToFile(item, price); return true }
        return try { main.itemPricesConfig.set(item.type.name, price); main.itemPricesConfig.save(itemPricesFile()); true } catch (exception: IOException) { main.logger.warning("Failed to save vanilla item price: ${exception.message}"); false }
    }
    private fun saveCustomItemToFile(item: ItemStack, price: Double) {
        val config = main.getCustomItemsConfig(); val section = config.getConfigurationSection("custom-items") ?: config.createSection("custom-items")
        val key = section.getKeys(false).firstOrNull { similarCustomItem(item, section.getItemStack("$it.item")) } ?: UUID.randomUUID().toString()
        config.set("custom-items.$key.item", item.clone().also { it.amount = 1 }); config.set("custom-items.$key.price", price)
        try { config.save(File(main.dataFolder, "customitems.yml")) } catch (exception: IOException) { main.logger.warning("Failed to save customitems.yml: ${exception.message}") }; loadCustomItemPrices()
    }
    private fun itemPricesFile() = File(main.dataFolder, "itemprices.yml")
    private fun vanillaPrice(item: ItemStack): Double = customItemPrice(item).takeIf { it > 0 } ?: main.itemPricesConfig.getDouble(if (item.type.name.endsWith("SHULKER_BOX")) "SHULKER_BOX" else item.type.name, 0.0)
    private fun similarCustomItem(first: ItemStack?, second: ItemStack?): Boolean = first != null && second != null && first.type == second.type && first.isSimilar(second)
    private fun customItemPrice(item: ItemStack): Double = customItemPrices.entries.firstOrNull { similarCustomItem(item, it.key) }?.value ?: 0.0
    private fun setMMOItemPrice(item: ItemStack, price: Double): Boolean = try { val id = ItemIdentifier.getItemIdentifier(item)?.removePrefix("MMOITEMS:") ?: return false; val parts = id.split('.', limit = 2); if (parts.size != 2) return false; main.getMMOItemsPricesFileConfig().set("mmoitems.${parts[0]}.${parts[1]}", price); main.getMMOItemsPricesFileConfig().save(File(main.dataFolder, "mmoitems.yml")); main.getLoadedMMOItemPrices()[id] = price; true } catch (exception: IOException) { main.logger.warning("Failed to save MMOItem price: ${exception.message}"); false }
    private fun mmoPrice(item: ItemStack): Double = ItemIdentifier.getItemIdentifier(item)?.removePrefix("MMOITEMS:")?.let { main.getLoadedMMOItemPrices()[it] } ?: 0.0
    private fun setNexoPrice(item: ItemStack, price: Double): Boolean = try { val id = ItemIdentifier.getItemIdentifier(item)?.removePrefix("NEXO:") ?: return false; main.getNexoPricesFileConfig().set("nexo.$id", price); main.getNexoPricesFileConfig().save(File(main.dataFolder, "nexo.yml")); main.getLoadedNexoPrices()[id] = price; true } catch (exception: IOException) { main.logger.warning("Failed to save Nexo item price: ${exception.message}"); false }
    private fun nexoPrice(item: ItemStack): Double = ItemIdentifier.getItemIdentifier(item)?.removePrefix("NEXO:")?.let { main.getLoadedNexoPrices()[it] } ?: 0.0
    fun getAllPricesForType(type: ItemIdentifier.ItemType): Map<String, Double> = when (type) {
        ItemIdentifier.ItemType.MMOITEMS -> main.getLoadedMMOItemPrices().filterValues { it > 0 }.mapKeys { "MMOITEMS:${it.key}" }
        ItemIdentifier.ItemType.NEXO -> main.getNexoPricesFileConfig().getConfigurationSection("nexo")?.getKeys(false)?.associate { key -> "NEXO:$key" to main.getNexoPricesFileConfig().getDouble("nexo.$key") }?.filterValues { it > 0 } ?: emptyMap()
        ItemIdentifier.ItemType.VANILLA -> main.itemPricesConfig.getKeys(false).filter { it != "flat-enchantment-bonus" && it != "multiplier-enchantment-bonus" }.associate { key -> "VANILLA:$key" to main.itemPricesConfig.getDouble(key) }.filterValues { it > 0 }
        else -> emptyMap()
    }
    fun getAllPricedItems(): Map<String, Double> = ItemIdentifier.ItemType.entries.flatMap { getAllPricesForType(it).entries }.associate { it.toPair() }
}
