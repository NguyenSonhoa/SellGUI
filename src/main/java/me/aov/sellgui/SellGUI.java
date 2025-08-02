package me.aov.sellgui;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTCompound;
import io.lumine.mythic.lib.api.item.NBTItem;
import me.aov.sellgui.commands.SellCommand;
import me.aov.sellgui.listeners.InventoryListeners;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.utils.ItemIdentifier;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
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

import javax.annotation.Nullable;

public class SellGUI implements Listener {
    private final SellGUIMain main;
    private final Player player;
    private static ItemStack sellItem;
    private static ItemStack filler;
    private String menuTitle;
    private static Inventory menu;
    private ItemStack confirmItem;
    private ItemStack noItemsItemStack;
    private int sellItemSlot;
    private int updateTaskId = -1;
    private boolean isConfirmMode = false;

    public SellGUI(SellGUIMain main, Player p) {
        this.main = main;
        this.player = p;
        this.createItems();
        this.createMenu();
        this.addCustomItems();
        p.openInventory(menu);
        startAutoUpdateTask();
    }

    private void createMenu() {
        org.bukkit.configuration.file.FileConfiguration guiConfig = this.main.getConfigManager() != null ?
                this.main.getConfigManager().getGUIConfig() : null;
        int size = 54;
        if (guiConfig != null) {
            size = guiConfig.getInt("sell_gui.size", 54);
        }

        menu = Bukkit.createInventory((InventoryHolder) null, size, color(menuTitle));

        this.addFillerFromConfig();
        this.addSellItem();
    }

    private boolean isSellItem(ItemStack item) {
        return item(item, sellItem);
    }

    private boolean item(ItemStack item, ItemStack sellItem) {
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
        return item(item, confirmItem);
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

        org.bukkit.configuration.file.FileConfiguration guiConfig = this.main.getConfigManager() != null ?
                this.main.getConfigManager().getGUIConfig() : null;

        int slot = 49;
        if (guiConfig != null) {
            slot = guiConfig.getInt("sell_gui.positions.sell-button", 49);
        }

        int maxSlot = menu.getSize() - 1;

        if (slot < 0 || slot > maxSlot) {
            this.player.sendMessage(ChatColor.RED + "Invalid sell-item slot! It must be between 0 and " + maxSlot + ".");
            return;
        }

        menu.setItem(slot, sellItem);
        this.sellItemSlot = slot;
        this.makeConfirmItem();

    }

    private void startAutoUpdateTask() {

        if (updateTaskId != -1) {
            this.main.getServer().getScheduler().cancelTask(updateTaskId);
        }

        long updateInterval = this.main.getConfig().getLong("performance.gui-update-interval", 20);

        updateTaskId = this.main.getServer().getScheduler().runTaskTimer(this.main, () -> {
            if (menu != null && player != null && player.isOnline() && !isConfirmMode) {
                updateSellItemTotal();
            } else if (!player.isOnline() || menu == null) {

                if (updateTaskId != -1) {
                    this.main.getServer().getScheduler().cancelTask(updateTaskId);
                    updateTaskId = -1;
                }
            }

        }, 0L, updateInterval).getTaskId();
    }

    public void updateSellItemTotal() {
        if (menu == null || isConfirmMode) {
            return;
        }

        double currentTotal = getTotal(menu);

        if (currentTotal > 0) {
            if (sellItem == null) return;

            ItemMeta meta = sellItem.getItemMeta();
            if (meta != null && meta.hasLore()) {
                java.util.List<String> lore = new java.util.ArrayList<>(meta.getLore());

                for (int i = 0; i < lore.size(); i++) {
                    String line = lore.get(i);
                    if (ChatColor.stripColor(line).contains("Total Value:")) {
                        boolean roundPrices = this.main.shouldRoundPrices();
                        double displayedTotal = currentTotal;
                        if (roundPrices) {
                            displayedTotal = Math.round(currentTotal);
                        }
                        lore.set(i, color("&eTotal Value: &a$" + String.format("%.2f", displayedTotal)));
                        break;
                    }
                }

                meta.setLore(lore);
                sellItem.setItemMeta(meta);

                if (sellItemSlot >= 0 && sellItemSlot < menu.getSize()) {
                    menu.setItem(sellItemSlot, sellItem);
                }
            }
        } else {
            if (noItemsItemStack != null && sellItemSlot >= 0 && sellItemSlot < menu.getSize()) {
                menu.setItem(sellItemSlot, noItemsItemStack);
            }
        }
    }

