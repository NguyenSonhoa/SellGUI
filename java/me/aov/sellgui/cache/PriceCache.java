package me.aov.sellgui.cache;

import org.bukkit.inventory.ItemStack;
import me.aov.sellgui.SellGUIMain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PriceCache {
    private final SellGUIMain plugin;
    private final ConcurrentHashMap<String, CachedPrice> priceCache;
    private final ScheduledExecutorService scheduler;
    private final boolean cacheEnabled;
    private final long cacheDuration;

    public PriceCache(SellGUIMain plugin) {
        this.plugin = plugin;
        this.priceCache = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.cacheEnabled = plugin.getConfig().getBoolean("performance.cache-prices", true);
        this.cacheDuration = plugin.getConfig().getLong("performance.cache-duration", 300) * 1000;

        if (cacheEnabled) {
            startCleanupTask();
        }
    }

    public Double getCachedPrice(ItemStack item, org.bukkit.entity.Player player) {
        if (!cacheEnabled) return null;

        String cacheKey = generateCacheKey(item, player);
        CachedPrice cached = priceCache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            return cached.getPrice();
        }

        if (cached != null) {
            priceCache.remove(cacheKey);
        }

        return null;
    }

    public void cachePrice(ItemStack item, org.bukkit.entity.Player player, double price) {
        if (!cacheEnabled || price <= 0) return;

        String cacheKey = generateCacheKey(item, player);
        CachedPrice cachedPrice = new CachedPrice(price, System.currentTimeMillis() + cacheDuration);
        priceCache.put(cacheKey, cachedPrice);
    }

    private String generateCacheKey(ItemStack item, org.bukkit.entity.Player player) {
        StringBuilder key = new StringBuilder();

        key.append(item.getType().name());
        if (item.hasItemMeta()) {
            if (item.getItemMeta().hasDisplayName()) {
                key.append("_").append(item.getItemMeta().getDisplayName().hashCode());
            }
            if (item.getItemMeta().hasLore()) {
                key.append("_").append(item.getItemMeta().getLore().hashCode());
            }
        }

        if (player != null) {
            key.append("_player_").append(player.getUniqueId().toString());
        }

        return key.toString();
    }

    public void clearCache() {
        priceCache.clear();
    }

    public CacheStats getStats() {
        return new CacheStats(priceCache.size(), cacheEnabled, cacheDuration);
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            priceCache.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTime));
        }, 60, 60, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
        priceCache.clear();
    }

    private static class CachedPrice {
        private final double price;
        private final long expiryTime;

        public CachedPrice(double price, long expiryTime) {
            this.price = price;
            this.expiryTime = expiryTime;
        }

        public double getPrice() {
            return price;
        }

        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        public boolean isExpired(long currentTime) {
            return currentTime > expiryTime;
        }
    }

    public static class CacheStats {
        private final int size;
        private final boolean enabled;
        private final long duration;

        public CacheStats(int size, boolean enabled, long duration) {
            this.size = size;
            this.enabled = enabled;
            this.duration = duration;
        }

        public int getSize() { return size; }
        public boolean isEnabled() { return enabled; }
        public long getDuration() { return duration; }
    }
}