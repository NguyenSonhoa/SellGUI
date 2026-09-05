package me.aov.sellgui.dynamicshop

import me.aov.sellgui.api.SellGUIPriceProvider
import me.aov.sellgui.api.SoldItem
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.plugin.Plugin

internal class DynamicShopPriceProvider(private val plugin: SellGUIDynamicShop) : SellGUIPriceProvider {
    override fun getName(): String = DYNAMIC_SHOP_PLUGIN
    override fun getPriority(): Int = 100
    override fun isAvailable(): Boolean = getDynamicShopPlugin() != null && getShopDataManagerClass() != null

    override fun getSellPrice(player: Player, itemStack: ItemStack): Double {
        if (!isSellable(itemStack)) return 0.0
        val amount = maxOf(1, itemStack.amount)
        val material = itemStack.type
        if (!canSell(material, amount)) return 0.0
        return try {
            val managerClass = getShopDataManagerClass() ?: return 0.0
            val total = managerClass.getMethod("getTotalSellValue", Material::class.java, Int::class.javaPrimitiveType)
                .invoke(null, material, amount) as Number
            val totalPrice = total.toDouble()
            if (totalPrice > 0 && !totalPrice.isNaN() && !totalPrice.isInfinite()) totalPrice / amount else 0.0
        } catch (exception: ReflectiveOperationException) {
            debug("Failed to read DynamicShop sell price: ${exception.message}")
            0.0
        } catch (exception: RuntimeException) {
            debug("Failed to read DynamicShop sell price: ${exception.message}")
            0.0
        }
    }

    override fun onItemsSold(player: Player, soldItems: List<SoldItem>, totalPrice: Double) {
        val managerClass = getShopDataManagerClass() ?: return
        soldItems.forEach { soldItem ->
            val itemStack = soldItem.getItemStack() ?: return@forEach
            if (!isSellable(itemStack)) return@forEach
            val material = itemStack.type
            val amount = maxOf(1, soldItem.getAmount())
            if (!canSell(material, amount)) return@forEach
            val payout = readTotalSellValue(material, amount)
            updateStock(managerClass, material, amount)
            logTransaction(player, material, amount, payout)
        }
    }

    private fun isSellable(itemStack: ItemStack?): Boolean {
        if (itemStack == null || itemStack.type == Material.AIR || isDamaged(itemStack)) return false
        val material = itemStack.type
        val managerClass = getShopDataManagerClass() ?: return false
        return try {
            val basePrice = (managerClass.getMethod("getBasePrice", Material::class.java).invoke(null, material) as Number).toDouble()
            if (basePrice < 0) return false
            val disabled = managerClass.getMethod("isSellDisabled", Material::class.java).invoke(null, material)
            disabled != true && matchesTemplate(managerClass, itemStack, material)
        } catch (exception: ReflectiveOperationException) {
            debug("Failed to check DynamicShop item: ${exception.message}")
            false
        } catch (exception: RuntimeException) {
            debug("Failed to check DynamicShop item: ${exception.message}")
            false
        }
    }

    private fun canSell(material: Material, amount: Int): Boolean {
        val managerClass = getShopDataManagerClass() ?: return false
        return try {
            managerClass.getMethod("canSell", Material::class.java, Int::class.javaPrimitiveType).invoke(null, material, amount) == true
        } catch (exception: ReflectiveOperationException) {
            debug("Failed to check DynamicShop stock limit: ${exception.message}")
            false
        } catch (exception: RuntimeException) {
            debug("Failed to check DynamicShop stock limit: ${exception.message}")
            false
        }
    }

    @Throws(ReflectiveOperationException::class)
    private fun matchesTemplate(managerClass: Class<*>, itemStack: ItemStack, material: Material): Boolean {
        val template = (managerClass.getMethod("getTemplate", Material::class.java).invoke(null, material) as? ItemStack)?.clone()
            ?: ItemStack(material, 1)
        val oneItem = itemStack.clone()
        oneItem.amount = 1
        template.amount = 1
        return oneItem.isSimilar(template)
    }