    public void updateButtonState() {
        if (menu == null) return;
        double currentTotal = getTotal(menu);
        if (currentTotal > 0) {
            if (!isConfirmMode) {
                makeConfirmItem();
                setConfirmItem();
            } else {
                makeConfirmItem();
                menu.setItem(sellItemSlot, confirmItem);
            }
        } else {
            if (isConfirmMode) {
                setSellItem();
            }
            updateSellItemTotal();
        }
    }

    private void right(String z) {
        if (z.equalsIgnoreCase("left")) {
            menu.setItem(menu.getSize() - 1, (ItemStack) null);
            menu.setItem(menu.getSize() - 1, sellItem);
            this.sellItemSlot = menu.getSize() - 1;
        } else if (z.equalsIgnoreCase("middle")) {
            menu.setItem(8 + 9 * menu.getSize() / 9 / 2, (ItemStack) null);
            menu.setItem(8 + 9 * menu.getSize() / 9 / 2, sellItem);
            this.sellItemSlot = 8 + 9 * menu.getSize() / 9 / 2;
        } else if (z.equalsIgnoreCase("right")) {
            menu.setItem(8, (ItemStack) null);
            menu.setItem(8, sellItem);
            this.sellItemSlot = 8;
        }

    }

    public void cleanup() {
        if (updateTaskId != -1) {
            this.main.getServer().getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
        }
    }

