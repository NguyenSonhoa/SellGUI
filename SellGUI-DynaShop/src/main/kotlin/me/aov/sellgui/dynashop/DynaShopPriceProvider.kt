package me.aov.sellgui.dynashop

import me.aov.sellgui.api.SellGUIPriceProvider
import me.aov.sellgui.api.SoldItem
import net.brcdev.shopgui.ShopGuiPlusApi
import net.brcdev.shopgui.shop.ShopManager.ShopAction
import net.brcdev.shopgui.shop.item.ShopItem
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.util.HashMap
import java.util.HashSet
import java.util.Optional

internal class DynaShopPriceProvider(private val plugin: SellGUIDynaShop) : SellGUIPriceProvider {
    override fun getName(): String = DYNASHOP_PLUGIN
    override fun getPriority(): Int = 100
    override fun isAvailable(): Boolean = getDynaShopPlugin() != null && Bukkit.getPluginManager().isPluginEnabled("ShopGUIPlus")

    override fun getSellPrice(player: Player, itemStack: ItemStack): Double {
        val shopItem = findShopItem(player, itemStack) ?: return 0.0
        if (!isDynaShopItem(shopItem)) return 0.0
        val amount = maxOf(1, itemStack.amount)
        if (!canSell(shopItem, amount)) return 0.0
        val dynamicPrice = loadDynamicPrice(player, shopItem, itemStack) ?: return 0.0
        return try {
            val unitPrice = if (isProgressivePricing()) {
                val decay = (dynamicPrice.javaClass.getMethod("getDecaySell").invoke(dynamicPrice) as Number).toDouble()
                (dynamicPrice.javaClass.getMethod("calculateProgressiveAveragePrice", Int::class.javaPrimitiveType, Double::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    .invoke(dynamicPrice, amount, decay, false) as Number).toDouble()
            } else {
                (dynamicPrice.javaClass.getMethod("getSellPrice").invoke(dynamicPrice) as Number).toDouble()
            }
            if (unitPrice > 0 && !unitPrice.isNaN() && !unitPrice.isInfinite()) unitPrice else 0.0
        } catch (exception: ReflectiveOperationException) {
            debug("Failed to read DynaShop sell price: ${exception.message}")
            0.0
        } catch (exception: RuntimeException) {
            debug("Failed to read DynaShop sell price: ${exception.message}")
            0.0
        }
    }

    override fun onItemsSold(player: Player, soldItems: List<SoldItem>, totalPrice: Double) {
        soldItems.forEach { soldItem ->
            val itemStack = soldItem.getItemStack() ?: return@forEach
            val shopItem = findShopItem(player, itemStack) ?: return@forEach
            if (!isDynaShopItem(shopItem)) return@forEach
            val amount = maxOf(1, soldItem.getAmount())
            if (getSellPrice(player, itemStack) <= 0) return@forEach
            processSell(player, shopItem, itemStack, amount)
        }
    }

    private fun findShopItem(player: Player?, itemStack: ItemStack?): ShopItem? {
        itemStack ?: return null
        try {
            val shopItem = if (player != null) ShopGuiPlusApi.getItemStackShopItem(player, itemStack) else ShopGuiPlusApi.getItemStackShopItem(itemStack)
            if (shopItem != null) return shopItem
        } catch (_: Throwable) {
        }
        return try {
            ShopGuiPlusApi.getItemStackShopItem(itemStack)
        } catch (_: Throwable) {
            null
        }
    }

    private fun isDynaShopItem(shopItem: ShopItem): Boolean {
        val manager = getShopConfigManager() ?: return false
        val shopId = shopItem.shop.id
        val itemId = shopItem.id
        return hasTypeDynaShop(manager, shopId, itemId) ||
            invokeBoolean(manager, "hasDynamicSection", shopId, itemId) ||
            invokeBoolean(manager, "hasStockSection", shopId, itemId) ||
            invokeBoolean(manager, "hasRecipeSection", shopId, itemId)
    }

    private fun hasTypeDynaShop(manager: Any, shopId: String, itemId: String): Boolean = try {
        val result = manager.javaClass.getMethod("getItemValue", String::class.java, String::class.java, String::class.java, Class::class.java)
            .invoke(manager, shopId, itemId, "typeDynaShop", String::class.java)
        result is Optional<*> && result.isPresent
    } catch (exception: ReflectiveOperationException) {
        debug("Failed to check DynaShop item type: ${exception.message}")
        false
    } catch (exception: RuntimeException) {
        debug("Failed to check DynaShop item type: ${exception.message}")
        false
    }

    private fun canSell(shopItem: ShopItem, amount: Int): Boolean {
        val sellType = getDynaShopType(shopItem, "sell")
        if (sellType != "STOCK" && sellType != "STATIC_STOCK") return true
        val dynaShop = getDynaShopPlugin() ?: return false
        return try {
            val stock = dynaShop.javaClass.getMethod("getPriceStock").invoke(dynaShop)
            stock.javaClass.getMethod("canSell", String::class.java, String::class.java, Int::class.javaPrimitiveType)
                .invoke(stock, shopItem.shop.id, shopItem.id, amount) == true
        } catch (exception: ReflectiveOperationException) {
            debug("Failed to check DynaShop stock sell limit: ${exception.message}")
            true
        } catch (exception: RuntimeException) {
            debug("Failed to check DynaShop stock sell limit: ${exception.message}")
            true
        }
    }

    private fun getDynaShopType(shopItem: ShopItem, action: String): String {
        val manager = getShopConfigManager() ?: return "UNKNOWN"
        val shopId = shopItem.shop.id
        val itemId = shopItem.id
        try {
            manager.javaClass.getMethod("getRealTypeDynaShop", String::class.java, String::class.java, String::class.java)
                .invoke(manager, shopId, itemId, action)?.let { return it.toString() }
        } catch (_: ReflectiveOperationException) {
        } catch (_: RuntimeException) {
        }
        try {
            manager.javaClass.getMethod("getTypeDynaShop", String::class.java, String::class.java, String::class.java)
                .invoke(manager, shopId, itemId, action)?.toString()?.takeIf { it != "NONE" && it != "UNKNOWN" }?.let { return it }
        } catch (_: ReflectiveOperationException) {
        } catch (_: RuntimeException) {
        }
        return try {
            manager.javaClass.getMethod("getTypeDynaShop", String::class.java, String::class.java).invoke(manager, shopId, itemId)?.toString() ?: "UNKNOWN"
        } catch (_: ReflectiveOperationException) {
            "UNKNOWN"
        } catch (_: RuntimeException) {
            "UNKNOWN"
        }
    }

    private fun loadDynamicPrice(player: Player, shopItem: ShopItem, itemStack: ItemStack): Any? {
        val listener = getDynaShopListener() ?: return null
        return try {
            listener.javaClass.getMethod("getOrLoadPrice", Player::class.java, String::class.java, String::class.java, ItemStack::class.java, java.util.Set::class.java, java.util.Map::class.java)
                .invoke(listener, player, shopItem.shop.id, shopItem.id, itemStack, HashSet<String>(), HashMap<String, Any>())
        } catch (exception: ReflectiveOperationException) {
            debug("Failed to load DynaShop price: ${exception.message}")
            null
        } catch (exception: RuntimeException) {
            debug("Failed to load DynaShop price: ${exception.message}")
            null
        }
    }

    private fun processSell(player: Player, shopItem: ShopItem, itemStack: ItemStack, amount: Int) {
        val listener = getDynaShopListener() ?: return
        val shopId = shopItem.shop.id
        val itemId = shopItem.id
        val soldStack = itemStack.clone().also { it.amount = amount }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { invokeProcessTransaction(listener, shopId, itemId, soldStack, amount) })
        invokeUpdateStorage(listener, player, shopId, itemId, amount)
    }

