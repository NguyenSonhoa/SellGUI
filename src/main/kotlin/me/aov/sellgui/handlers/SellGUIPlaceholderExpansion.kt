package me.aov.sellgui.handlers

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.managers.PriceManager
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.math.BigDecimal
import java.math.RoundingMode

class SellGUIPlaceholderExpansion(private val main: SellGUIMain) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "sellgui"
    override fun getAuthor(): String = "SaneNuyan"
    override fun getVersion(): String = "3.1.9"
    override fun canRegister(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, identifier: String): String? {
        player ?: return ""
        val item = player.inventory.itemInMainHand
        return when (identifier) {
            "pricehand" -> getPrice(item, player).toString()
            "pricehandfull" -> "${if (item.hasItemMeta() && item.itemMeta.hasDisplayName()) item.itemMeta.displayName else item.type.name} - ${getPrice(item, player)}"
            else -> null
        }
    }

    fun getPrice(itemStack: ItemStack?, player: Player?): Double {
        if (itemStack == null || itemStack.type == Material.AIR) return 0.0
        var price = if (itemStack.hasItemMeta()) itemStack.itemMeta.persistentDataContainer.get(NamespacedKey(main, "current_price"), PersistentDataType.DOUBLE) ?: 0.0 else 0.0
        if (price == 0.0) {
            price = PriceManager(main).getItemPrice(itemStack)
            if (price == 0.0 && main.hasEssentials() && main.config.getBoolean("use-essentials-price")) {
                val essentialsPrice = main.essentialsHolder?.getPrice(itemStack)
                if (essentialsPrice != null) price = round(essentialsPrice.toDouble(), main.config.getInt("places-to-round", 2))
            }
            if (price == 0.0 && main.itemPricesConfig?.contains(itemStack.type.name) == true) price = main.itemPricesConfig!!.getDouble(itemStack.type.name)
        }
        return if (price > 0) applyPlayerBonuses(price, player) else round(price, main.config.getInt("places-to-round", 2))
    }

    private fun applyPlayerBonuses(initialPrice: Double, player: Player?): Double {
        var price = initialPrice
        if (player == null || price <= 0) return round(price, main.config.getInt("places-to-round", 2))
        player.effectivePermissions.filter { it.value }.forEach { permission ->
            when {
                permission.permission.startsWith("sellgui.bonus.") -> permission.permission.removePrefix("sellgui.bonus.").toDoubleOrNull()?.let { price += it }
                permission.permission.startsWith("sellgui.multiplier.") -> permission.permission.removePrefix("sellgui.multiplier.").toDoubleOrNull()?.takeIf { it > 0 }?.let { price *= it }
            }
        }
        return round(price, main.config.getInt("places-to-round", 2))
    }

    private fun round(value: Double, places: Int): Double {
        if (places < 0 || value.isNaN() || value.isInfinite()) return if (value.isNaN() || value.isInfinite()) 0.0 else value
        return try { BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).toDouble() } catch (_: NumberFormatException) { value }
    }
}
