package me.aov.sellgui.managers;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.item.ItemTag;
import me.aov.sellgui.SellGUIMain;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class NBTPriceManager {

    private final SellGUIMain main;
    private final Random random;

    private static final String PRICE_NBT_KEY = "sellgui:price";
    private static final String PRICE_TYPE_NBT_KEY = "sellgui:price_type";

    private static final String PRICE_TYPE_FIXED = "FIXED";
    private static final String PRICE_TYPE_RANDOM = "RANDOM";

    public NBTPriceManager(SellGUIMain main) {
        this.main = main;
        this.random = new Random();
    }

    public ItemStack setFixedPrice(ItemStack itemStack, double price) {
        if (itemStack == null) return itemStack;

        try {
            NBTItem nbtItem = NBTItem.get(itemStack);
            nbtItem.addTag(new ItemTag(PRICE_NBT_KEY, price));
            nbtItem.addTag(new ItemTag(PRICE_TYPE_NBT_KEY, PRICE_TYPE_FIXED));

            ItemStack result = nbtItem.toItem();
            return updateWorthLore(result, price);
        } catch (Exception e) {
            main.getLogger().warning("Failed to set fixed price: " + e.getMessage());
            return itemStack;
        }
    }

    public ItemStack setRandomPrice(ItemStack itemStack, double minPrice, double maxPrice) {
        if (itemStack == null) return itemStack;

        try {
            double finalPrice = calculateRandomPrice(minPrice, maxPrice);

            NBTItem nbtItem = NBTItem.get(itemStack);
            nbtItem.addTag(new ItemTag(PRICE_NBT_KEY, finalPrice));
            nbtItem.addTag(new ItemTag(PRICE_TYPE_NBT_KEY, PRICE_TYPE_RANDOM));
            nbtItem.addTag(new ItemTag("sellgui:min_price", minPrice));
            nbtItem.addTag(new ItemTag("sellgui:max_price", maxPrice));

            ItemStack result = nbtItem.toItem();
            return updateWorthLore(result, finalPrice);
        } catch (Exception e) {
            main.getLogger().warning("Failed to set random price: " + e.getMessage());
            return itemStack;
        }
    }

    private double calculateRandomPrice(double minPrice, double maxPrice) {
        if (minPrice >= maxPrice) {
            return minPrice;
        }
        return minPrice + (random.nextDouble() * (maxPrice - minPrice));
    }

    public double getPriceFromNBT(ItemStack itemStack) {
        if (itemStack == null) return 0.0;

        try {
            NBTItem nbtItem = NBTItem.get(itemStack);
            if (nbtItem.hasTag(PRICE_NBT_KEY)) {
                return nbtItem.getDouble(PRICE_NBT_KEY);
            }
        } catch (Exception e) {

        }

        return 0.0;
    }

    public boolean hasNBTPrice(ItemStack itemStack) {
        if (itemStack == null) return false;

        try {
            NBTItem nbtItem = NBTItem.get(itemStack);
            return nbtItem.hasTag(PRICE_NBT_KEY);
        } catch (Exception e) {
            return false;
        }
    }

    public String getPriceType(ItemStack itemStack) {
        if (itemStack == null) return null;

        try {
            NBTItem nbtItem = NBTItem.get(itemStack);
            if (nbtItem.hasTag(PRICE_TYPE_NBT_KEY)) {
                return nbtItem.getString(PRICE_TYPE_NBT_KEY);
            }
        } catch (Exception e) {

        }

        return null;
    }

    private ItemStack updateWorthLore(ItemStack itemStack, double price) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return itemStack;
        }

        ItemMeta meta = itemStack.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        lore.removeIf(line -> ChatColor.stripColor(line).startsWith("Worth:"));

        String worthLine = ChatColor.GOLD + "Worth: " + ChatColor.GREEN + "$" + String.format("%.2f", price);
        lore.add(worthLine);

        meta.setLore(lore);
        itemStack.setItemMeta(meta);

        return itemStack;
    }

    public ItemStack removePricing(ItemStack itemStack) {
        if (itemStack == null) return itemStack;

        try {
            NBTItem nbtItem = NBTItem.get(itemStack);
            nbtItem.removeTag(PRICE_NBT_KEY);
            nbtItem.removeTag(PRICE_TYPE_NBT_KEY);
            nbtItem.removeTag("sellgui:min_price");
            nbtItem.removeTag("sellgui:max_price");

            ItemStack result = nbtItem.toItem();

            if (result.hasItemMeta()) {
                ItemMeta meta = result.getItemMeta();
                if (meta.hasLore()) {
                    List<String> lore = new ArrayList<>(meta.getLore());
                    lore.removeIf(line -> ChatColor.stripColor(line).startsWith("Worth:"));
                    meta.setLore(lore);
                    result.setItemMeta(meta);
                }
            }

            return result;
        } catch (Exception e) {
            main.getLogger().warning("Failed to remove pricing: " + e.getMessage());
            return itemStack;
        }
    }

    public double[] getRandomPriceRange(ItemStack itemStack) {
        if (itemStack == null) return null;

        try {
            NBTItem nbtItem = NBTItem.get(itemStack);
            if (nbtItem.hasTag("sellgui:min_price") && nbtItem.hasTag("sellgui:max_price")) {
                return new double[]{
                        nbtItem.getDouble("sellgui:min_price"),
                        nbtItem.getDouble("sellgui:max_price")
                };
            }
        } catch (Exception e) {

        }

        return null;
    }

    public boolean isAvailable() {
        try {
            Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public ItemStack regenerateRandomPrice(ItemStack itemStack) {
        if (itemStack == null) return itemStack;

        double[] range = getRandomPriceRange(itemStack);
        if (range != null) {
            return setRandomPrice(itemStack, range[0], range[1]);
        }

        return itemStack;
    }
}