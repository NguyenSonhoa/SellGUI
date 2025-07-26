package me.aov.sellgui.managers;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.utils.ItemIdentifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages item prices for different item types (Vanilla, MMOItems, Nexo)
 */
public class PriceManager {
    
    private final SellGUIMain main;
    
    public PriceManager(SellGUIMain main) {
        this.main = main;
    }
    
    /**
     * Sets the price for an item
     * @param itemStack The item to set price for
     * @param price The price to set
     * @return true if successful, false otherwise
     */
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
                case VANILLA:
                    return setVanillaPrice(itemStack, price);
                    
                case MMOITEMS:
                    return setMMOItemPrice(itemStack, price);
                    
                case NEXO:
                    return setNexoPrice(itemStack, price);
                    
                default:
                    return false;
            }
        } catch (Exception e) {
            main.getLogger().warning("Failed to set price for item: " + identifier + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the price for an item
     * @param itemStack The item to get price for
     * @return The price, or 0.0 if not found
     */
    public double getItemPrice(ItemStack itemStack) {
        if (itemStack == null) {
            return 0.0;
        }
        
        ItemIdentifier.ItemType type = ItemIdentifier.getItemType(itemStack);
        
        switch (type) {
            case VANILLA:
                return getVanillaPrice(itemStack);
                
            case MMOITEMS:
                return getMMOItemPrice(itemStack);
                
            case NEXO:
                return getNexoPrice(itemStack);
                
            default:
                return 0.0;
        }
    }
    
    /**
     * Removes the price for an item
     * @param itemStack The item to remove price for
     * @return true if successful, false otherwise
     */
    public boolean removeItemPrice(ItemStack itemStack) {
        return setItemPrice(itemStack, 0.0);
    }
    
    // Vanilla item price methods
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

    // Helper methods to get config files
    private File getItemPricesFile() {
        return new File(main.getDataFolder(), "itemprices.yml");
    }

    private File getMMOItemsPricesFile() {
        return new File(main.getDataFolder(), "mmoitems.yml");
    }

    private File getNexoPricesFile() {
        return new File(main.getDataFolder(), "nexo.yml");
    }
    
    private double getVanillaPrice(ItemStack itemStack) {
        return main.getItemPricesConfig().getDouble(itemStack.getType().name(), 0.0);
    }
    
    // MMOItems price methods
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
            
            // Update loaded prices cache
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
    
    // Nexo item price methods
    private boolean setNexoPrice(ItemStack itemStack, double price) {
        try {
            String identifier = ItemIdentifier.getItemIdentifier(itemStack);
            if (identifier == null || !identifier.startsWith("NEXO:")) {
                return false;
            }
            
            String nexoId = identifier.substring("NEXO:".length());
            main.getNexoPricesFileConfig().set("nexo." + nexoId, price);
            main.getNexoPricesFileConfig().save(getNexoPricesFile());
            
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
        return main.getNexoPricesFileConfig().getDouble("nexo." + nexoId, 0.0);
    }
    
    /**
     * Gets all items with prices for a specific type
     * @param type The item type to get prices for
     * @return Map of item identifiers to prices
     */
    public Map<String, Double> getAllPricesForType(ItemIdentifier.ItemType type) {
        Map<String, Double> prices = new HashMap<>();
        
        switch (type) {
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
                            prices.put("NEXO:" + key, price); // Keep original case for Nexo IDs
                        }
                    }
                }
                break;
        }
        
        return prices;
    }
}