    private fun readTotalSellValue(material: Material, amount: Int): Double {
        val managerClass = getShopDataManagerClass() ?: return 0.0
        return try {
            (managerClass.getMethod("getTotalSellValue", Material::class.java, Int::class.javaPrimitiveType).invoke(null, material, amount) as Number).toDouble()
        } catch (exception: ReflectiveOperationException) {
            debug("Failed to read DynamicShop transaction price: ${exception.message}")
            0.0
        } catch (exception: RuntimeException) {
            debug("Failed to read DynamicShop transaction price: ${exception.message}")
            0.0
        }
    }

    private fun updateStock(managerClass: Class<*>, material: Material, amount: Int) {
        try {
            managerClass.getMethod("updateStock", Material::class.java, Double::class.javaPrimitiveType).invoke(null, material, amount.toDouble())
        } catch (exception: ReflectiveOperationException) {
            plugin.logger.warning("Failed to update DynamicShop stock for $material: ${exception.message}")
        } catch (exception: RuntimeException) {
            plugin.logger.warning("Failed to update DynamicShop stock for $material: ${exception.message}")
        }
    }

    private fun logTransaction(player: Player, material: Material, amount: Int, payout: Double) {
        val dynamicShop = getDynamicShopPlugin() ?: return
        try {
            val logger = dynamicShop.javaClass.getMethod("getTransactionLogger").invoke(dynamicShop) ?: return
            val transactionClass = Class.forName(TRANSACTION)
            val transactionTypeClass = Class.forName(TRANSACTION_TYPE)
            val sellType = transactionTypeClass.enumConstants.firstOrNull { (it as Enum<*>).name == "SELL" } ?: return
            val transaction = transactionClass.getMethod(
                "now", String::class.java, transactionTypeClass, String::class.java,
                Int::class.javaPrimitiveType, Double::class.javaPrimitiveType, String::class.java, String::class.java
            ).invoke(null, player.name, sellType, material.name, amount, payout, readCategory(material), "")
            logger.javaClass.getMethod("log", transactionClass).invoke(logger, transaction)
        } catch (exception: ReflectiveOperationException) {
            debug("Failed to log DynamicShop transaction: ${exception.message}")
        } catch (exception: RuntimeException) {
            debug("Failed to log DynamicShop transaction: ${exception.message}")
        }
    }

    private fun readCategory(material: Material): String {
        val managerClass = getShopDataManagerClass() ?: return "UNKNOWN"
        return try {
            managerClass.getMethod("detectCategory", Material::class.java).invoke(null, material)?.toString() ?: "UNKNOWN"
        } catch (_: ReflectiveOperationException) {
            "UNKNOWN"
        } catch (_: RuntimeException) {
            "UNKNOWN"
        }
    }

    private fun isDamaged(itemStack: ItemStack): Boolean = (itemStack.itemMeta as? Damageable)?.let { it.hasDamage() && it.damage > 0 } == true
    private fun getShopDataManagerClass(): Class<*>? = try { Class.forName(SHOP_DATA_MANAGER) } catch (_: ClassNotFoundException) { null }
    private fun getDynamicShopPlugin(): Plugin? = Bukkit.getPluginManager().getPlugin(DYNAMIC_SHOP_PLUGIN)?.takeIf { it.isEnabled }
    private fun debug(message: String) { if (plugin.config.getBoolean("debug", false)) plugin.logger.info(message) }

    private companion object {
        const val DYNAMIC_SHOP_PLUGIN = "DynamicShop"
        const val SHOP_DATA_MANAGER = "org.minecraftsmp.dynamicshop.managers.ShopDataManager"
        const val TRANSACTION = "org.minecraftsmp.dynamicshop.transactions.Transaction"
        const val TRANSACTION_TYPE = "org.minecraftsmp.dynamicshop.transactions.Transaction\$TransactionType"
    }
}
