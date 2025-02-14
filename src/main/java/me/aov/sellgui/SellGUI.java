package me.aov.sellgui;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

import me.aov.sellgui.commands.CustomItemsCommand;
import me.aov.sellgui.commands.SellCommand;
import me.aov.sellgui.listeners.InventoryListeners;
import org.apache.commons.lang.WordUtils;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;

public class SellGUI implements Listener {
    private final SellGUIMain main;
    private final Player player;
    private static ItemStack sellItem;
    private static ItemStack filler;
    private String menuTitle;
    private static Inventory menu;
    private ItemStack confirmItem;
    private int sellItemSlot;

    public SellGUI(SellGUIMain main, Player p) {
        this.main = main;
        this.player = p;
        this.createItems();
        this.   createMenu();
        this.addCustomItems();
        p.openInventory(menu);
    }

    private void createMenu() {
        this.menu = Bukkit.createInventory((InventoryHolder)null, this.main.getConfig().getInt("menu-size"), color(this.main.getLangConfig().getString("menu-title")));
        this.addFiller(Objects.requireNonNull(this.main.getConfig().getString("menu-filler-location")));
        this.addSellItem();
    }


    private boolean isSellItem(ItemStack item) {
        if (item == null || item.getType() != sellItem.getType()) return false;
        ItemMeta meta1 = item.getItemMeta();
        ItemMeta meta2 = sellItem.getItemMeta();

        return meta1.hasDisplayName() == meta2.hasDisplayName() &&
                (!meta1.hasDisplayName() || meta1.getDisplayName().equals(meta2.getDisplayName())) &&
                meta1.hasLore() == meta2.hasLore() &&
                (!meta1.hasLore() || meta1.getLore().equals(meta2.getLore())) &&
                meta1.hasCustomModelData() == meta2.hasCustomModelData() &&
                (!meta1.hasCustomModelData() || meta1.getCustomModelData() == meta2.getCustomModelData());
    }

    private boolean isConfirmItem(ItemStack item) {
        if (item == null || item.getType() != confirmItem.getType()) return false;
        ItemMeta meta1 = item.getItemMeta();
        ItemMeta meta2 = confirmItem.getItemMeta();

        return meta1.hasDisplayName() == meta2.hasDisplayName() &&
                (!meta1.hasDisplayName() || meta1.getDisplayName().equals(meta2.getDisplayName())) &&
                meta1.hasLore() == meta2.hasLore() &&
                (!meta1.hasLore() || meta1.getLore().equals(meta2.getLore())) &&
                meta1.hasCustomModelData() == meta2.hasCustomModelData() &&
                (!meta1.hasCustomModelData() || meta1.getCustomModelData() == meta2.getCustomModelData());
    }

    private boolean isFillerItem(ItemStack item) {
        if (item == null || item.getType() != filler.getType()) return false;
        ItemMeta meta1 = item.getItemMeta();
        ItemMeta meta2 = filler.getItemMeta();

        return meta1.hasCustomModelData() == meta2.hasCustomModelData() &&
                (!meta1.hasCustomModelData() || meta1.getCustomModelData() == meta2.getCustomModelData());
    }
    private void addCustomItems() {
        for (String itemPath : this.main.getCustomMenuItemsConfig().getKeys(false)) {
            ItemStack customItem = new ItemStack(Material.valueOf(this.main.getCustomMenuItemsConfig().getString(itemPath + ".material")));
            ItemMeta itemMeta = customItem.getItemMeta();

            if (this.main.getCustomMenuItemsConfig().contains(itemPath + ".custom-model-data")) {
                itemMeta.setCustomModelData(this.main.getCustomMenuItemsConfig().getInt(itemPath + ".custom-model-data"));
            }

            if (!this.main.getCustomMenuItemsConfig().getString(itemPath + ".name").isEmpty()) {
                itemMeta.setDisplayName(color(this.main.getCustomMenuItemsConfig().getString(itemPath + ".name")));
            }

            if (this.main.getCustomMenuItemsConfig().getBoolean(itemPath + ".glimmer")) {
                itemMeta.addEnchant(Enchantment.INFINITY, 1, false);
                itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            if (!this.main.getCustomMenuItemsConfig().getStringList(itemPath + ".lore").isEmpty()) {
                ArrayList<String> lore = new ArrayList<>();
                for (String s : this.main.getCustomMenuItemsConfig().getStringList(itemPath + ".lore")) {
                    lore.add(color(s));
                }
                itemMeta.setLore(lore);
            }

            NamespacedKey key = new NamespacedKey(this.main, "custom-menu-item");
            StringBuilder sb = new StringBuilder();
            for (String command : this.main.getCustomMenuItemsConfig().getStringList(itemPath + ".commands")) {
                sb.append(command.replaceAll("%player%", this.player.getName())).append(";");
            }
            itemMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, sb.toString());
            customItem.setItemMeta(itemMeta);
            menu.setItem(this.main.getCustomMenuItemsConfig().getInt(itemPath + ".slot"), customItem);
        }
    }