    private void createItems() {
        if (menuTitle == null || sellItem == null || filler == null) {

            org.bukkit.configuration.file.FileConfiguration guiConfig = this.main.getConfigManager() != null ?
                    this.main.getConfigManager().getGUIConfig() : null;

            if (guiConfig != null) {
                menuTitle = guiConfig.getString("sell_gui.title", "&6&l✦ &eSell GUI &6&l✦");
            } else {
                menuTitle = "&6&l✦ &eSell GUI &6&l✦";
            }

            NamespacedKey guiKey = new NamespacedKey(this.main, "sellgui");
            NamespacedKey actionKey = new NamespacedKey(this.main, "guiAction");

            String sellItemMaterial = "EMERALD";
            if (guiConfig != null) {
                sellItemMaterial = guiConfig.getString("sell_gui.items.sell_button.material", "EMERALD");
            }

            Material material = Material.getMaterial(sellItemMaterial);
            if (material == null) {
                material = Material.EMERALD;
                this.main.getLogger().warning("Invalid sell-item material: " + sellItemMaterial + ", using EMERALD");
            }
            sellItem = new ItemStack(material);
            ItemMeta sellItemMeta = sellItem.getItemMeta();
            if (sellItemMeta != null) {
                String itemName = "&a&lSell Items";
                if (guiConfig != null) {
                    itemName = guiConfig.getString("sell_gui.items.sell_button.name", "&a&lSell Items");
                }
                sellItemMeta.setDisplayName(color(itemName));

                ArrayList<String> lore = new ArrayList<>();
                if (guiConfig != null) {
                    List<String> configLore = guiConfig.getStringList("sell_gui.items.sell_button.lore");
                    if (configLore.isEmpty()) {
                        lore.add(color("&7Click to sell all items"));
                        lore.add(color("&7in this GUI"));
                        lore.add(color(""));
                        lore.add(color("&eTotal Value: &a$0.00"));
                    } else {
                        for (String s : configLore) {
                            lore.add(color(s));
                        }
                    }
                } else {
                    lore.add(color("&7Click to sell all items"));
                }
                sellItemMeta.setLore(lore);

                boolean addGlow = false;
                if (guiConfig != null) {
                    addGlow = guiConfig.getBoolean("sell_gui.items.sell_button.glow", true);
                }
                if (addGlow) {
                    sellItemMeta.addEnchant(Enchantment.INFINITY, 1, false);
                    sellItemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }

                if (guiConfig != null && guiConfig.contains("sell_gui.items.sell_button.custom_model_data")) {
                    int modelData = guiConfig.getInt("sell_gui.items.sell_button.custom_model_data");
                    if (modelData > 0) {
                        sellItemMeta.setCustomModelData(modelData);
                    }
                }
                sellItemMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
                sellItemMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "sell");
                sellItem.setItemMeta(sellItemMeta);
            }

            String fillerMaterial = "GRAY_STAINED_GLASS_PANE";
            if (guiConfig != null) {
                fillerMaterial = guiConfig.getString("sell_gui.items.filler.material", "GRAY_STAINED_GLASS_PANE");
            }

            Material fillerMat = Material.getMaterial(fillerMaterial);
            if (fillerMat == null) {
                fillerMat = Material.GRAY_STAINED_GLASS_PANE;
                this.main.getLogger().warning("Invalid filler material: " + fillerMaterial + ", using GRAY_STAINED_GLASS_PANE");
            }

            filler = new ItemStack(fillerMat);
            ItemMeta fillerMeta = filler.getItemMeta();
            if (fillerMeta != null) {
                String fillerName = " ";
                if (guiConfig != null) {
                    fillerName = guiConfig.getString("sell_gui.items.filler.name", " ");
                }
                fillerMeta.setDisplayName(color(fillerName));

                if (guiConfig != null && guiConfig.contains("sell_gui.items.filler.custom_model_data")) {
                    int modelData = guiConfig.getInt("sell_gui.items.filler.custom_model_data");
                    if (modelData > 0) {
                        fillerMeta.setCustomModelData(modelData);
                    }
                }

                fillerMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
                filler.setItemMeta(fillerMeta);
            }

            // Create the "No items to sell" item
            noItemsItemStack = new ItemStack(Material.BARRIER);
            ItemMeta noItemsMeta = noItemsItemStack.getItemMeta();
            if (noItemsMeta != null) {
                noItemsMeta.setDisplayName(color("&cNo items to sell!"));
                List<String> noItemLore = new ArrayList<>();
                noItemLore.add(color("&7Place items in the GUI"));
                noItemLore.add(color("&7to sell them."));
                noItemsMeta.setLore(noItemLore);
                noItemsMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
                noItemsItemStack.setItemMeta(noItemsMeta);
            }
        }
    }

    private void addFillerFromConfig() {
        org.bukkit.configuration.file.FileConfiguration guiConfig = this.main.getConfigManager() != null ?
                this.main.getConfigManager().getGUIConfig() : null;

        if (guiConfig != null) {

            List<Integer> fillerSlots = guiConfig.getIntegerList("sell_gui.positions.filler_slots");
            if (!fillerSlots.isEmpty()) {
                for (int slot : fillerSlots) {
                    if (slot >= 0 && slot < menu.getSize()) {
                        menu.setItem(slot, filler);
                    }
                }
                return;
            }
        }

        addFiller("border");
    }
    private ItemStack createSystemItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            if (lore != null) {
                meta.setLore(lore.stream().map(line -> ChatColor.translateAlternateColorCodes('&', line)).toList());
            }
            // Gắn tag nhận diện item hệ thống
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(SellGUIMain.getInstance(), "system_item"),
                    PersistentDataType.INTEGER,
                    1
            );
            item.setItemMeta(meta);
        }
        return item;
    }
    private void addFiller(String s) {
        int i;
        if (s.equalsIgnoreCase("bottom")) {
            for (i = menu.getSize() - 9; i < menu.getSize(); ++i) {
                menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("left")) {
            for (i = 0; i < menu.getSize(); i += 9) {
                menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("right")) {
            for (i = 8; i < menu.getSize(); i += 9) {
                menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("top")) {
            for (i = 0; i < 9; ++i) {
                menu.setItem(i, filler);
            }
        } else if (s.equalsIgnoreCase("round")) {
            for (i = menu.getSize() - 9; i < menu.getSize(); ++i) {
                menu.setItem(i, filler);
            }

            for (i = 0; i < menu.getSize(); i += 9) {
                menu.setItem(i, filler);
            }

            for (i = 8; i < menu.getSize(); i += 9) {
                menu.setItem(i, filler);
            }

            for (i = 0; i < 9; ++i) {
                menu.setItem(i, filler);
            }
        }

    }

    public void makeConfirmItem() {

        org.bukkit.configuration.file.FileConfiguration guiConfig = this.main.getConfigManager() != null ?
                this.main.getConfigManager().getGUIConfig() : null;

        String confirmMaterial = "GREEN_CONCRETE";
        if (guiConfig != null) {
            confirmMaterial = guiConfig.getString("sell_gui.items.confirm_button.material", "GREEN_CONCRETE");

        }

        Material material = Material.getMaterial(confirmMaterial);
        if (material == null) {
            material = Material.GREEN_CONCRETE;
            this.main.getLogger().warning("Invalid confirm-item material: " + confirmMaterial + ", using GREEN_CONCRETE");
        }
        this.confirmItem = new ItemStack(material);
        ItemMeta itemMeta = this.confirmItem.getItemMeta();
        if (itemMeta != null) {

            String itemName = "&a&lConfirm Sale";
            if (guiConfig != null) {
                itemName = guiConfig.getString("sell_gui.items.confirm_button.name", "&a&lConfirm Sale");
            }
            itemMeta.setDisplayName(color(itemName));

            boolean addGlow = false;
            if (guiConfig != null) {
                addGlow = guiConfig.getBoolean("sell_gui.items.confirm_button.glow", true);
            }
            if (addGlow) {
                itemMeta.addEnchant(Enchantment.POWER, 1, false);
                itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            itemMeta.setLore(this.makeLore());

            if (guiConfig != null && guiConfig.contains("sell_gui.items.confirm_button.custom_model_data")) {
                int modelData = guiConfig.getInt("sell_gui.items.confirm_button.custom_model_data");
                if (modelData > 0) {
                    itemMeta.setCustomModelData(modelData);
                }
            }

            NamespacedKey guiKey = new NamespacedKey(this.main, "sellgui");
            NamespacedKey actionKey = new NamespacedKey(this.main, "guiAction");
            itemMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
            itemMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "confirm");
            this.confirmItem.setItemMeta(itemMeta);
        }
    }

    public ArrayList<String> makeLore() {
        HashMap<String, Integer> itemCounts = new HashMap<>();
        HashMap<String, Double> itemTotals = new HashMap<>();
        HashMap<String, Boolean> itemNeedsEvaluation = new HashMap<>();
        ItemStack[] contents = this.getMenu().getContents();

        for (ItemStack item : contents) {
            if (item != null && !InventoryListeners.sellGUIItem(item, this.player) && !isCustomMenuItem(item)) {
                String itemName = getItemName(item);
                double price = getPrice(item, player);
                boolean needsEvaluation = main.getRandomPriceManager() != null &&
                        main.getRandomPriceManager().requiresEvaluation(item) &&
                        !main.getRandomPriceManager().isEvaluated(item);

                itemCounts.put(itemName, itemCounts.getOrDefault(itemName, 0) + item.getAmount());
                itemTotals.put(itemName, itemTotals.getOrDefault(itemName, 0.0) + (price * item.getAmount()));
                itemNeedsEvaluation.put(itemName, needsEvaluation);
            }
        }

        ArrayList<String> lore = new ArrayList<>();
        for (String itemName : itemCounts.keySet()) {

            org.bukkit.configuration.file.FileConfiguration guiConfig = this.main.getConfigManager() != null ?
                    this.main.getConfigManager().getGUIConfig() : null;
            String format = "&7%amount%x &f%item% &8= &e$%total%";
            if (guiConfig != null) {
                format = guiConfig.getString("sell_gui.item_total_format", format);
            }

            if (itemNeedsEvaluation.get(itemName)) {

                String evaluationFormat = "&7%amount%x &f%item% &c⚠ Needs Evaluation";
                if (guiConfig != null) {
                    evaluationFormat = guiConfig.getString("sell_gui.evaluation_required_format", evaluationFormat);
                }
                String formatted = evaluationFormat
                        .replace("%item%", itemName)
                        .replace("%amount%", String.valueOf(itemCounts.get(itemName)));
                lore.add(ChatColor.translateAlternateColorCodes('&', formatted));
                lore.add(" ");
            } else {
                double total = itemTotals.get(itemName);
                int amount = itemCounts.get(itemName);
                double averagePrice = (amount > 0) ? total / amount : 0.0;

                String formatted = format
                        .replace("%item%", itemName)
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%price%", String.valueOf(averagePrice))
                        .replace("%total%", String.valueOf(total));
                lore.add(ChatColor.translateAlternateColorCodes('&', formatted));
                lore.add(" ");
            }
        }

        org.bukkit.configuration.file.FileConfiguration guiConfig = this.main.getConfigManager() != null ?
                this.main.getConfigManager().getGUIConfig() : null;
        String totalFormat = "&6&lTotal: &e$%total%";
        if (guiConfig != null) {
            totalFormat = guiConfig.getString("sell_gui.total_format", totalFormat);
        }

        String totalFormatted = totalFormat.replace("%total%", String.valueOf(getTotal(this.menu)));
        lore.add(ChatColor.translateAlternateColorCodes('&', totalFormatted));

        boolean hasEvaluationItems = itemNeedsEvaluation.values().stream().anyMatch(Boolean::booleanValue);
        if (hasEvaluationItems) {
            lore.add("");
            lore.add(ChatColor.translateAlternateColorCodes('&', "&c⚠ Some items need evaluation"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Use /sellgui evaluate"));
        }

        return lore;
    }

    public void setConfirmItem() {
        this.menu.setItem(this.sellItemSlot, (ItemStack) null);
        this.menu.setItem(this.sellItemSlot, this.confirmItem);
        this.isConfirmMode = true;
    }

    public void setSellItem() {
        this.menu.setItem(this.sellItemSlot, (ItemStack) null);
        this.menu.setItem(this.sellItemSlot, this.sellItem);
        this.isConfirmMode = false;
    }

    public String getItemName(ItemStack itemStack) {
        if (itemStack == null) return "Unknown Item";

        try {
            NBTItem nbtItem = new NBTItem(itemStack) {
                @Override
                public Object get(String s) {
                    return null;
                }

                @Override
                public String getString(String s) {
                    return "";
                }

                @Override
                public boolean hasTag(String s) {
                    return false;
                }

                @Override
                public boolean getBoolean(String s) {
                    return false;
                }

                @Override
                public double getDouble(String s) {
                    return 0;
                }

                @Override
                public int getInteger(String s) {
                    return 0;
                }

                @Override
                public NBTCompound getNBTCompound(String s) {
                    return null;
                }

                @Override
                public NBTItem addTag(List<ItemTag> list) {
                    return null;
                }

                @Override
                public NBTItem removeTag(String... strings) {
                    return null;
                }

                @Override
                public Set<String> getTags() {
                    return Set.of();
                }

                @Override
                public ItemStack toItem() {
                    return null;
                }

                @Override
                public int getTypeId(String s) {
                    return 0;
                }
            };

            if (this.main.hasNexo && nbtItem.hasTag("nexo:id")) {
                String nexoId = nbtItem.getString("nexo:id");
                if (nexoId != null && !nexoId.isEmpty()) {
                    if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                        return ChatColor.AQUA + itemStack.getItemMeta().getDisplayName();
                    } else {
                        return ChatColor.AQUA + "[Nexo] " + nexoId;
                    }
                }
            }

            if (this.main.isMMOItemsEnabled() && nbtItem.hasTag("MMOITEMS_ITEM_ID")) {
                MMOItem mmoItem = MMOItems.plugin.getMMOItem(Type.get(nbtItem.getType()), nbtItem.getString("MMOITEMS_ITEM_ID"));
                if (mmoItem != null) {
                    return ChatColor.GREEN + itemStack.getItemMeta().getDisplayName();
                }
            }
        } catch (Exception e) {}

        return itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()
                ? itemStack.getItemMeta().getDisplayName()
                : WordUtils.capitalizeFully(itemStack.getType().name().replace('_', ' '));
    }

    public double getPrice(ItemStack itemStack, @Nullable Player player) {
        double price = 0.0;

        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return price;
        }

        PriceManager priceManager = new PriceManager(main);
        price = priceManager.getItemPrice(itemStack);

        if (price == 0) {
            if (this.main.hasEssentials() && this.main.getConfig().getBoolean("use-essentials-price")) {
                double essentialsPrice = round(this.main.getEssentialsHolder().getPrice(itemStack).doubleValue(),
                        this.main.getConfig().getInt("places-to-round"));
                if (essentialsPrice > 0) {
                    price = essentialsPrice;
                }
            }
            if (price == 0 && this.main.getItemPricesConfig().contains(itemStack.getType().name())) {
                price = this.main.getItemPricesConfig().getDouble(itemStack.getType().name());
            }
        }

        if (price == 0 && itemStack.hasItemMeta()) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                NamespacedKey key = new NamespacedKey(main, "current_price");
                if (meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
                    price = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
                }
            }
        }

        if (price == 0) {if (main instanceof SellGUIMain) {
            Object rpm = null;
            try {
                rpm = main.getClass().getMethod("getRandomPriceManager").invoke(main);
            } catch (Exception ignored) {}
            if (rpm != null) {
                try {
                    boolean canSell = (boolean) rpm.getClass().getMethod("canBeSold", ItemStack.class).invoke(rpm, itemStack);
                    if (!canSell) {
                        return 0.0;
                    }
                } catch (Exception ignored) {}
            }
        }
        }

        if (price > 0) {
            price = applyPermissionBonuses(player, price);
        }

        return round(price, this.main.getConfig().getInt("places-to-round"));
    }

    private double applyPermissionBonuses(Player player, double price) {
        if (player == null || price <= 0) return price;

        double bonusPercent = 0.0;
        double multiplier = 1.0;

        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            if (pai.getPermission().startsWith("sellgui.bonus.") && pai.getValue()) {
                double percent = Double.parseDouble(pai.getPermission().replace("sellgui.bonus.", ""));
                bonusPercent += percent;
            }
            if (pai.getPermission().startsWith("sellgui.multiplier.") && pai.getValue()) {
                multiplier *= Double.parseDouble(pai.getPermission().replace("sellgui.multiplier.", ""));
            }
        }

        price = price * (1 + bonusPercent / 100) * multiplier;
        return price;
    }

    public double getTotal(Inventory inventory) {
        double total = 0.0;

        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack == null || itemStack.getType().isAir()) continue;
            if (isCustomMenuItem(itemStack) || InventoryListeners.sellGUIItem(itemStack, this.player)) continue;
            double price = this.getPrice(itemStack, player);
            if (price > 0) {
                total += price * itemStack.getAmount();
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
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.getMain().getLog(), true))) {
                Date now = new Date();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                String itemType = "VANILLA";
                String itemId = itemStack.getType().name();
                String displayName = "Unknown Item";
                double unitPrice = this.getPrice(itemStack, player);
                double totalPrice = unitPrice * itemStack.getAmount();
                String playerName = this.getPlayer().getName();

                try {
                    NBTItem nbtItem = new NBTItem(itemStack) {
                        @Override
                        public Object get(String s) {
                            return null;
                        }

                        @Override
                        public String getString(String s) {
                            return "";
                        }

                        @Override
                        public boolean hasTag(String s) {
                            return false;
                        }

                        @Override
                        public boolean getBoolean(String s) {
                            return false;
                        }

                        @Override
                        public double getDouble(String s) {
                            return 0;
                        }

                        @Override
                        public int getInteger(String s) {
                            return 0;
                        }

                        @Override
                        public NBTCompound getNBTCompound(String s) {
                            return null;
                        }

                        @Override
                        public NBTItem addTag(List<ItemTag> list) {
                            return null;
                        }

                        @Override
                        public NBTItem removeTag(String... strings) {
                            return null;
                        }

                        @Override
                        public Set<String> getTags() {
                            return Set.of();
                        }

                        @Override
                        public ItemStack toItem() {
                            return null;
                        }

                        @Override
                        public int getTypeId(String s) {
                            return 0;
                        }
                    };

                    if (this.getMain().hasNexo && nbtItem.hasTag("nexo:id")) {

                        itemType = "NEXO";
                        String nexoId = nbtItem.getString("nexo:id");
                        if (nexoId != null && !nexoId.isEmpty()) {
                            itemId = nexoId;
                        }
                        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                            displayName = ChatColor.stripColor(itemStack.getItemMeta().getDisplayName());
                        } else {
                            displayName = "[Nexo] " + itemId;
                        }
                    } else if (this.getMain().isMMOItemsEnabled() && nbtItem.hasTag("MMOITEMS_ITEM_ID")) {

                        itemType = "MMOITEMS";
                        String mmoItemType = nbtItem.getType();
                        String mmoItemId = nbtItem.getString("MMOITEMS_ITEM_ID");
                        if (mmoItemType != null && mmoItemId != null) {
                            itemId = mmoItemType.toUpperCase() + "." + mmoItemId.toUpperCase();
                        }
                        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                            displayName = ChatColor.stripColor(itemStack.getItemMeta().getDisplayName());
                        } else {
                            displayName = "[MMOItems] " + itemId;
                        }
                    } else {

                        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                            displayName = ChatColor.stripColor(itemStack.getItemMeta().getDisplayName());
                        } else {
                            displayName = WordUtils.capitalizeFully(itemStack.getType().name().replace('_', ' '));
                        }
                    }
                } catch (Exception e) {

                    if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                        displayName = ChatColor.stripColor(itemStack.getItemMeta().getDisplayName());
                    } else {
                        displayName = WordUtils.capitalizeFully(itemStack.getType().name().replace('_', ' '));
                    }
                }

                String logEntry = String.format("%s|%s|%s|%d|%.2f|%.2f|%s|%s",
                        itemType,
                        itemId,
                        displayName,
                        itemStack.getAmount(),
                        unitPrice,
                        totalPrice,
                        playerName,
                        format.format(now)
                );

                writer.append(logEntry + " ");
                writer.flush();
            } catch (IOException e) {
                this.getMain().getLogger().severe("Failed to write to sell log: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void sellItems(Inventory inventory) {
        double total = getTotal(inventory);
        if (total <= 0) {
            String noItemsMessage = "&cNothing to sell!";
            if (this.main.getMessagesConfig() != null) {
                noItemsMessage = this.main.getMessagesConfig().getString("sell.no_items", noItemsMessage);
            }
            player.sendMessage(color(noItemsMessage));
            setSellItem();
            return;
        }
        
        this.main.getEcon().depositPlayer(this.player, total);

        for (ItemStack item : inventory.getContents()) {
            if (item != null && !InventoryListeners.sellGUIItem(item, this.player) && !isCustomMenuItem(item)) {
                if (this.main.getConfig().getBoolean("log-transactions")) {
                    logSell(item);
                }
                inventory.remove(item);
            }
        }

        if (this.main.getConfig().getBoolean("general.close-after-sell")) {
            this.player.closeInventory();
            SellCommand.getSellGUIs().remove(this);
        } else {
            setSellItem();
            updateSellItemTotal();
        }

        String soldMessage = "&a✅ Sold items for &e$%total%!";
        if (this.main.getMessagesConfig() != null) {
            soldMessage = this.main.getMessagesConfig().getString("sell.sold_success", soldMessage);
        }
        player.sendMessage(color(soldMessage.replace("%total%", String.format("%.2f", total))));
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
        if (s == null) {
            return "";
        }
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
