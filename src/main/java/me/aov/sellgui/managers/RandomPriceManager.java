package me.aov.sellgui.managers;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class RandomPriceManager {

    private final SellGUIMain plugin;
    private final Random random;

    public RandomPriceManager(SellGUIMain plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    public void setRandomPriceRange(ItemStack item, double minPrice, double maxPrice, String playerName) {
        if (item == null) return;

        String itemKey = getItemKey(item);
        String itemType = getItemType(item);

        FileConfiguration config = getRandomPricesConfig();
        if (config == null) return;

        String path = "prices." + itemKey;
        config.set(path + ".min_price", minPrice);
        config.set(path + ".max_price", maxPrice);
        config.set(path + ".last_updated", System.currentTimeMillis());
        config.set(path + ".set_by", playerName);
        config.set(path + ".item_type", itemType);

        saveConfig();

        plugin.getLogger().info("Set random price range for " + itemKey + ": $" + minPrice + " - $" + maxPrice);
    }

    public double getRandomPrice(ItemStack item) {
        if (item == null) return 0.0;

        String itemKey = getItemKey(item);
        FileConfiguration config = getRandomPricesConfig();
        if (config == null) return 0.0;

        String path = "prices." + itemKey;
        if (!config.contains(path)) {
            return 0.0;
        }

        double minPrice = config.getDouble(path + ".min_price", 0.0);
        double maxPrice = config.getDouble(path + ".max_price", 0.0);

        if (minPrice >= maxPrice) {
            return minPrice;
        }

        return minPrice + (random.nextDouble() * (maxPrice - minPrice));
    }

    public double[] getPriceRange(ItemStack item) {
        if (item == null) return null;

        String itemKey = getItemKey(item);
        FileConfiguration config = getRandomPricesConfig();
        if (config == null) return null;

        String path = "prices." + itemKey;
        if (!config.contains(path)) {
            return null;
        }

        double minPrice = config.getDouble(path + ".min_price", 0.0);
        double maxPrice = config.getDouble(path + ".max_price", 0.0);

        return new double[]{minPrice, maxPrice};
    }

    public boolean hasRandomPrice(ItemStack item) {
        if (item == null) return false;

        String itemKey = getItemKey(item);
        FileConfiguration config = getRandomPricesConfig();
        if (config == null) return false;

        return config.contains("prices." + itemKey);
    }

    public void removeRandomPrice(ItemStack item) {
        if (item == null) return;

        String itemKey = getItemKey(item);
        FileConfiguration config = getRandomPricesConfig();
        if (config == null) return;

        config.set("prices." + itemKey, null);
        saveConfig();

        plugin.getLogger().info("Removed random price range for " + itemKey);
    }

    private String getItemKey(ItemStack item) {

        if (hasMMOItems()) {
            try {
                String mmoId = getMMOItemsId(item);
                if (mmoId != null) {
                    return "mmoitems:" + mmoId;
                }
            } catch (Exception e) {

            }
        }

        if (hasNexo()) {
            try {
                String nexoId = getNexoId(item);
                if (nexoId != null) {
                    return "nexo:" + nexoId;
                }
            } catch (Exception e) {

            }
        }

        return item.getType().name();
    }

    private String getItemType(ItemStack item) {
        if (hasMMOItems()) {
            try {
                String mmoId = getMMOItemsId(item);
                if (mmoId != null) {
                    return "MMOITEMS";
                }
            } catch (Exception e) {

            }
        }

        if (hasNexo()) {
            try {
                String nexoId = getNexoId(item);
                if (nexoId != null) {
                    return "NEXO";
                }
            } catch (Exception e) {

            }
        }

        return "VANILLA";
    }

    private boolean hasMMOItems() {
        try {
            return plugin.getServer().getPluginManager().getPlugin("MMOItems") != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasNexo() {
        try {
            return plugin.getServer().getPluginManager().getPlugin("Nexo") != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String getMMOItemsId(ItemStack item) {
        try {

            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String displayName = item.getItemMeta().getDisplayName();

                return null;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getNexoId(ItemStack item) {
        try {

            try {
                Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
                java.lang.reflect.Method getIdMethod = nexoItemsClass.getMethod("idFromItem", ItemStack.class);
                Object result = getIdMethod.invoke(null, item);
                if (result != null) {
                    return result.toString();
                }
            } catch (Exception e) {

            }

            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Error detecting Nexo item: " + e.getMessage());
            return null;
        }
    }

    private FileConfiguration getRandomPricesConfig() {
        if (plugin.getConfigManager() != null) {
            return plugin.getConfigManager().getConfig("random-prices");
        }
        return null;
    }

    private void saveConfig() {
        if (plugin.getConfigManager() != null) {
            plugin.getConfigManager().saveConfig("random-prices");
        }
    }

    public void cleanupOldEntries() {
        FileConfiguration config = getRandomPricesConfig();
        if (config == null) return;

        boolean autoCleanup = config.getBoolean("config.auto_cleanup", true);
        if (!autoCleanup) return;

        int cleanupDays = config.getInt("config.cleanup_days", 30);
        long cutoffTime = System.currentTimeMillis() - (cleanupDays * 24 * 60 * 60 * 1000L);

        if (config.contains("prices")) {
            for (String key : config.getConfigurationSection("prices").getKeys(false)) {
                long lastUpdated = config.getLong("prices." + key + ".last_updated", 0);
                if (lastUpdated < cutoffTime) {
                    config.set("prices." + key, null);
                    plugin.getLogger().info("Cleaned up old random price entry: " + key);
                }
            }
        }

        saveConfig();
    }

    public int getStoredPricesCount() {
        FileConfiguration config = getRandomPricesConfig();
        if (config == null || !config.contains("prices")) return 0;

        return config.getConfigurationSection("prices").getKeys(false).size();
    }

    public boolean requiresEvaluation(ItemStack item) {
        return hasRandomPrice(item);
    }

    public boolean isEvaluated(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        if (item.getItemMeta().getPersistentDataContainer() != null) {
            try {
                org.bukkit.NamespacedKey worthKey = new org.bukkit.NamespacedKey(plugin, "worth");
                if (item.getItemMeta().getPersistentDataContainer().has(worthKey,
                        org.bukkit.persistence.PersistentDataType.DOUBLE)) {
                    return true;
                }
            } catch (Exception e) {

            }
        }

        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer() != null) {
            try {
                org.bukkit.NamespacedKey evaluatedKey = new org.bukkit.NamespacedKey(plugin, "evaluated");
                if (item.getItemMeta().getPersistentDataContainer().has(evaluatedKey,
                        org.bukkit.persistence.PersistentDataType.BYTE)) {
                    return true;
                }
            } catch (Exception e) {

            }
        }

        try {
            if (plugin.getNBTPriceManager() != null) {
                double nbtPrice = plugin.getNBTPriceManager().getPriceFromNBT(item);
                return nbtPrice > 0;
            }
        } catch (Exception e) {

            plugin.getLogger().warning("Error checking NBT price: " + e.getMessage());
        }

        return false;
    }

    public boolean canBeSold(ItemStack item) {
        if (!requiresEvaluation(item)) {
            return true;
        }

        return isEvaluated(item);
    }
}