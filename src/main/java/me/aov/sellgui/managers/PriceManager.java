package me.aov.sellgui.managers;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.utils.ItemIdentifier;
import net.brcdev.shopgui.ShopGuiPlusApi;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PriceManager {

    private final SellGUIMain main;

    public PriceManager(SellGUIMain main) {
        this.main = main;
    }
    private double getShopGUIPlusPrice(ItemStack itemStack, Player player) {
        if (!main.hasShopGUIPlus) return 0.0;
        try {
            return ShopGuiPlusApi.getItemStackPriceSell(player, itemStack);
        } catch (Throwable t) {
            return 0.0;
        }
    }
    public boolean setItemPrice(ItemStack itemStack, double price) {
        if (itemStack == null) {
            return false;
        }

        ItemIdentifier.ItemType type = ItemIdentifier.getItemType(itemStack);
        String identifier = ItemIdentifier.getItemIdentifier(itemStack);

        if (identifier == null) {
            return false;
        }

        try {
            switch (type) {
                case MMOITEMS:
                    return setMMOItemPrice(itemStack, price);

                case NEXO:
                    return setNexoPrice(itemStack, price);

                case VANILLA:
                    return setVanillaPrice(itemStack, price);

                default:
                    return false;
            }
        } catch (Exception e) {
            main.getLogger().warning("Failed to set price for item: " + identifier + " - " + e.getMessage());
            return false;
        }
    }

    public double getItemPrice(ItemStack itemStack) {
        if (itemStack == null) {
            return 0.0;
        }

        String calculationMethod = main.getConfig().getString("prices.calculation-method", "auto");
        boolean nbtPricingEnabled = main.getConfig().getBoolean("prices.nbt-pricing", true);

        if (nbtPricingEnabled && itemStack.hasItemMeta() && itemStack.getItemMeta().getPersistentDataContainer() != null) {
            try {
                NamespacedKey worthKey = new NamespacedKey(main, "worth");
                if (itemStack.getItemMeta().getPersistentDataContainer().has(worthKey,
                        PersistentDataType.DOUBLE)) {
                    double worthPrice = itemStack.getItemMeta().getPersistentDataContainer().get(worthKey,
                            PersistentDataType.DOUBLE);
                    if (worthPrice > 0) {

                        if (main.getConfig().getBoolean("general.debug", false)) {
                            main.getLogger().info("Using worth NBT price for " + itemStack.getType() + ": $" + worthPrice);
                        }
                        return worthPrice;
                    }
                }
            } catch (Exception e) {

            }
        }

        if (!calculationMethod.equals("auto")) {
            double price = getSpecificMethodPrice(itemStack, calculationMethod, null); // Truyền null nếu không có Player
            if (price > 0) {
                return applyRandomVariation(price);
            }
        }

        if (main.getRandomPriceManager() != null) {
            try {
                if (main.getRandomPriceManager().hasRandomPrice(itemStack)) {
                    double randomPrice = main.getRandomPriceManager().getRandomPrice(itemStack);
                    if (randomPrice > 0) {
                        if (main.getConfig().getBoolean("general.debug", false)) {
                            main.getLogger().info("Using random price for " + itemStack.getType() + ": $" + randomPrice);
                        }
                        return randomPrice;
                    }
                }
            } catch (Exception e) {

            }
        }

        if (main.getNBTPriceManager() != null) {
            try {
                double nbtPrice = main.getNBTPriceManager().getPriceFromNBT(itemStack);
                if (nbtPrice > 0) {
                    return nbtPrice;
                }
            } catch (Exception e) {

            }
        }

        ItemIdentifier.ItemType type = ItemIdentifier.getItemType(itemStack);

        switch (type) {
            case MMOITEMS:
                double mmoPrice = getMMOItemPrice(itemStack);
                return applyRandomVariation(mmoPrice);

            case NEXO:
                double nexoPrice = getNexoPrice(itemStack);
                return applyRandomVariation(nexoPrice);

            case VANILLA:
                double vanillaPrice = getVanillaPrice(itemStack);
                return applyRandomVariation(vanillaPrice);

            default:
                double defaultPrice = main.getConfig().getDouble("prices.default-price", 0.0);
                return applyRandomVariation(defaultPrice);
        }
    }

    public double getItemPriceWithPlayer(ItemStack itemStack, Player player) {
        String calculationMethod = main.getConfig().getString("prices.calculation-method", "auto");
        if ("shopguiplus".equalsIgnoreCase(calculationMethod)) {
            double price = getSpecificMethodPrice(itemStack, "shopguiplus", player);
            return price > 0 ? price : 0.0;
        }
        double basePrice = getItemPrice(itemStack);
        if (basePrice <= 0 || player == null) {
            return basePrice;
        }
        double multiplier = getPlayerMultiplier(player);
        return basePrice * multiplier;
    }

    private double getSpecificMethodPrice(ItemStack itemStack, String method, Player player) {
        switch (method.toLowerCase()) {
            case "config":
                return getConfigPrice(itemStack);
            case "essentials":
                return getEssentialsPrice(itemStack);
            case "nbt":
                if (main.getNBTPriceManager() != null) {
                    return main.getNBTPriceManager().getPriceFromNBT(itemStack);
                }
                return 0.0;
            case "shopguiplus":
                return getShopGUIPlusPrice(itemStack, player);
            default:
                return 0.0;
        }
    }

    private double getConfigPrice(ItemStack itemStack) {
        ItemIdentifier.ItemType type = ItemIdentifier.getItemType(itemStack);

        switch (type) {
            case MMOITEMS:
                return getMMOItemPrice(itemStack);

            case NEXO:
                return getNexoPrice(itemStack);

            case VANILLA:
                return getVanillaPrice(itemStack);

            default:
                return main.getConfig().getDouble("prices.default-price", 0.0);
        }
    }

    private double getEssentialsPrice(ItemStack itemStack) {

        return 0.0;
    }

    private double applyMultipliers(double basePrice, ItemStack itemStack) {
        if (basePrice <= 0) return basePrice;

        boolean multipliersEnabled = main.getConfig().getBoolean("prices.multipliers.enabled", true);
        if (!multipliersEnabled) return basePrice;

        double multiplier = getMultiplier(itemStack);
        double finalPrice = basePrice * multiplier;

        finalPrice = applyRandomVariation(finalPrice);

        return finalPrice;
    }

    private double getMultiplier(ItemStack itemStack) {
        double defaultMultiplier = main.getConfig().getDouble("prices.multipliers.default-multiplier", 1.0);
        double maxMultiplier = main.getConfig().getDouble("prices.multipliers.max-multiplier", 5.0);

        return Math.min(defaultMultiplier, maxMultiplier);
    }

    public double getPlayerMultiplier(Player player) {
        if (player == null) return 1.0;

        boolean permissionBasedEnabled = main.getConfig().getBoolean("prices.multipliers.permission-based", true);
        if (!permissionBasedEnabled) {
            return main.getConfig().getDouble("prices.multipliers.default-multiplier", 1.0);
        }

        double maxMultiplier = main.getConfig().getDouble("prices.multipliers.max-multiplier", 5.0);
        double multiplier = 1.0;

        for (double testMultiplier = 0.1; testMultiplier <= maxMultiplier; testMultiplier += 0.1) {
            String permission = "sellgui.multiplier." + String.format("%.1f", testMultiplier).replace(",", ".");
            if (player.hasPermission(permission)) {
                multiplier = Math.max(multiplier, testMultiplier);
            }
        }

        if (player.hasPermission("sellgui.vip")) {
            multiplier = Math.max(multiplier, 1.5);
        }
        if (player.hasPermission("sellgui.premium")) {
            multiplier = Math.max(multiplier, 2.0);
        }
        if (player.hasPermission("sellgui.elite")) {
            multiplier = Math.max(multiplier, 3.0);
        }

        return Math.min(multiplier, maxMultiplier);
    }

    private double applyRandomVariation(double price) {
        boolean randomPricingEnabled = main.getConfig().getBoolean("prices.random-pricing.enabled", false);
        if (!randomPricingEnabled) return price;

        double variationPercent = main.getConfig().getDouble("prices.random-pricing.variation-percent", 10.0);
        double variation = (Math.random() - 0.5) * 2 * (variationPercent / 100.0);

        return price * (1.0 + variation);
    }

    public boolean removeItemPrice(ItemStack itemStack) {
        return setItemPrice(itemStack, 0.0);
    }

    private boolean setVanillaPrice(ItemStack itemStack, double price) {
        try {
            main.getItemPricesConfig().set(itemStack.getType().name(), price);
            main.getItemPricesConfig().save(getItemPricesFile());
            return true;
        } catch (IOException e) {
            main.getLogger().warning("Failed to save vanilla item price: " + e.getMessage());
            return false;
        }
    }
    private File getMMOItemsPricesFile() {
        return new File(main.getDataFolder(), "mmoitems.yml");
    }

    private File getNexoPricesFile() {
        return new File(main.getDataFolder(), "nexo.yml");
    }
    private File getItemPricesFile() {
        return new File(main.getDataFolder(), "itemprices.yml");
    }


    private double getVanillaPrice(ItemStack itemStack) {
        return main.getItemPricesConfig().getDouble(itemStack.getType().name(), 0.0);
    }

    private boolean setMMOItemPrice(ItemStack itemStack, double price) {
        try {
            String identifier = ItemIdentifier.getItemIdentifier(itemStack);
            if (identifier == null || !identifier.startsWith("MMOITEMS:")) {
                return false;
            }

            String[] parts = identifier.substring("MMOITEMS:".length()).split("\\.");
            if (parts.length != 2) {
                return false;
            }

            String itemType = parts[0];
            String itemId = parts[1];

            main.getMMOItemsPricesFileConfig().set("mmoitems." + itemType + "." + itemId, price);
            main.getMMOItemsPricesFileConfig().save(getMMOItemsPricesFile());

            Map<String, Double> loadedPrices = main.getLoadedMMOItemPrices();
            if (loadedPrices != null) {
                loadedPrices.put(itemType + "." + itemId, price);
            }

            return true;
        } catch (IOException e) {
            main.getLogger().warning("Failed to save MMOItem price: " + e.getMessage());
            return false;
        }
    }

    private double getMMOItemPrice(ItemStack itemStack) {
        String identifier = ItemIdentifier.getItemIdentifier(itemStack);
        if (identifier == null || !identifier.startsWith("MMOITEMS:")) {
            return 0.0;
        }

        String key = identifier.substring("MMOITEMS:".length());
        Map<String, Double> loadedPrices = main.getLoadedMMOItemPrices();

        if (loadedPrices != null && loadedPrices.containsKey(key)) {
            return loadedPrices.get(key);
        }

        return 0.0;
    }

    private boolean setNexoPrice(ItemStack itemStack, double price) {
        try {
            String identifier = ItemIdentifier.getItemIdentifier(itemStack);
            if (identifier == null || !identifier.startsWith("NEXO:")) {
                return false;
            }

            String nexoId = identifier.substring("NEXO:".length());
            main.getNexoPricesFileConfig().set("nexo." + nexoId, price);
            main.getNexoPricesFileConfig().save(getNexoPricesFile());

            Map<String, Double> loadedPrices = main.getLoadedNexoPrices();
            if (loadedPrices != null) {
                loadedPrices.put(nexoId, price);
            }

            return true;
        } catch (IOException e) {
            main.getLogger().warning("Failed to save Nexo item price: " + e.getMessage());
            return false;
        }
    }

    private double getNexoPrice(ItemStack itemStack) {
        String identifier = ItemIdentifier.getItemIdentifier(itemStack);
        if (identifier == null || !identifier.startsWith("NEXO:")) {
            return 0.0;
        }

        String nexoId = identifier.substring("NEXO:".length());
        Map<String, Double> loadedPrices = main.getLoadedNexoPrices();

        if (loadedPrices != null && loadedPrices.containsKey(nexoId)) {
            return loadedPrices.get(nexoId);
        }

        return 0.0;
    }

    public Map<String, Double> getAllPricesForType(ItemIdentifier.ItemType type) {
        Map<String, Double> prices = new HashMap<>();

        switch (type) {
            case MMOITEMS:
                Map<String, Double> mmoItems = main.getLoadedMMOItemPrices();
                if (mmoItems != null) {
                    for (Map.Entry<String, Double> entry : mmoItems.entrySet()) {
                        if (entry.getValue() > 0) {
                            prices.put("MMOITEMS:" + entry.getKey(), entry.getValue());
                        }
                    }
                }
                break;

            case NEXO:
                ConfigurationSection nexoSection = main.getNexoPricesFileConfig().getConfigurationSection("nexo");
                if (nexoSection != null) {
                    for (String key : nexoSection.getKeys(false)) {
                        double price = nexoSection.getDouble(key);
                        if (price > 0) {
                            prices.put("NEXO:" + key, price);
                        }
                    }
                }
                break;

            case VANILLA:
                for (String key : main.getItemPricesConfig().getKeys(false)) {
                    if (!key.equals("flat-enchantment-bonus") && !key.equals("multiplier-enchantment-bonus")) {
                        double price = main.getItemPricesConfig().getDouble(key);
                        if (price > 0) {
                            prices.put("VANILLA:" + key, price);
                        }
                    }
                }
                break;
        }

        return prices;
    }
}