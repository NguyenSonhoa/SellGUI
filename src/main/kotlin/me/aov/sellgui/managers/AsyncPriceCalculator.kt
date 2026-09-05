package me.aov.sellgui.managers

import me.aov.sellgui.SellGUIMain
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class AsyncPriceCalculator(private val plugin: SellGUIMain) {
    private val asyncEnabled = plugin.config.getBoolean("performance.async-calculations", true)
    private val maxConcurrentOps = plugin.config.getInt("performance.max-concurrent-operations", 10).coerceAtLeast(1)
    private val executor: ExecutorService? = if (asyncEnabled) Executors.newFixedThreadPool(maxConcurrentOps) else null
    private val semaphore: Semaphore? = if (asyncEnabled) Semaphore(maxConcurrentOps) else null

    fun calculatePriceAsync(item: ItemStack?, player: Player, callback: Consumer<Double>) {
        if (!asyncEnabled) {
            callback.accept(calculatePriceSync(item, player))
            return
        }
        CompletableFuture.supplyAsync({ withPermit { calculatePriceSync(item, player) } }, executor)
            .thenAccept { price -> Bukkit.getScheduler().runTask(plugin, Runnable { callback.accept(price) }) }
    }

    fun calculatePricesAsync(items: Array<ItemStack?>, player: Player, callback: Consumer<Array<Double?>>) {
        if (!asyncEnabled) {
            callback.accept(Array(items.size) { calculatePriceSync(items[it], player) })
            return
        }
        CompletableFuture.supplyAsync({
            val prices = arrayOfNulls<Double>(items.size)
            val latch = CountDownLatch(items.size)
            items.forEachIndexed { index, item ->
                executor!!.submit {
                    try {
                        prices[index] = withPermit { calculatePriceSync(item, player) }
                    } finally {
                        latch.countDown()
                    }
                }
            }
            try {
                latch.await(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            prices
        }, executor).thenAccept { prices -> Bukkit.getScheduler().runTask(plugin, Runnable { callback.accept(prices) }) }
    }

    fun calculateInventoryValueAsync(player: Player, callback: Consumer<Double>) {
        val contents = player.inventory.contents
        calculatePricesAsync(contents, player) { prices ->
            val total = contents.indices.sumOf { index -> (contents[index]?.amount ?: 0) * (prices[index] ?: 0.0) }
            callback.accept(total)
        }
    }

    private fun <T> withPermit(action: () -> T): T {
        val currentSemaphore = semaphore ?: return action()
        var acquired = false
        try {
            currentSemaphore.acquire()
            acquired = true
            return action()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            @Suppress("UNCHECKED_CAST")
            return 0.0 as T
        } finally {
            if (acquired) currentSemaphore.release()
        }
    }

    private fun calculatePriceSync(item: ItemStack?, player: Player): Double {
        if (item == null) return 0.0
        return try {
            plugin.getPriceCache()?.getCachedPrice(item, player)?.let { return it }
            val price = plugin.getPriceManager()?.getItemPriceWithPlayer(item, player) ?: 0.0
            if (price > 0) plugin.getPriceCache()?.cachePrice(item, player, price)
            price
        } catch (exception: Exception) {
            plugin.logger.warning("Error calculating price for ${item.type}: ${exception.message}")
            0.0
        }
    }

    fun getStats(): AsyncStats {
        val threadPool = executor as? ThreadPoolExecutor
        return AsyncStats(asyncEnabled, maxConcurrentOps, threadPool?.activeCount ?: 0, threadPool?.queue?.size ?: 0)
    }

    fun shutdown() {
        val currentExecutor = executor ?: return
        currentExecutor.shutdown()
        try {
            if (!currentExecutor.awaitTermination(5, TimeUnit.SECONDS)) currentExecutor.shutdownNow()
        } catch (_: InterruptedException) {
            currentExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    class AsyncStats(private val enabled: Boolean, private val maxConcurrent: Int, private val activeThreads: Int, private val queuedTasks: Int) {
        fun isEnabled(): Boolean = enabled
        fun getMaxConcurrent(): Int = maxConcurrent
        fun getActiveThreads(): Int = activeThreads
        fun getQueuedTasks(): Int = queuedTasks
    }
}