    public void addSellItem() {
        int slot = this.main.getConfig().getInt("sell-item-slot", 4);  //
        int maxSlot = menu.getSize() - 1;

        if (slot < 0 || slot > maxSlot) {
            this.player.sendMessage(ChatColor.RED + "Invalid sell-item slot! It must be between 0 and " + maxSlot + ".");
            return;
        }

        menu.setItem(slot, sellItem);
        this.sellItemSlot = slot;
        this.makeConfirmItem();
    }

    private void right(String z) {
        if (z.equalsIgnoreCase("left")) {
            menu.setItem(menu.getSize() - 1, (ItemStack)null);
            menu.setItem(menu.getSize() - 1, sellItem);
            this.sellItemSlot = menu.getSize() - 1;
        } else if (z.equalsIgnoreCase("middle")) {
            menu.setItem(8 + 9 * menu.getSize() / 9 / 2, (ItemStack)null);
            menu.setItem(8 + 9 * menu.getSize() / 9 / 2, sellItem);
            this.sellItemSlot = 8 + 9 * menu.getSize() / 9 / 2;
        } else if (z.equalsIgnoreCase("right")) {
            menu.setItem(8, (ItemStack)null);
            menu.setItem(8, sellItem);
            this.sellItemSlot = 8;
        }

    }


