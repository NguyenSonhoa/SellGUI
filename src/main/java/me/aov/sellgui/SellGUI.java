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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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
    private Inventory menu;
    private ItemStack confirmItem;
    private int sellItemSlot;

    public SellGUI(SellGUIMain main, Player p) {
        this.main = main;
        this.player = p;
        this.createItems();
        this.createMenu();
        this.addCustomItems();
        p.openInventory(this.menu);
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
            System.out.println(this.main.getCustomMenuItemsConfig().getKeys(false));
            ItemStack customItem = new ItemStack(Material.valueOf(this.main.getCustomMenuItemsConfig().getString(itemPath + ".material")));
            ItemMeta itemMeta = customItem.getItemMeta();
            if (!this.main.getCustomMenuItemsConfig().getString(itemPath + ".name").isEmpty()) {
                itemMeta.setDisplayName(color(this.main.getCustomMenuItemsConfig().getString(itemPath + ".name")));
            }

            if (this.main.getCustomMenuItemsConfig().getBoolean(itemPath + ".glimmer")) {
                itemMeta.addEnchant(Enchantment.INFINITY, 1, false);
                itemMeta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
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
                String var10001 = command.replaceAll("%player%", this.player.getName());
                sb.append(var10001).append(";");
            }

            sb.deleteCharAt(sb.length() - 1);
            itemMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, sb.toString());
            customItem.setItemMeta(itemMeta);
            this.menu.setItem(this.main.getCustomMenuItemsConfig().getInt(itemPath + ".slot"), customItem);
        }

    }

    public void addSellItem() {
        String s = this.main.getConfig().getString("menu-filler-location");
        String z = this.main.getConfig().getString("sell-item-location");
        assert s != null;
        if (!s.equalsIgnoreCase("none") && !s.equalsIgnoreCase("round")) {
            if (s.equalsIgnoreCase("bottom")) {
                this.bottom(z);
            } else if (s.equalsIgnoreCase("left")) {
                this.left(z);
            } else if (s.equalsIgnoreCase("right")) {
                this.right(z);
            } else if (s.equalsIgnoreCase("top")) {
                assert z != null;
                if (z.equalsIgnoreCase("left")) {
                    this.menu.setItem(0, sellItem);
                } else if (z.equalsIgnoreCase("middle")) {
                    this.menu.setItem(4, sellItem);
                } else if (z.equalsIgnoreCase("right")) {
                    this.menu.setItem(8, sellItem);
                }
            }
        } else if (Objects.requireNonNull(this.main.getConfig().getString("sell-item-side")).equalsIgnoreCase("bottom")) {
            this.bottom(z);
        } else if (Objects.requireNonNull(this.main.getConfig().getString("sell-item-side")).equalsIgnoreCase("top")) {
            this.top(z);
        } else if (Objects.requireNonNull(this.main.getConfig().getString("sell-item-side")).equalsIgnoreCase("left")) {
            this.left(z);
        } else if (Objects.requireNonNull(this.main.getConfig().getString("sell-item-side")).equalsIgnoreCase("right")) {
            this.right(z);
        }

        this.makeConfirmItem();
    }

    private void right(String z) {
        if (z.equalsIgnoreCase("left")) {
            this.menu.setItem(this.menu.getSize() - 1, (ItemStack)null);
            this.menu.setItem(this.menu.getSize() - 1, sellItem);
            this.sellItemSlot = this.menu.getSize() - 1;
        } else if (z.equalsIgnoreCase("middle")) {
            this.menu.setItem(8 + 9 * this.menu.getSize() / 9 / 2, (ItemStack)null);
            this.menu.setItem(8 + 9 * this.menu.getSize() / 9 / 2, sellItem);
            this.sellItemSlot = 8 + 9 * this.menu.getSize() / 9 / 2;
        } else if (z.equalsIgnoreCase("right")) {
            this.menu.setItem(8, (ItemStack)null);
            this.menu.setItem(8, sellItem);
            this.sellItemSlot = 8;
        }

    }

    private void top(String z) {
        if (z.equalsIgnoreCase("left")) {
            this.menu.setItem(0, (ItemStack)null);
            this.menu.setItem(0, sellItem);
            this.sellItemSlot = 0;
        } else if (z.equalsIgnoreCase("middle")) {
            this.menu.setItem(4, (ItemStack)null);
            this.menu.setItem(4, sellItem);
            this.sellItemSlot = 4;
        } else if (z.equalsIgnoreCase("right")) {
            this.menu.setItem(8, (ItemStack)null);
            this.menu.setItem(8, sellItem);
            this.sellItemSlot = 8;
        }

    }

    private void left(String z) {
        if (z.equalsIgnoreCase("left")) {
            this.menu.setItem(0, (ItemStack)null);
            this.menu.setItem(0, sellItem);
            this.sellItemSlot = 0;
        } else if (z.equalsIgnoreCase("middle")) {
            this.menu.setItem(this.menu.getSize() / 9 / 2 * 9, (ItemStack)null);
            this.menu.setItem(this.menu.getSize() / 9 / 2 * 9, sellItem);
            this.sellItemSlot = 9 * this.menu.getSize() / 9 / 2;
        } else if (z.equalsIgnoreCase("right")) {
            this.menu.setItem(this.menu.getSize() - 9, (ItemStack)null);
            this.menu.setItem(this.menu.getSize() - 9, sellItem);
            this.sellItemSlot = this.menu.getSize() - 9;
        }

    }

    private void bottom(String z) {
        if (z.equalsIgnoreCase("middle")) {
            this.menu.setItem(this.menu.getSize() - 5, (ItemStack)null);
            this.menu.setItem(this.menu.getSize() - 5, sellItem);
            this.sellItemSlot = this.menu.getSize() - 5;
        } else if (z.equalsIgnoreCase("left")) {
            this.menu.setItem(this.menu.getSize() - 9, (ItemStack)null);
            this.menu.setItem(this.menu.getSize() - 9, sellItem);
            this.sellItemSlot = this.menu.getSize() - 9;
        } else if (z.equalsIgnoreCase("right")) {
            this.menu.setItem(this.menu.getSize() - 1, (ItemStack)null);
            this.menu.setItem(this.menu.getSize() - 1, sellItem);
            this.sellItemSlot = this.menu.getSize() - 1;
        }

    }

    private void createItems() {
        if (this.menuTitle == null || sellItem == null || filler == null) {
            this.menuTitle = this.main.getLangConfig().getString("menu-title");
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
            for(i = this.menu.getSize() - 9; i < this.menu.getSize(); ++i) {
                this.menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("left")) {
            for(i = 0; i < this.menu.getSize(); i += 9) {
                this.menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("right")) {
            for(i = 8; i < this.menu.getSize(); i += 9) {
                this.menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("top")) {
            for(i = 0; i < 9; ++i) {
                this.menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("round")) {
            for(i = this.menu.getSize() - 9; i < this.menu.getSize(); ++i) {
                this.menu.setItem(i, filler);
            }

            for(i = 0; i < this.menu.getSize(); i += 9) {
                this.menu.setItem(i, filler);
            }

            for(i = 8; i < this.menu.getSize(); i += 9) {
                this.menu.setItem(i, filler);
            }

            for(i = 0; i < 9; ++i) {
                this.menu.setItem(i, filler);
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
        HashMap<ItemStack, Integer> hashMap = new HashMap();
        ItemStack[] var2 = this.getMenu().getContents();
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            ItemStack i = var2[var4];
            if (i != null && !InventoryListeners.sellGUIItem(i, this.player)) {
                ItemStack tempItemStack = i.clone();
                tempItemStack.setAmount(1);
                if (!hashMap.containsKey(tempItemStack)) {
                    hashMap.put(tempItemStack, i.getAmount());
                } else {
                    int amount = (Integer)hashMap.get(tempItemStack);
                    hashMap.put(tempItemStack, amount + i.getAmount());
                }
            }
        }

        ArrayList<String> lore = new ArrayList();
        Iterator var9 = hashMap.keySet().iterator();

        String var12;
        String var10001;
        FileConfiguration var10004;
        while(var9.hasNext()) {
            ItemStack i = (ItemStack)var9.next();
            double var11;
            Object var10003;
            if (this.main.getConfig().getBoolean("round-places")) {
                var10001 = this.main.getLangConfig().getString("item-total-format").replaceAll("%item%", this.getItemName(i));
                var10003 = hashMap.get(i);
                var10001 = var10001.replaceAll("%amount%", "" + var10003);
                var11 = this.getPrice(i);
                var10001 = var10001.replaceAll("%price%", "" + var11);
                var12 = (new BigDecimal((double)i.getAmount() * this.getPrice(i))).toPlainString();
                var10004 = this.main.getConfig();
                lore.add(color(var10001.replaceAll("%total%", "" + roundString(var12, var10004.getInt("places-to-round")))));
            } else {
                var10001 = this.main.getLangConfig().getString("item-total-format").replaceAll("%item%", this.getItemName(i));
                var10003 = hashMap.get(i);
                var10001 = var10001.replaceAll("%amount%", "" + var10003);
                var11 = this.getPrice(i);
                var10001 = var10001.replaceAll("%price%", "" + var11);
                var11 = (double)i.getAmount();
                double var13 = this.getPrice(i);
                lore.add(color(var10001.replaceAll("%total%", "" + var11 * var13)));
            }
        }

        if (this.main.getConfig().getBoolean("round-places")) {
            var10001 = this.main.getLangConfig().getString("total-format");
            var12 = "" + this.getTotal(this.menu);
            var10004 = this.main.getConfig();
            lore.add(color(var10001.replaceAll("%total%", "" + roundString(var12, var10004.getInt("places-to-round")))));
        } else {
            lore.add(color(this.main.getLangConfig().getString("total-format").replaceAll("%total%", "" + this.getTotal(this.menu))));
        }

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
            if (!InventoryListeners.sellGUIItem(itemStack, this.player) && itemStack != null) {
                total += this.getPrice(itemStack) * (double)itemStack.getAmount();
            }
        }

        return total;
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
        this.main.getEcon().depositPlayer(this.player, this.getTotal(inventory));
        Player var10000;
        String var10001;
        double var10003;
        if (this.main.getConfig().getBoolean("round-places")) {
            var10000 = this.player;
            var10001 = this.main.getLangConfig().getString("sold-message");
            var10003 = this.getTotal(inventory);
            FileConfiguration var10004 = this.main.getConfig();
            var10000.sendMessage(color(var10001.replaceAll("%total%", "" + round(var10003, var10004.getInt("places-to-round")))));
        } else {
            var10000 = this.player;
            var10001 = this.main.getLangConfig().getString("sold-message");
            var10003 = this.getTotal(inventory);
            var10000.sendMessage(color(var10001.replaceAll("%total%", "" + var10003)));
        }

        ItemStack[] var2 = inventory.getContents();
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            ItemStack itemStack = var2[var4];
            if (itemStack != null && !InventoryListeners.sellGUIItem(itemStack, this.player) && this.getPrice(itemStack) != 0.0) {
                if (this.main.getConfig().getBoolean("log-transactions")) {
                    this.logSell(itemStack);
                }

                inventory.remove(itemStack);
            }
        }

        if (this.main.getConfig().getBoolean("close-after-sell")) {
            InventoryListeners.dropItems(this.getMenu(), this.player);
            this.player.closeInventory();
            SellCommand.getSellGUIs().remove(this);
        } else {
            this.addSellItem();
        }

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
        return this.menuTitle;
    }

    public Inventory getMenu() {
        return this.menu;
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
