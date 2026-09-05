package me.aov.sellgui

import me.aov.sellgui.api.SellGUIPriceProvider
import me.aov.sellgui.api.SoldItem
import me.aov.sellgui.commands.SellCommand
import me.aov.sellgui.managers.PriceManager
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.CopyOnWriteArrayList

class SellGUIAPI(private val main: SellGUIMain) {
    private val priceProviders = CopyOnWriteArrayList<SellGUIPriceProvider>()
    fun registerPriceProvider(provider: SellGUIPriceProvider) { priceProviders.removeIf { it === provider || it.getName().equals(provider.getName(), true) }; priceProviders += provider; priceProviders.sortByDescending { it.getPriority() }; clearPriceCache() }
    fun unregisterPriceProvider(provider: SellGUIPriceProvider?): Boolean { provider ?: return false; val removed = priceProviders.removeIf { it === provider || it.getName().equals(provider.getName(), true) }; if (removed) clearPriceCache(); return removed }
    fun getPriceProviders(): List<SellGUIPriceProvider> = priceProviders.toList()
    fun getProviderPrice(itemStack: ItemStack?, player: Player?): Double {
        if (itemStack == null || itemStack.type == Material.AIR) return 0.0
        return priceProviders.firstNotNullOfOrNull { provider -> try { provider.takeIf { it.isAvailable() }?.getSellPrice(player ?: return@firstNotNullOfOrNull null, itemStack.clone())?.takeIf { it > 0 && !it.isNaN() && !it.isInfinite() } } catch (throwable: Throwable) { if (main.config.getBoolean("general.debug", false)) main.logger.warning("Price provider '${provider.getName()}' failed: ${throwable.message}"); null } } ?: 0.0
    }
    fun notifyItemsSold(player: Player, soldItems: List<SoldItem>?, totalPrice: Double) { if (soldItems.isNullOrEmpty()) return; val immutable = soldItems.toList(); priceProviders.forEach { provider -> try { if (provider.isAvailable()) provider.onItemsSold(player, immutable, totalPrice) } catch (throwable: Throwable) { if (main.config.getBoolean("general.debug", false)) main.logger.warning("Price provider '${provider.getName()}' sell callback failed: ${throwable.message}") } }; clearPriceCache() }
    fun getPrice(itemStack: ItemStack?, player: Player?): Double {
        if (itemStack == null || itemStack.type == Material.AIR) return 0.0
        var price = if (itemStack.hasItemMeta()) itemStack.itemMeta.persistentDataContainer.get(NamespacedKey(main, "current_price"), PersistentDataType.DOUBLE) ?: 0.0 else 0.0
        if (price == 0.0) {
            price = (main.getPriceManager() ?: PriceManager(main)).getItemPriceWithPlayer(itemStack, player)
            if (price == 0.0 && main.hasEssentials() && main.config.getBoolean("use-essentials-price")) main.essentialsHolder?.getPrice(itemStack)?.let { price = round(it.toDouble(), main.config.getInt("places-to-round", 2)) }
            if (price == 0.0 && main.itemPricesConfig?.contains(itemStack.type.name) == true) price = main.itemPricesConfig!!.getDouble(itemStack.type.name)
        }
        return if (price > 0) applyPlayerBonuses(price, player) else round(price, main.config.getInt("places-to-round", 2))
    }
    private fun applyPlayerBonuses(initial: Double, player: Player?): Double { var price = initial; if (player == null || price <= 0) return round(price, main.config.getInt("places-to-round", 2)); player.effectivePermissions.filter { it.value }.forEach { info -> info.permission.removePrefix("sellgui.bonus.").takeIf { info.permission.startsWith("sellgui.bonus.") }?.toDoubleOrNull()?.let { price += it }; info.permission.removePrefix("sellgui.multiplier.").takeIf { info.permission.startsWith("sellgui.multiplier.") }?.toDoubleOrNull()?.takeIf { it > 0 }?.let { price *= it } }; return round(price, main.config.getInt("places-to-round", 2)) }
    fun getPurePrice(itemStack: ItemStack?): Double = if (itemStack != null && itemStack.type != Material.AIR && main.itemPricesConfig?.contains(itemStack.type.name) == true) main.itemPricesConfig!!.getDouble(itemStack.type.name) else 0.0
    fun openSellGUI(player: Player) { SellCommand.getSellGUIs().add(SellGUI(main, player, main.itemNBTManager)) }
    private fun round(value: Double, places: Int): Double = if (places < 0) value else if (value.isNaN() || value.isInfinite()) 0.0 else try { BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).toDouble() } catch (_: NumberFormatException) { value }
    private fun clearPriceCache() { main.getPriceCache()?.clearCache() }
}