    private void createItems() {
        if (menuTitle == null || sellItem == null || filler == null) {
            menuTitle = this.main.getLangConfig().getString("menu-title");
            NamespacedKey key = new NamespacedKey(this.main, "sellgui-item");

            //
            sellItem = new ItemStack(Objects.requireNonNull(Material.getMaterial(Objects.requireNonNull(this.main.getConfig().getString("sell-item")))));
            ItemMeta sellItemMeta = sellItem.getItemMeta();
            sellItemMeta.setDisplayName(color(this.main.getLangConfig().getString("sell-item-name")));

            ArrayList<String> lore = new ArrayList<>();
            for (String s : this.main.getLangConfig().getStringList("sell-item-lore")) {
                lore.add(color(s));
            }
            sellItemMeta.setLore(lore);

            if (this.main.getConfig().getBoolean("sell-item-glimmer")) {
                sellItemMeta.addEnchant(Enchantment.INFINITY, 1, false);
                sellItemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            // check config to get model data
            if (this.main.getConfig().contains("sell-item-custom-model-data")) {
                sellItemMeta.setCustomModelData(this.main.getConfig().getInt("sell-item-custom-model-data"));
            }

            sellItemMeta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            sellItem.setItemMeta(sellItemMeta);

            // create filler
            filler = new ItemStack(Material.valueOf(this.main.getConfig().getString("menu-filler-type")));
            ItemMeta fillerMeta = filler.getItemMeta();
            fillerMeta.setDisplayName(" ");

            // check again
            if (this.main.getConfig().contains("menu-filler-custom-model-data")) {
                fillerMeta.setCustomModelData(this.main.getConfig().getInt("menu-filler-custom-model-data"));
            }

            fillerMeta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            filler.setItemMeta(fillerMeta);
        }
    }

    private void addFiller(String s) {
        int i;
        if (s.equalsIgnoreCase("bottom")) {
            for(i = menu.getSize() - 9; i < menu.getSize(); ++i) {
                menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("left")) {
            for(i = 0; i < menu.getSize(); i += 9) {
                menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("right")) {
            for(i = 8; i < menu.getSize(); i += 9) {
                menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("top")) {
            for(i = 0; i < 9; ++i) {
                menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("round")) {
            for(i = menu.getSize() - 9; i < menu.getSize(); ++i) {
                menu.setItem(i, filler);
            }

            for(i = 0; i < menu.getSize(); i += 9) {
                menu.setItem(i, filler);
            }

            for(i = 8; i < menu.getSize(); i += 9) {
                menu.setItem(i, filler);
            }

            for(i = 0; i < 9; ++i) {
                menu.setItem(i, filler);
            }
        }

    }

    public void makeConfirmItem() {
        this.confirmItem = new ItemStack(Objects.requireNonNull(Material.matchMaterial(this.main.getConfig().getString("confirm-item"))));
        ItemMeta itemMeta = this.confirmItem.getItemMeta();
        itemMeta.setDisplayName(color(this.main.getLangConfig().getString("confirm-item-name")));

        if (this.main.getConfig().getBoolean("confirm-item-glimmer")) {
            itemMeta.addEnchant(Enchantment.POWER, 1, false);
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        itemMeta.setLore(this.makeLore());

        // add model data
        if (this.main.getConfig().contains("confirm-item-custom-model-data")) {
            itemMeta.setCustomModelData(this.main.getConfig().getInt("confirm-item-custom-model-data"));
        }

        itemMeta.getPersistentDataContainer().set(new NamespacedKey(this.main, "sellgui-item"), PersistentDataType.BYTE, (byte) 1);
        this.confirmItem.setItemMeta(itemMeta);
    }
    public ArrayList<String> makeLore() {
        HashMap<String, Integer> itemCounts = new HashMap<>();
        HashMap<String, Double> itemPrices = new HashMap<>();
        ItemStack[] contents = this.getMenu().getContents();

        for (ItemStack item : contents) {
            if (item != null && !InventoryListeners.sellGUIItem(item, this.player) && !isCustomMenuItem(item)) {
                String itemName = getItemName(item);
                double price = getPrice(item);

                itemCounts.put(itemName, itemCounts.getOrDefault(itemName, 0) + item.getAmount());
                itemPrices.put(itemName, price);
            }
        }

        ArrayList<String> lore = new ArrayList<>();
        for (String itemName : itemCounts.keySet()) {
            String formatted = this.main.getLangConfig().getString("item-total-format")
                    .replace("%item%", itemName)
                    .replace("%amount%", String.valueOf(itemCounts.get(itemName)))
                    .replace("%price%", String.valueOf(itemPrices.get(itemName)))
                    .replace("%total%", String.valueOf(itemPrices.get(itemName) * itemCounts.get(itemName)));
            lore.add(ChatColor.translateAlternateColorCodes('&', formatted));
        }

        String totalFormatted = this.main.getLangConfig().getString("total-format")
                .replace("%total%", String.valueOf(getTotal(this.menu)));
        lore.add(ChatColor.translateAlternateColorCodes('&', totalFormatted));

        return lore;
    }

    public void setConfirmItem() {
        this.menu.setItem(this.sellItemSlot, (ItemStack)null);
        this.menu.setItem(this.sellItemSlot, this.confirmItem);
    }

    public String getItemName(ItemStack itemStack) {
        return itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName() ? itemStack.getItemMeta().getDisplayName() : WordUtils.capitalizeFully(itemStack.getType().name().replace('_', ' '));
    }

    public double getPrice(ItemStack itemStack) {
        double price = 0.0;
        if (CustomItemsCommand.getPrice(itemStack) != -1.0) {
            return CustomItemsCommand.getPrice(itemStack);
        } else if (this.main.getConfig().getBoolean("prevent-custom-item-selling") && itemStack.hasItemMeta()) {
            return 0.0;
        } else {
            Iterator var13;
            PermissionAttachmentInfo pai;
            if (this.main.hasEssentials() && this.main.getConfig().getBoolean("use-essentials-price") && this.main.getEssentialsHolder().getEssentials() != null) {
                if (!this.main.getConfig().getBoolean("use-permission-bonuses-on-essentials")) {
                    return round(this.main.getEssentialsHolder().getPrice(itemStack).doubleValue(), this.main.getConfig().getInt("places-to-round"));
                } else {
                    double temp = round(this.main.getEssentialsHolder().getPrice(itemStack).doubleValue(), this.main.getConfig().getInt("places-to-round"));
                    var13 = this.player.getEffectivePermissions().iterator();

                    while(true) {
                        while(var13.hasNext()) {
                            pai = (PermissionAttachmentInfo)var13.next();
                            if (pai.getPermission().contains("sellgui.bonus.") && pai.getValue()) {
                                if (temp != 0.0) {
                                    temp += Double.parseDouble(pai.getPermission().replaceAll("sellgui.bonus.", ""));
                                }
                            } else if (pai.getPermission().contains("sellgui.multiplier.") && pai.getValue()) {
                                temp *= Double.parseDouble(pai.getPermission().replaceAll("sellgui.multiplier.", ""));
                            }
                        }

                        if (this.main.getConfig().getBoolean("round-places")) {
                            return round(temp, this.main.getConfig().getInt("places-to-round"));
                        }

                        return temp;
                    }
                }
            } else {
                ArrayList<String> flatBonus = new ArrayList<>();
                this.main.getItemPricesConfig().getStringList("flat-enchantment-bonus");

                for (String s : this.main.getItemPricesConfig().getStringList("flat-enchantment-bonus")) {
                    flatBonus.add(s);
                }

                ArrayList<String> multiplierBonus = new ArrayList<>();
                this.main.getItemPricesConfig().getStringList("multiplier-enchantment-bonus");
                var13 = this.main.getItemPricesConfig().getStringList("multiplier-enchantment-bonus").iterator();

                while(var13.hasNext()) {
                    String s = (String)var13.next();
                    multiplierBonus.add(s);
                }

                if (itemStack != null && !itemStack.getType().isAir() && this.main.getItemPricesConfig().contains(itemStack.getType().name())) {
                    price = this.main.getItemPricesConfig().getDouble(itemStack.getType().name());
                }

                if (itemStack != null && itemStack.getItemMeta().hasEnchants()) {
                    var13 = itemStack.getItemMeta().getEnchants().keySet().iterator();

                    Iterator var8;
                    String s;
                    String[] temp2;
                    Enchantment enchantment;
                    String var10000;
                    int var10001;
                    while(var13.hasNext()) {
                        enchantment = (Enchantment)var13.next();
                        var8 = flatBonus.iterator();

                        while(var8.hasNext()) {
                            s = (String)var8.next();
                            temp2 = s.split(":");
                            if (temp2[0].equalsIgnoreCase(enchantment.getKey().getKey())) {
                                var10000 = temp2[1];
                                var10001 = itemStack.getEnchantmentLevel(enchantment);
                                if (var10000.equalsIgnoreCase("" + var10001)) {
                                    price += Double.parseDouble(temp2[2]);
                                }
                            }
                        }
                    }

                    var13 = itemStack.getItemMeta().getEnchants().keySet().iterator();

                    while(var13.hasNext()) {
                        enchantment = (Enchantment)var13.next();
                        var8 = multiplierBonus.iterator();

                        while(var8.hasNext()) {
                            s = (String)var8.next();
                            temp2 = s.split(":");
                            if (temp2[0].equalsIgnoreCase(enchantment.getKey().getKey())) {
                                var10000 = temp2[1];
                                var10001 = itemStack.getEnchantmentLevel(enchantment);
                                if (var10000.equalsIgnoreCase("" + var10001)) {
                                    price *= Double.parseDouble(temp2[2]);
                                }
                            }
                        }
                    }
                }

                var13 = this.player.getEffectivePermissions().iterator();

                while(true) {
                    while(var13.hasNext()) {
                        pai = (PermissionAttachmentInfo)var13.next();
                        if (pai.getPermission().contains("sellgui.bonus.") && pai.getValue()) {
                            if (price != 0.0) {
                                price += Double.parseDouble(pai.getPermission().replaceAll("sellgui.bonus.", ""));
                            }
                        } else if (pai.getPermission().contains("sellgui.multiplier.") && pai.getValue()) {
                            price *= Double.parseDouble(pai.getPermission().replaceAll("sellgui.multiplier.", ""));
                        }
                    }

                    if (this.main.getConfig().getBoolean("round-places")) {
                        return round(price, this.main.getConfig().getInt("places-to-round"));
                    }

                    return price;
                }
            }
        }
    }

    public double getTotal(Inventory inventory) {
        double total = 0.0;
        ItemStack[] var4 = inventory.getContents();
        int var5 = var4.length;
        for(int var6 = 0; var6 < var5; ++var6) {
            ItemStack itemStack = var4[var6];
            if (itemStack != null && !isCustomMenuItem(itemStack) && !InventoryListeners.sellGUIItem(itemStack, this.player)) {
                total += this.getPrice(itemStack) * (double)itemStack.getAmount();
            }
        }
        return total;
    }

    private boolean isCustomMenuItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(this.main, "custom-menu-item"), PersistentDataType.STRING);
    }
    public void logSell(ItemStack itemStack) {
        if (itemStack != null) {
            BufferedWriter writer = null;

            try {
                writer = new BufferedWriter(new FileWriter(this.getMain().getLog(), true));
                Date now = new Date();
                SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                Material var10001;
                if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                    var10001 = itemStack.getType();
                    writer.append("" + var10001 + "|" + itemStack.getItemMeta().getDisplayName() + "|" + itemStack.getAmount() + "|" + this.getPrice(itemStack) + "|" + this.getPlayer().getName() + "|" + format.format(now) + "\n");
                } else {
                    var10001 = itemStack.getType();
                    writer.append("" + var10001 + "|N\\A|" + itemStack.getAmount() + "|" + this.getPrice(itemStack) + "|" + this.getPlayer().getName() + "|" + format.format(now) + "\n");
                }

                writer.close();
            } catch (IOException var5) {
                var5.printStackTrace();
            }

        }
    }

    public void sellItems(Inventory inventory) {
        double total = getTotal(inventory);
        this.main.getEcon().depositPlayer(this.player, total);

        for (ItemStack item : inventory.getContents()) {
            if (item != null && !InventoryListeners.sellGUIItem(item, this.player) && !isCustomMenuItem(item)) {
                if (this.main.getConfig().getBoolean("log-transactions")) {
                    logSell(item);
                }
                inventory.remove(item);
            }
        }

        if (this.main.getConfig().getBoolean("close-after-sell")) {
            InventoryListeners.dropItems(this.getMenu(), this.player);
            this.player.closeInventory();
            SellCommand.getSellGUIs().remove(this);
        } else {
            addSellItem();
        }

        player.sendMessage(color(this.main.getLangConfig().getString("sold-message").replace("%total%", String.valueOf(total))));
    }

    public ItemStack getConfirmItem() {
        return this.confirmItem;
    }

    public Player getPlayer() {
        return this.player;
    }

    public static ItemStack getSellItem() {
        return sellItem;
    }

    public static ItemStack getFiller() {
        return filler;
    }

    public String getMenuTitle() {
        return menuTitle;
    }

    public Inventory getMenu() {
        return menu;
    }

    public SellGUIMain getMain() {
        return this.main;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static double round(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        } else {
            BigDecimal bd = new BigDecimal("" + value);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }
    }

    public static double round(String value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        } else {
            BigDecimal bd = new BigDecimal(value);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }
    }

    public static String roundString(String value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        } else {
            BigDecimal bd = new BigDecimal("" + value);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
            return bd.toPlainString();
        }
    }
}
