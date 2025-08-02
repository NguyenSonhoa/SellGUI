package me.aov.sellgui.managers;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import me.aov.sellgui.SellGUIMain;

import java.util.concurrent.*;
import java.util.function.Consumer;

public class AsyncPriceCalculator {

    private final SellGUIMain plugin;
    private final ExecutorService executor;
    private final boolean asyncEnabled;
    private final int maxConcurrentOps;
    private final Semaphore semaphore;

    public AsyncPriceCalculator(SellGUIMain plugin) {
        this.plugin = plugin;
        this.asyncEnabled = plugin.getConfig().getBoolean("performance.async-calculations", true);
        this.maxConcurrentOps = plugin.getConfig().getInt("performance.max-concurrent-operations", 10);

        if (asyncEnabled) {
            this.executor = Executors.newFixedThreadPool(maxConcurrentOps);
            this.semaphore = new Semaphore(maxConcurrentOps);
        } else {
            this.executor = null;
            this.semaphore = null;
        }
    }

    public void calculatePriceAsync(ItemStack item, Player player, Consumer<Double> callback) {
        if (!asyncEnabled) {

            double price = calculatePriceSync(item, player);
            callback.accept(price);
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                semaphore.acquire();
                return calculatePriceSync(item, player);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0.0;
            } finally {
                semaphore.release();
            }
        }, executor).thenAcceptAsync(price -> {

            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(price));
        });
    }

    public void calculatePricesAsync(ItemStack[] items, Player player, Consumer<Double[]> callback) {
        if (!asyncEnabled) {

            Double[] prices = new Double[items.length];
            for (int i = 0; i < items.length; i++) {
                prices[i] = calculatePriceSync(items[i], player);
            }
            callback.accept(prices);
            return;
        }

        CompletableFuture<Double[]> future = CompletableFuture.supplyAsync(() -> {
            Double[] prices = new Double[items.length];
            CountDownLatch latch = new CountDownLatch(items.length);

            for (int i = 0; i < items.length; i++) {
                final int index = i;
                final ItemStack item = items[i];

                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        prices[index] = calculatePriceSync(item, player);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        prices[index] = 0.0;
                    } finally {
                        semaphore.release();
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return prices;
        }, executor);

        future.thenAcceptAsync(prices -> {

            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(prices));
        });
    }

    public void calculateInventoryValueAsync(Player player, Consumer<Double> callback) {
        ItemStack[] contents = player.getInventory().getContents();

        calculatePricesAsync(contents, player, prices -> {
            double total = 0.0;
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null && prices[i] != null) {
                    total += prices[i] * contents[i].getAmount();
                }
            }
            callback.accept(total);
        });
    }

    private double calculatePriceSync(ItemStack item, Player player) {
        if (item == null) return 0.0;

        try {

            if (plugin.getPriceCache() != null) {
                Double cachedPrice = plugin.getPriceCache().getCachedPrice(item, player);
                if (cachedPrice != null) {
                    return cachedPrice;
                }
            }

            double price = 0.0;
            if (plugin.getPriceManager() != null) {
                price = plugin.getPriceManager().getItemPriceWithPlayer(item, player);
            }

            if (plugin.getPriceCache() != null && price > 0) {
                plugin.getPriceCache().cachePrice(item, player, price);
            }

            return price;

        } catch (Exception e) {
            plugin.getLogger().warning("Error calculating price for " + item.getType() + ": " + e.getMessage());
            return 0.0;
        }
    }

    public AsyncStats getStats() {
        int activeThreads = 0;
        int queuedTasks = 0;

        if (executor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
            activeThreads = tpe.getActiveCount();
            queuedTasks = tpe.getQueue().size();
        }

        return new AsyncStats(asyncEnabled, maxConcurrentOps, activeThreads, queuedTasks);
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static class AsyncStats {
        private final boolean enabled;
        private final int maxConcurrent;
        private final int activeThreads;
        private final int queuedTasks;

        public AsyncStats(boolean enabled, int maxConcurrent, int activeThreads, int queuedTasks) {
            this.enabled = enabled;
            this.maxConcurrent = maxConcurrent;
            this.activeThreads = activeThreads;
            this.queuedTasks = queuedTasks;
        }

        public boolean isEnabled() { return enabled; }
        public int getMaxConcurrent() { return maxConcurrent; }
        public int getActiveThreads() { return activeThreads; }
        public int getQueuedTasks() { return queuedTasks; }
    }
}