package me.aov.sellgui.managers;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.utils.ColorUtils;
import me.aov.sellgui.utils.ItemIdentifier;
import net.brcdev.shopgui.ShopGuiPlusApi;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PriceManager {

    private final SellGUIMain main;
    private final Map<ItemStack, Double> customItemPrices = new HashMap<>();

    public PriceManager(SellGUIMain main) {
        this.main = main;
        loadCustomItemPrices();
    }

    public void loadCustomItemPrices() {
        customItemPrices.clear();
        ConfigurationSection customItemsSection = main.getCustomItemsConfig().getConfigurationSection("custom-items");
        if (customItemsSection != null) {
            for (String key : customItemsSection.getKeys(false)) {
                ItemStack item = customItemsSection.getItemStack(key + ".item");
                double price = customItemsSection.getDouble(key + ".price");
                if (item != null && price > 0) {
                    customItemPrices.put(item, price);
                }
            }
        }
    }

    private double getShopGUIPlusPrice(ItemStack itemStack, Player player) {
        if (!main.hasShopGUIPlus) return 0.0;
        try {
            double price = ShopGuiPlusApi.getItemStackPriceSell(player, itemStack);
            if (price <= 0) {

                price = ShopGuiPlusApi.getItemStackPriceSell(itemStack);
            }

            if (price > 0) {
                return price / itemStack.getAmount();
            }

            return 0.0;
        } catch (Throwable t) {
            if (main.getConfig().getBoolean("general.debug", false)) {
                main.getLogger().warning("Error getting ShopGUI+ price: " + t.getMessage());
            }
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
            main.getLogger().warning(() -> "Failed to set price for item: " + identifier + " - " + e.getMessage());
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
            } catch (Exception ignored) {}
        }

        if (!calculationMethod.equals("auto")) {
            double price = getSpecificMethodPrice(itemStack, calculationMethod, null);
            return applyRandomVariation(price);
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
            } catch (Exception ignored) {}
        }

        double customPrice = getCustomItemPrice(itemStack);
        if (customPrice > 0) {
            if (main.getConfig().getBoolean("general.debug", false)) {
                main.getLogger().info("Using custom item price for " + itemStack.getType() + ": $" + customPrice);
            }
            return applyRandomVariation(customPrice);
        }

        ItemIdentifier.ItemType type = ItemIdentifier.getItemType(itemStack);
        double specialConfigPrice = 0;
        if (type == ItemIdentifier.ItemType.MMOITEMS) {
            specialConfigPrice = getMMOItemPrice(itemStack);
        } else if (type == ItemIdentifier.ItemType.NEXO) {
            specialConfigPrice = getNexoPrice(itemStack);
        }

        if (specialConfigPrice > 0) {
            return applyRandomVariation(specialConfigPrice);
        }

        if (main.hasEssentials()) {
            double essPrice = getEssentialsPrice(itemStack);
            if (essPrice > 0) {
                if (main.getConfig().getBoolean("general.debug", false)) {
                    main.getLogger().info("Using Essentials price for " + itemStack.getType() + ": $" + essPrice);
                }
                return applyRandomVariation(essPrice);
            }
        }

        if (type == ItemIdentifier.ItemType.VANILLA) {
            double vanillaPrice = getVanillaPrice(itemStack);
            if (vanillaPrice > 0) {
                return applyRandomVariation(vanillaPrice);
            }
        }

        if (main.getNBTPriceManager() != null) {
            try {
                double nbtPrice = main.getNBTPriceManager().getSellPrice(itemStack);
                if (nbtPrice > 0) {
                    return nbtPrice;
                }
            } catch (Exception ignored) {}
        }

        double defaultPrice = main.getConfig().getDouble("prices.default-price", 0.0);
        return applyRandomVariation(defaultPrice);
    }

    public double getPrice(String itemIdentifier) {
        ItemIdentifier.ItemType type = ItemIdentifier.getItemTypeFromString(itemIdentifier);
        if (type == null) return 0.0;

        switch (type) {
            case MMOITEMS:
                String mmoKey = itemIdentifier.substring("MMOITEMS:".length());
                return main.getLoadedMMOItemPrices().getOrDefault(mmoKey, 0.0);
            case NEXO:
                String nexoKey = itemIdentifier.substring("NEXO:".length());
                return main.getLoadedNexoPrices().getOrDefault(nexoKey, 0.0);
            case VANILLA:
                String vanillaKey = itemIdentifier.substring("VANILLA:".length());
                return main.getItemPricesConfig().getDouble(vanillaKey, 0.0);
            default:
                return 0.0;
        }
    }

    public boolean hasPrice(String itemIdentifier) {
        return getPrice(itemIdentifier) > 0.0;
    }

    public double getItemPriceWithPlayer(ItemStack itemStack, Player player) {
        String calculationMethod = main.getConfig().getString("prices.calculation-method", "auto");
        if ("shopguiplus".equalsIgnoreCase(calculationMethod)) {
            double price = getSpecificMethodPrice(itemStack, "shopguiplus", player);
            return price > 0 ? price : 0.0;
        }
        double basePrice = getItemPrice(itemStack);

        if (basePrice == 0 && player != null && main.getConfig().getBoolean("use-shopguiplus-price") && main.hasShopGUIPlus) {
            basePrice = getShopGUIPlusPrice(itemStack, player);
        }

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
                    return main.getNBTPriceManager().getSellPrice(itemStack);
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
        if (main.hasEssentials() && main.getEssentialsHolder() != null) {
            return main.getEssentialsHolder().getPrice(itemStack).doubleValue();
        }
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
        double defaultMultiplier = main.getConfig().getDouble("prices.multipliers.default-multiplier", 1.0);
        double multiplier = defaultMultiplier;

        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            if (pai.getPermission().startsWith("sellgui.multiplier.") && pai.getValue()) {

                if (player.isOp() && pai.getAttachment() == null) {
                    continue;
                }

                try {
                    String val = pai.getPermission().substring("sellgui.multiplier.".length());
                    double testMultiplier = Double.parseDouble(val);
                    if (testMultiplier > multiplier) {
                        multiplier = testMultiplier;
                    }
                } catch (NumberFormatException ignored) {}
            }
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

        if (itemStack.hasItemMeta() && (itemStack.getItemMeta().hasDisplayName() || itemStack.getItemMeta().hasLore())) {

            if (main.getNBTPriceManager() != null) {
                try {
                    main.getNBTPriceManager().setSellPrice(itemStack, price);
                } catch (Exception e) {
                    main.getLogger().warning("Failed to save custom item price to NBT: " + e.getMessage());
                }
            }

            saveCustomItemToFile(itemStack, price);
            return true;
        }

        try {
            main.getItemPricesConfig().set(itemStack.getType().name(), price);
            main.getItemPricesConfig().save(getItemPricesFile());
            return true;
        } catch (IOException e) {
            main.getLogger().warning("Failed to save vanilla item price: " + e.getMessage());
            return false;
        }
    }

    private void saveCustomItemToFile(ItemStack item, double price) {
        FileConfiguration config = main.getCustomItemsConfig();
        ConfigurationSection section = config.getConfigurationSection("custom-items");
        if (section == null) {
            section = config.createSection("custom-items");
        }

        String foundKey = null;
        for (String key : section.getKeys(false)) {
            ItemStack existingItem = section.getItemStack(key + ".item");
            if (isSimilarCustomItem(item, existingItem)) {
                foundKey = key;
                break;
            }
        }

        if (foundKey == null) {
            foundKey = UUID.randomUUID().toString();
        }

        ItemStack itemToSave = item.clone();
        itemToSave.setAmount(1);

        config.set("custom-items." + foundKey + ".item", itemToSave);
        config.set("custom-items." + foundKey + ".price", price);

        try {
            config.save(new File(main.getDataFolder(), "customitems.yml"));
        } catch (IOException e) {
            main.getLogger().warning("Failed to save customitems.yml: " + e.getMessage());
        }

        loadCustomItemPrices();
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

        String typeMaterial = String.valueOf(itemStack.getType());
        if (itemStack.getType() == Material.SHULKER_BOX ||
                itemStack.getType() == Material.WHITE_SHULKER_BOX ||
                itemStack.getType() == Material.ORANGE_SHULKER_BOX ||
                itemStack.getType() == Material.MAGENTA_SHULKER_BOX ||
                itemStack.getType() == Material.LIGHT_BLUE_SHULKER_BOX ||
                itemStack.getType() == Material.YELLOW_SHULKER_BOX ||
                itemStack.getType() == Material.LIME_SHULKER_BOX ||
                itemStack.getType() == Material.PINK_SHULKER_BOX ||
                itemStack.getType() == Material.GRAY_SHULKER_BOX ||
                itemStack.getType() == Material.LIGHT_GRAY_SHULKER_BOX ||
                itemStack.getType() == Material.CYAN_SHULKER_BOX ||
                itemStack.getType() == Material.PURPLE_SHULKER_BOX ||
                itemStack.getType() == Material.BLUE_SHULKER_BOX ||
                itemStack.getType() == Material.BROWN_SHULKER_BOX ||
                itemStack.getType() == Material.GREEN_SHULKER_BOX ||
                itemStack.getType() == Material.RED_SHULKER_BOX ||
                itemStack.getType() == Material.BLACK_SHULKER_BOX) {
            return main.getItemPricesConfig().getDouble("SHULKER_BOX", 0.0);
        }
        return main.getItemPricesConfig().getDouble(typeMaterial, 0.0);
    }

    private boolean isSimilarCustomItem(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) {
            return false;
        }
        if (item1.getType() != item2.getType()) {
            return false;
        }

        boolean hasMeta1 = item1.hasItemMeta();
        boolean hasMeta2 = item2.hasItemMeta();

        if (hasMeta1 != hasMeta2) {
            return false;
        }

        if (!hasMeta1) {

            return true;
        }

        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();

        try {
            NamespacedKey slimefunKey = new NamespacedKey("slimefun", "slimefun_item");
            if (meta1.getPersistentDataContainer().has(slimefunKey, PersistentDataType.STRING) &&
                    meta2.getPersistentDataContainer().has(slimefunKey, PersistentDataType.STRING)) {

                String id1 = meta1.getPersistentDataContainer().get(slimefunKey, PersistentDataType.STRING);
                String id2 = meta2.getPersistentDataContainer().get(slimefunKey, PersistentDataType.STRING);
                if (id1 != null && id1.equals(id2)) {
                    return true;

                }
            }
        } catch (Exception ignored) {}

        try {
            NamespacedKey oraxenKey = new NamespacedKey("oraxen", "id");
            if (meta1.getPersistentDataContainer().has(oraxenKey, PersistentDataType.STRING) &&
                    meta2.getPersistentDataContainer().has(oraxenKey, PersistentDataType.STRING)) {

                String id1 = meta1.getPersistentDataContainer().get(oraxenKey, PersistentDataType.STRING);
                String id2 = meta2.getPersistentDataContainer().get(oraxenKey, PersistentDataType.STRING);
                if (id1 != null && id1.equals(id2)) {
                    return true;

                }
            }
        } catch (Exception ignored) {}

        try {
            NamespacedKey itemsAdderKey = new NamespacedKey("itemsadder", "id");
            if (meta1.getPersistentDataContainer().has(itemsAdderKey, PersistentDataType.STRING) &&
                    meta2.getPersistentDataContainer().has(itemsAdderKey, PersistentDataType.STRING)) {

                String id1 = meta1.getPersistentDataContainer().get(itemsAdderKey, PersistentDataType.STRING);
                String id2 = meta2.getPersistentDataContainer().get(itemsAdderKey, PersistentDataType.STRING);
                if (id1 != null && id1.equals(id2)) {
                    return true;

                }
            }
        } catch (Exception ignored) {}

        try {
            NamespacedKey ecoKey = new NamespacedKey("eco", "item");
            if (meta1.getPersistentDataContainer().has(ecoKey, PersistentDataType.STRING) &&
                    meta2.getPersistentDataContainer().has(ecoKey, PersistentDataType.STRING)) {

                String id1 = meta1.getPersistentDataContainer().get(ecoKey, PersistentDataType.STRING);
                String id2 = meta2.getPersistentDataContainer().get(ecoKey, PersistentDataType.STRING);
                if (id1 != null && id1.equals(id2)) {
                    return true;

                }
            }
        } catch (Exception ignored) {}

        boolean hasName1 = meta1.hasDisplayName();
        boolean hasName2 = meta2.hasDisplayName();
        if (hasName1 != hasName2) {
            return false;
        }
        if (hasName1 && !meta1.getDisplayName().equals(meta2.getDisplayName())) {
            return false;
        }

        List<String> lore1 = meta1.hasLore() ? new ArrayList<>(meta1.getLore()) : new ArrayList<>();
        List<String> lore2 = meta2.hasLore() ? new ArrayList<>(meta2.getLore()) : new ArrayList<>();

        String worthLineTemplate = main.getMessagesConfig().getString("sell.lore_worth", "&7Worth: &a$%price%");
        String worthPrefix = "";
        if (worthLineTemplate.contains("%price%")) {
            String[] split = worthLineTemplate.split("%price%");
            worthPrefix = (split.length > 0) ? ColorUtils.stripColor(split[0]) : "";
        }

        final String finalWorthPrefix = worthPrefix;
        if (!finalWorthPrefix.isEmpty()) {
            lore1.removeIf(line -> ColorUtils.stripColor(line).startsWith(finalWorthPrefix));
        }

        if (!lore1.equals(lore2)) {
            return false;
        }

        boolean hasModel1 = meta1.hasCustomModelData();
        boolean hasModel2 = meta2.hasCustomModelData();
        if (hasModel1 != hasModel2) {
            return false;
        }
        if (hasModel1 && meta1.getCustomModelData() != meta2.getCustomModelData()) {
            return false;
        }

        return true;
    }

    private double getCustomItemPrice(ItemStack itemStack) {
        if (customItemPrices == null) return 0.0;
        for (Map.Entry<ItemStack, Double> entry : customItemPrices.entrySet()) {
            if (isSimilarCustomItem(itemStack, entry.getKey())) {
                return entry.getValue();
            }
        }
        return 0.0;
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

    public Map<String, Double> getAllPricedItems() {
        Map<String, Double> allPricedItems = new HashMap<>();
        for (ItemIdentifier.ItemType type : ItemIdentifier.ItemType.values()) {
            allPricedItems.putAll(getAllPricesForType(type));
        }
        return allPricedItems;
    }
}

