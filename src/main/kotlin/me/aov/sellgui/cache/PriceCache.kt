package me.aov.sellgui.cache

import me.aov.sellgui.SellGUIMain
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class PriceCache(private val plugin: SellGUIMain) {
    private val priceCache = ConcurrentHashMap<String, CachedPrice>()
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private val cacheEnabled = plugin.config.getBoolean("performance.cache-prices", true)
    private val cacheDuration = plugin.config.getLong("performance.cache-duration", 300) * 1000

    init {
        if (cacheEnabled) {
            startCleanupTask()
        }
    }

    fun getCachedPrice(item: ItemStack, player: Player?): Double? {
        if (!cacheEnabled) return null

        val cacheKey = generateCacheKey(item, player)
        val cached = priceCache[cacheKey]
        if (cached != null && !cached.isExpired()) {
            return cached.getPrice()
        }
        if (cached != null) {
            priceCache.remove(cacheKey)
        }
        return null
    }

    fun cachePrice(item: ItemStack, player: Player?, price: Double) {
        if (!cacheEnabled || price <= 0) return

        priceCache[generateCacheKey(item, player)] = CachedPrice(price, System.currentTimeMillis() + cacheDuration)
    }

    private fun generateCacheKey(item: ItemStack, player: Player?): String {
        val key = StringBuilder()
        key.append(item.type.name)
        key.append("_amount_").append(item.amount)
        if (item.hasItemMeta()) {
            val itemMeta = item.itemMeta
            if (itemMeta.hasDisplayName()) {
                key.append("_").append(itemMeta.displayName.hashCode())
            }
            if (itemMeta.hasLore()) {
                key.append("_").append(itemMeta.lore.hashCode())
            }
        }
        if (player != null) {
            key.append("_player_").append(player.uniqueId)
        }
        return key.toString()
    }

    fun clearCache() {
        priceCache.clear()
    }

    fun getStats(): CacheStats = CacheStats(priceCache.size, cacheEnabled, cacheDuration)

    private fun startCleanupTask() {
        scheduler.scheduleAtFixedRate({
            val currentTime = System.currentTimeMillis()
            priceCache.entries.removeIf { it.value.isExpired(currentTime) }
        }, 60, 60, TimeUnit.SECONDS)
    }

    fun shutdown() {
        scheduler.shutdown()
        priceCache.clear()
    }

    private class CachedPrice(private val price: Double, private val expiryTime: Long) {
        fun getPrice(): Double = price

        fun isExpired(): Boolean = isExpired(System.currentTimeMillis())

        fun isExpired(currentTime: Long): Boolean = currentTime > expiryTime
    }

    class CacheStats(private val size: Int, private val enabled: Boolean, private val duration: Long) {
        fun getSize(): Int = size

        fun isEnabled(): Boolean = enabled

        fun getDuration(): Long = duration
    }
}
