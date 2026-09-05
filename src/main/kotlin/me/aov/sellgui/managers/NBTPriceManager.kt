package me.aov.sellgui.managers

import io.lumine.mythic.lib.api.item.ItemTag
import io.lumine.mythic.lib.api.item.NBTItem
import me.aov.sellgui.SellGUIMain
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack

class NBTPriceManager(private val main: SellGUIMain) {
    private val random = java.util.Random()

    fun setFixedPrice(itemStack: ItemStack?, price: Double): ItemStack? {
        if (itemStack == null) return null
        return try {
            val nbtItem = NBTItem.get(itemStack)
            nbtItem.addTag(ItemTag(PRICE_NBT_KEY, price))
            nbtItem.addTag(ItemTag(PRICE_TYPE_NBT_KEY, PRICE_TYPE_FIXED))
            updateWorthLore(nbtItem.toItem(), price)
        } catch (exception: Exception) {
            main.logger.warning("Failed to set fixed price: ${exception.message}")
            itemStack
        }
    }

    fun setRandomPrice(itemStack: ItemStack?, minPrice: Double, maxPrice: Double): ItemStack? {
        if (itemStack == null) return null
        return try {
            val finalPrice = calculateRandomPrice(minPrice, maxPrice)
            val nbtItem = NBTItem.get(itemStack)
            nbtItem.addTag(ItemTag(PRICE_NBT_KEY, finalPrice))
            nbtItem.addTag(ItemTag(PRICE_TYPE_NBT_KEY, PRICE_TYPE_RANDOM))
            nbtItem.addTag(ItemTag(MIN_PRICE_NBT_KEY, minPrice))
            nbtItem.addTag(ItemTag(MAX_PRICE_NBT_KEY, maxPrice))
            updateWorthLore(nbtItem.toItem(), finalPrice)
        } catch (exception: Exception) {
            main.logger.warning("Failed to set random price: ${exception.message}")
            itemStack
        }
    }

    private fun calculateRandomPrice(minPrice: Double, maxPrice: Double): Double =
        if (minPrice >= maxPrice) minPrice else minPrice + random.nextDouble() * (maxPrice - minPrice)

    fun getPriceFromNBT(itemStack: ItemStack?): Double = try {
        if (itemStack != null && NBTItem.get(itemStack).hasTag(PRICE_NBT_KEY)) NBTItem.get(itemStack).getDouble(PRICE_NBT_KEY) else 0.0
    } catch (_: Exception) {
        0.0
    }

    fun getSellPrice(itemStack: ItemStack?): Double = getPriceFromNBT(itemStack)

    fun setSellPrice(itemStack: ItemStack?, price: Double) {
        setFixedPrice(itemStack, price)
    }

    fun hasNBTPrice(itemStack: ItemStack?): Boolean = try {
        itemStack != null && NBTItem.get(itemStack).hasTag(PRICE_NBT_KEY)
    } catch (_: Exception) {
        false
    }

    fun getPriceType(itemStack: ItemStack?): String? = try {
        itemStack?.let { NBTItem.get(it) }?.takeIf { it.hasTag(PRICE_TYPE_NBT_KEY) }?.getString(PRICE_TYPE_NBT_KEY)
    } catch (_: Exception) {
        null
    }

    private fun updateWorthLore(itemStack: ItemStack, price: Double): ItemStack {
        if (!itemStack.hasItemMeta()) return itemStack
        val meta = itemStack.itemMeta
        val lore = if (meta.hasLore()) ArrayList(meta.lore) else ArrayList()
        lore.removeIf { ChatColor.stripColor(it)?.startsWith("Worth:") == true }
        lore.add("${ChatColor.GOLD}Worth: ${ChatColor.GREEN}$${"%.2f".format(price)}")
        meta.lore = lore
        itemStack.itemMeta = meta
        return itemStack
    }

    fun removePricing(itemStack: ItemStack?): ItemStack? {
        if (itemStack == null) return null
        return try {
            val nbtItem = NBTItem.get(itemStack)
            listOf(PRICE_NBT_KEY, PRICE_TYPE_NBT_KEY, MIN_PRICE_NBT_KEY, MAX_PRICE_NBT_KEY).forEach(nbtItem::removeTag)
            nbtItem.toItem().also { result ->
                if (result.hasItemMeta()) {
                    val meta = result.itemMeta
                    if (meta.hasLore()) {
                        meta.lore = ArrayList(meta.lore).also { it.removeIf { line -> ChatColor.stripColor(line)?.startsWith("Worth:") == true } }
                        result.itemMeta = meta
                    }
                }
            }
        } catch (exception: Exception) {
            main.logger.warning("Failed to remove pricing: ${exception.message}")
            itemStack
        }
    }

    fun getRandomPriceRange(itemStack: ItemStack?): DoubleArray? = try {
        val nbtItem = itemStack?.let(NBTItem::get) ?: return null
        if (nbtItem.hasTag(MIN_PRICE_NBT_KEY) && nbtItem.hasTag(MAX_PRICE_NBT_KEY)) doubleArrayOf(nbtItem.getDouble(MIN_PRICE_NBT_KEY), nbtItem.getDouble(MAX_PRICE_NBT_KEY)) else null
    } catch (_: Exception) {
        null
    }

    fun isAvailable(): Boolean = try {
        Class.forName("io.lumine.mythic.lib.api.item.NBTItem")
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    fun regenerateRandomPrice(itemStack: ItemStack?): ItemStack? {
        val range = getRandomPriceRange(itemStack)
        return if (range != null) setRandomPrice(itemStack, range[0], range[1]) else itemStack
    }

    private companion object {
        const val PRICE_NBT_KEY = "sellgui:price"
        const val PRICE_TYPE_NBT_KEY = "sellgui:price_type"
        const val PRICE_TYPE_FIXED = "FIXED"
        const val PRICE_TYPE_RANDOM = "RANDOM"
        const val MIN_PRICE_NBT_KEY = "sellgui:min_price"
        const val MAX_PRICE_NBT_KEY = "sellgui:max_price"
    }
}