    private fun invokeProcessTransaction(listener: Any, shopId: String, itemId: String, itemStack: ItemStack, amount: Int) {
        try {
            listener.javaClass.getDeclaredMethod("processTransactionAsync", String::class.java, String::class.java, ItemStack::class.java, Int::class.javaPrimitiveType, ShopAction::class.java)
                .also { it.isAccessible = true }
                .invoke(listener, shopId, itemId, itemStack, amount, ShopAction.SELL)
        } catch (exception: ReflectiveOperationException) {
            plugin.logger.warning("Failed to update DynaShop market transaction for $shopId:$itemId: ${exception.message}")
        } catch (exception: RuntimeException) {
            plugin.logger.warning("Failed to update DynaShop market transaction for $shopId:$itemId: ${exception.message}")
        }
    }

    private fun invokeUpdateStorage(listener: Any, player: Player, shopId: String, itemId: String, amount: Int) {
        try {
            listener.javaClass.getDeclaredMethod("updateStorageData", Player::class.java, String::class.java, String::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .also { it.isAccessible = true }
                .invoke(listener, player, shopId, itemId, false, amount)
        } catch (exception: ReflectiveOperationException) {
            debug("Failed to flush DynaShop storage update: ${exception.message}")
        } catch (exception: RuntimeException) {
            debug("Failed to flush DynaShop storage update: ${exception.message}")
        }
    }

    private fun isProgressivePricing(): Boolean {
        val dynaShop = getDynaShopPlugin() ?: return false
        return try {
            val config = dynaShop.javaClass.getMethod("getConfigMain").invoke(dynaShop)
            "progressive".equals(config.javaClass.getMethod("getString", String::class.java, String::class.java).invoke(config, "pricing.calculation-mode", "simple")?.toString(), ignoreCase = true)
        } catch (_: ReflectiveOperationException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun invokeBoolean(target: Any, methodName: String, shopId: String, itemId: String): Boolean = try {
        target.javaClass.getMethod(methodName, String::class.java, String::class.java).invoke(target, shopId, itemId) == true
    } catch (_: ReflectiveOperationException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    private fun getDynaShopListener(): Any? {
        val dynaShop = getDynaShopPlugin() ?: return null
        return try { dynaShop.javaClass.getMethod("getDynaShopListener").invoke(dynaShop) } catch (exception: ReflectiveOperationException) { debug("Failed to access DynaShop listener: ${exception.message}"); null } catch (exception: RuntimeException) { debug("Failed to access DynaShop listener: ${exception.message}"); null }
    }

    private fun getShopConfigManager(): Any? {
        val dynaShop = getDynaShopPlugin() ?: return null
        return try { dynaShop.javaClass.getMethod("getShopConfigManager").invoke(dynaShop) } catch (exception: ReflectiveOperationException) { debug("Failed to access DynaShop shop config manager: ${exception.message}"); null } catch (exception: RuntimeException) { debug("Failed to access DynaShop shop config manager: ${exception.message}"); null }
    }

    private fun getDynaShopPlugin(): Plugin? = Bukkit.getPluginManager().getPlugin(DYNASHOP_PLUGIN)?.takeIf { it.isEnabled }
    private fun debug(message: String) { if (plugin.config.getBoolean("debug", false)) plugin.logger.info(message) }

    private companion object { const val DYNASHOP_PLUGIN = "ShopGUIPlus-DynaShop" }
}
