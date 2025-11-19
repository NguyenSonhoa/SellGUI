package me.aov.sellgui;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import me.aov.sellgui.utils.ItemIdentifier;
import me.aov.sellgui.commands.SellCommand;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.utils.ColorUtils;
import me.aov.sellgui.managers.ItemNBTManager;
import org.bukkit.*;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nullable;
import me.aov.sellgui.handlers.SoundHandler;

public class SellGUI implements Listener {
    private final SellGUIMain main;
    private final Player player;
    private static ItemStack sellItem;
    private static ItemStack filler;
    private String menuTitle;
    private static Inventory menu;
    private ItemStack confirmItem;
    private ItemStack noItemsItemStack;
    private List<Integer> sellButtonSlots;
    private List<Integer> confirmButtonSlots;
    private int updateTaskId = -1;
    private boolean isConfirmMode = false;
    private boolean sold = false;
    private final ItemNBTManager itemNBTManager;

    public SellGUI(SellGUIMain main, Player p, ItemNBTManager itemNBTManager) {
        this.main = main;
        this.player = p;
        this.itemNBTManager = itemNBTManager;
        this.createItems();
        this.createMenu();
        this.addCustomItems();
        p.openInventory(menu);
        startAutoUpdateTask();
    }

    private void createMenu() {
        FileConfiguration guiConfig = this.main.getConfigManager().getGUIConfig();
        int size = guiConfig.getInt("sell_gui.size", 54);
        this.menuTitle = guiConfig.getString("sell_gui.title", "&6&l✦ &eSell GUI &6&l✦");

        this.sellButtonSlots = guiConfig.getIntegerList("sell_gui.positions.sell_button");
        this.confirmButtonSlots = guiConfig.getIntegerList("sell_gui.positions.confirm_button");

        menu = Bukkit.createInventory((InventoryHolder) null, size, color(menuTitle));

        this.addFillerFromConfig();
        this.addSellButton();
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
                itemMeta.setLore(color(this.main.getCustomMenuItemsConfig().getStringList(itemPath + ".lore")));
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

    public void addSellButton() {
        if (sellButtonSlots.isEmpty()) {
            this.player.sendMessage(color("&cError: sell_button slots are not defined in gui.yml."));
            return;
        }
        for (int slot : sellButtonSlots) {
            if (slot >= 0 && slot < menu.getSize()) {
                menu.setItem(slot, sellItem);
            }
        }
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
        ItemStack buttonToShow = (currentTotal > 0) ? sellItem : noItemsItemStack;

        if (currentTotal > 0) {
            ItemMeta meta = buttonToShow.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = new ArrayList<>(meta.getLore());
                for (int i = 0; i < lore.size(); i++) {
                    String line = lore.get(i);
                    if (ChatColor.stripColor(line).contains("Total Value:")) {
                        boolean roundPrices = this.main.shouldRoundPrices();
                        double displayedTotal = roundPrices ? Math.round(currentTotal) : currentTotal;
                        lore.set(i, color("&eTotal Value: &a$" + String.format("%.2f", displayedTotal)));
                        break;
                    }
                }
                meta.setLore(color(lore));
                buttonToShow.setItemMeta(meta);
            }
        }

        for (int slot : sellButtonSlots) {
            if (slot >= 0 && slot < menu.getSize()) {
                menu.setItem(slot, buttonToShow);
            }
        }
    }

    public void updateButtonState() {
        if (menu == null) return;
        double currentTotal = getTotal(menu);
        if (currentTotal > 0) {
            for (int slot : sellButtonSlots) {
                menu.setItem(slot, null);
            }
            makeConfirmItem();
            for (int slot : confirmButtonSlots) {
                if (slot >= 0 && slot < menu.getSize()) {
                    menu.setItem(slot, this.confirmItem);
                }
            }
            isConfirmMode = true;
        } else {
            isConfirmMode = false;
            updateSellItemTotal();
        }
    }

    public void cleanup() {
        if (updateTaskId != -1) {
            this.main.getServer().getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
        }
    }

    private void createItems() {
        FileConfiguration guiConfig = this.main.getConfigManager().getGUIConfig();

        NamespacedKey guiKey = new NamespacedKey(this.main, "sellgui");
        NamespacedKey actionKey = new NamespacedKey(this.main, "guiAction");

        // Sell Item
        Material sellMaterial = Material.getMaterial(guiConfig.getString("sell_gui.items.sell_button.material", "EMERALD"));
        sellItem = new ItemStack(sellMaterial != null ? sellMaterial : Material.EMERALD);
        ItemMeta sellItemMeta = sellItem.getItemMeta();
        if (sellItemMeta != null) {
            sellItemMeta.setDisplayName(color(guiConfig.getString("sell_gui.items.sell_button.name", "&a&lSell Items")));
            sellItemMeta.setLore(color(guiConfig.getStringList("sell_gui.items.sell_button.lore")));
            if (guiConfig.getBoolean("sell_gui.items.sell_button.glow", true)) {
                sellItemMeta.addEnchant(Enchantment.INFINITY, 1, false);
                sellItemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            if (guiConfig.contains("sell_gui.items.sell_button.custom-model-data")) {
                sellItemMeta.setCustomModelData(guiConfig.getInt("sell_gui.items.sell_button.custom-model-data"));
            }
            sellItemMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
            sellItemMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "sell");
            sellItem.setItemMeta(sellItemMeta);
        }

        // Filler Item
        Material fillerMaterial = Material.getMaterial(guiConfig.getString("sell_gui.items.filler.material", "GRAY_STAINED_GLASS_PANE"));
        filler = new ItemStack(fillerMaterial != null ? fillerMaterial : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(color(guiConfig.getString("sell_gui.items.filler.name", " ")));
            if (guiConfig.contains("sell_gui.items.filler.custom-model-data")) {
                fillerMeta.setCustomModelData(guiConfig.getInt("sell_gui.items.filler.custom-model-data"));
            }
            fillerMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
            filler.setItemMeta(fillerMeta);
        }

        // No Items Item
        Material noItemsMaterial = Material.getMaterial(guiConfig.getString("sell_gui.items.no_items.material", "BARRIER"));
        noItemsItemStack = new ItemStack(noItemsMaterial != null ? noItemsMaterial : Material.BARRIER);
        ItemMeta noItemsMeta = noItemsItemStack.getItemMeta();
        if (noItemsMeta != null) {
            noItemsMeta.setDisplayName(color(guiConfig.getString("sell_gui.items.no_items.name", "&cNo items to sell!")));
            noItemsMeta.setLore(color(guiConfig.getStringList("sell_gui.items.no_items.lore")));
            if (guiConfig.contains("sell_gui.items.no_items.custom-model-data")) {
                noItemsMeta.setCustomModelData(guiConfig.getInt("sell_gui.items.no_items.custom-model-data"));
            }
            noItemsMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
            noItemsItemStack.setItemMeta(noItemsMeta);
        }
    }

    private void addFillerFromConfig() {
        FileConfiguration guiConfig = this.main.getConfigManager().getGUIConfig();
        List<Integer> fillerSlots = guiConfig.getIntegerList("sell_gui.positions.filler_slots");
        if (!fillerSlots.isEmpty()) {
            for (int slot : fillerSlots) {
                if (slot >= 0 && slot < menu.getSize()) {
                    menu.setItem(slot, filler);
                }
            }
        }
    }

    public void makeConfirmItem() {
        FileConfiguration guiConfig = this.main.getConfigManager().getGUIConfig();
        Material material = Material.getMaterial(guiConfig.getString("sell_gui.items.confirm_button.material", "GREEN_CONCRETE"));
        this.confirmItem = new ItemStack(material != null ? material : Material.GREEN_CONCRETE);
        ItemMeta itemMeta = this.confirmItem.getItemMeta();

        if (itemMeta != null) {
            itemMeta.setDisplayName(color(guiConfig.getString("sell_gui.items.confirm_button.name", "&a&lConfirm Sale")));
            if (guiConfig.getBoolean("sell_gui.items.confirm_button.glow", true)) {
                itemMeta.addEnchant(Enchantment.POWER, 1, false);
                itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            if (guiConfig.contains("sell_gui.items.confirm_button.custom-model-data")) {
                itemMeta.setCustomModelData(guiConfig.getInt("sell_gui.items.confirm_button.custom-model-data"));
            }

            // New Lore Logic
            List<String> finalLore = new ArrayList<>();
            HashMap<String, Integer> itemsNeedingEvaluation = new HashMap<>();
            List<String> breakdownLore = generateItemBreakdownLore(itemsNeedingEvaluation);

            if (!breakdownLore.isEmpty()) {
                finalLore.addAll(breakdownLore);
                finalLore.add(" ");
            }

            List<String> loreTemplate = guiConfig.getStringList("sell_gui.items.confirm_button.lore");
            String totalValue = String.format("%.2f", getTotal(this.menu));

            for (String templateLine : loreTemplate) {
                finalLore.add(templateLine.replace("%total%", totalValue));
            }

            if (!itemsNeedingEvaluation.isEmpty()) {
                finalLore.add(" ");
                finalLore.add(color("&c⚠ Some items need evaluation"));
                finalLore.add(color("&7Use /sellgui evaluate"));
            }

            itemMeta.setLore(color(finalLore));

            NamespacedKey guiKey = new NamespacedKey(this.main, "sellgui");
            NamespacedKey actionKey = new NamespacedKey(this.main, "guiAction");
            itemMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
            itemMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "confirm");
            this.confirmItem.setItemMeta(itemMeta);
        }
    }

    private List<String> generateItemBreakdownLore(HashMap<String, Integer> itemsNeedingEvaluation) {
        List<String> lore = new ArrayList<>();
        FileConfiguration guiConfig = this.main.getConfigManager().getGUIConfig();
        String calculationMethod = main.getConfig().getString("prices.calculation-method", "auto").toLowerCase();

        HashMap<String, Integer> itemCounts = new HashMap<>();
        HashMap<String, Double> itemTotals = new HashMap<>();
        HashMap<String, String> itemDisplayNames = new HashMap<>();

        for (ItemStack item : this.getMenu().getContents()) {
            if (item != null && !isGuiItem(item)) {
                String itemIdentifierKey = ItemIdentifier.getItemIdentifier(item);
                String itemDisplayName = ItemIdentifier.getItemDisplayName(item);
                itemDisplayNames.put(itemIdentifierKey, itemDisplayName);

                boolean needsEvaluation = main.getRandomPriceManager() != null &&
                        main.getRandomPriceManager().requiresEvaluation(item) &&
                        !main.getRandomPriceManager().isEvaluated(item);

                if (needsEvaluation) {
                    itemsNeedingEvaluation.put(itemIdentifierKey, itemsNeedingEvaluation.getOrDefault(itemIdentifierKey, 0) + item.getAmount());
                } else {
                    double price = getPrice(item, player);
                    if (price > 0) {
                        itemCounts.put(itemIdentifierKey, itemCounts.getOrDefault(itemIdentifierKey, 0) + item.getAmount());
                        double totalItemPrice = calculationMethod.equals("shopguiplus") ? price : price * item.getAmount();
                        itemTotals.put(itemIdentifierKey, itemTotals.getOrDefault(itemIdentifierKey, 0.0) + totalItemPrice);
                    }
                }
            }
        }

        String format = guiConfig.getString("sell_gui.item_total_format", "&7%amount%x &f%item% &8= &e$%total%");
        for (String itemIdentifierKey : itemCounts.keySet()) {
            double total = itemTotals.get(itemIdentifierKey);
            int amount = itemCounts.get(itemIdentifierKey);
            double averagePrice = (amount > 0) ? total / amount : 0.0;
            String displayedItemName = itemDisplayNames.get(itemIdentifierKey);

            String formatted = format
                    .replace("%item%", displayedItemName)
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%price%", String.format("%.2f", averagePrice))
                    .replace("%total%", String.format("%.2f", total));
            lore.add(formatted);
        }

        String evaluationFormat = guiConfig.getString("sell_gui.evaluation_required_format", "&7%amount%x &f%item% &c⚠ Needs Evaluation");
        for (String itemIdentifierKey : itemsNeedingEvaluation.keySet()) {
            String displayedItemName = itemDisplayNames.get(itemIdentifierKey);
            String formatted = evaluationFormat
                    .replace("%item%", displayedItemName)
                    .replace("%amount%", String.valueOf(itemsNeedingEvaluation.get(itemIdentifierKey)));
            lore.add(formatted);
        }

        return lore;
    }

    public boolean hasUnevaluatedItems() {
        if (main.getRandomPriceManager() == null) return false;
        for (ItemStack item : getMenu().getContents()) {
            if (item != null && !isGuiItem(item)) {
                if (main.getRandomPriceManager().requiresEvaluation(item) && !main.getRandomPriceManager().isEvaluated(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setConfirmMode() {
        updateButtonState();
    }

    public void setSellItem() {
        for (int slot : confirmButtonSlots) {
            menu.setItem(slot, null);
        }
        isConfirmMode = false;
        updateSellItemTotal();
    }

    public double getPrice(ItemStack itemStack, @Nullable Player player) {
        double contentsPrice = 0.0;

        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return 0.0;
        }
        if (itemStack.getType().name().endsWith("_SHULKER_BOX")) {
            if (itemStack.hasItemMeta() && itemStack.getItemMeta() instanceof BlockStateMeta) {
                BlockStateMeta meta = (BlockStateMeta) itemStack.getItemMeta();
                if (meta.getBlockState() instanceof ShulkerBox) {
                    ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
                    String calculationMethod = this.main.getConfig().getString("prices.calculation-method", "auto").toLowerCase();
                    for (ItemStack contained : shulker.getInventory().getContents()) {
                        if (contained != null && !contained.getType().isAir()) {
                            double price = this.getPrice(contained, player);
                            if (calculationMethod.equals("shopguiplus")) {
                                contentsPrice += price;
                            } else {
                                contentsPrice += price * contained.getAmount();
                            }
                        }
                    }
                }
            }
        }

        double itemPrice = 0.0;
        ItemStack itemToPrice = itemStack;
        if(itemStack.getType().name().endsWith("_SHULKER_BOX")) {
            itemToPrice = itemStack.clone();
            itemToPrice.setAmount(1);
        }

        if (itemToPrice.hasItemMeta()) {
            ItemMeta meta = itemToPrice.getItemMeta();
            if (meta != null) {
                NamespacedKey key = new NamespacedKey(main, "current_price");
                if (meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
                    itemPrice = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
                }
            }
        }

        if (itemPrice == 0) {
            PriceManager priceManager = new PriceManager(main);
            itemPrice = priceManager.getItemPriceWithPlayer(itemToPrice, player);

            if (itemPrice == 0) {
                if (this.main.hasEssentials() && this.main.getConfig().getBoolean("use-essentials-price")) {
                    double essentialsPrice = round(this.main.getEssentialsHolder().getPrice(itemToPrice).doubleValue(),
                            this.main.getConfig().getInt("places-to-round"));
                    if (essentialsPrice > 0) {
                        itemPrice = essentialsPrice;
                    }
                }
                if (itemPrice == 0 && this.main.getItemPricesConfig().contains(itemToPrice.getType().name())) {
                    itemPrice = this.main.getItemPricesConfig().getDouble(itemToPrice.getType().name());
                }
            }
        }
        if (itemPrice == 0 && player != null) {

            if (this.main.getServer().getPluginManager().getPlugin("ShopGuiPlus") != null && this.main.getServer().getPluginManager().getPlugin("ShopGuiPlus").isEnabled()) {
                try {
                    double shopGuiPrice = net.brcdev.shopgui.ShopGuiPlusApi.getItemStackPriceSell(player, itemToPrice);
                    if (shopGuiPrice > 0) {
                        itemPrice = shopGuiPrice;
                    }
                } catch (NoClassDefFoundError e) {

                    this.main.getLogger().warning("ShopGuiPlusApi class not found, skipping ShopGuiPlus pricing. Error: " + e.getMessage());
                } catch (Exception e) {

                    this.main.getLogger().warning("An error occurred while getting price from ShopGuiPlus: " + e.getMessage());
                }
            } else {

            }
        }
        if (itemPrice == 0) {if (main instanceof SellGUIMain) {
            Object rpm = null;
            try {
                rpm = main.getClass().getMethod("getRandomPriceManager").invoke(main);
            } catch (Exception ignored) {}
            if (rpm != null) {
                try {
                    boolean canSell = (boolean) rpm.getClass().getMethod("canBeSold", ItemStack.class).invoke(rpm, itemToPrice);
                    if (!canSell) {
                        itemPrice = 0.0;
                    }
                } catch (Exception ignored) {}
            }
        }
        }

        double totalPrice = contentsPrice + itemPrice;

        if (totalPrice > 0) {
            totalPrice = applyPermissionBonuses(player, totalPrice);
        }

        if (this.main.shouldRoundPrices()) {
            return round(totalPrice, this.main.getConfig().getInt("places-to-round"));
        } else {
            return totalPrice;
        }
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
        String calculationMethod = main.getConfig().getString("prices.calculation-method", "auto").toLowerCase();
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack == null || itemStack.getType().isAir()) continue;
            if (isGuiItem(itemStack)) continue;
            boolean canSell = main.getRandomPriceManager() == null || main.getRandomPriceManager().canBeSold(itemStack);
            if (!canSell) continue;
            if (main.getRandomPriceManager() != null && main.getRandomPriceManager().hasRandomPrice(itemStack) && !main.getRandomPriceManager().isEvaluated(itemStack)) {
                continue;
            }
            double price = this.getPrice(itemStack, player);
            if (price > 0) {
                if (calculationMethod.equals("shopguiplus")) {
                    total += price;
                } else {
                    total += price * itemStack.getAmount();
                }
            }
        }
        return total;
    }

    private boolean isGuiItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey guiKey = new NamespacedKey(this.main, "sellgui");
        return meta.getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE);
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

                ItemIdentifier.ItemType itemTypeEnum = ItemIdentifier.getItemType(itemStack);
                String itemType = itemTypeEnum.name();
                String itemId = ItemIdentifier.getItemIdentifier(itemStack);
                String displayName = org.bukkit.ChatColor.stripColor(ItemIdentifier.getItemDisplayName(itemStack));
                double unitPrice = this.getPrice(itemStack, player);

                String calculationMethod = this.getMain().getConfig().getString("prices.calculation-method", "auto").toLowerCase();
                double totalPrice;
                if (calculationMethod.equals("shopguiplus")) {
                    totalPrice = unitPrice;
                } else {
                    totalPrice = unitPrice * itemStack.getAmount();
                }

                String playerName = this.getPlayer().getName();

                String logEntry = String.format("[SELLGUI] %s|%s|%s|%d|%.2f|%.2f|%s|%s",
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
        if (hasUnevaluatedItems()) {
            String message = main.getMessagesConfig().getString("sell.evaluation_required", "&cSome items must be evaluated before selling.");
            player.sendMessage(color(message));
            setSellItem();
            return;
        }

        double total = getTotal(inventory);
        if (total <= 0) {
            String message = main.getMessagesConfig().getString("sell.no_items", "&cNothing to sell!");
            player.sendMessage(color(message));
            setSellItem();
            return;
        }

        this.main.getEcon().depositPlayer(this.player, total);
        this.setSold(true);

        for (ItemStack item : inventory.getContents()) {
            if (item != null && !isGuiItem(item)) {
                if (getPrice(item, player) > 0 && !hasUnevaluatedItems()) {
                    if (this.main.getConfig().getBoolean("logging.enabled")) {
                        logSell(item);
                    }
                    inventory.remove(item);
                }
            }
        }
        if (this.main.getConfig().getBoolean("general.close-after-sell")) {
            this.player.closeInventory();
            SellCommand.getSellGUIs().remove(this);
        } else {
            setSellItem();
        }

        String soldMessage = main.getMessagesConfig().getString("sell.sold_success", "&a✅ Sold items for &e$%total%!");
        player.sendMessage(color(soldMessage.replace("%total%", String.format("%.2f", total))));
        SoundHandler.playConfigSound(player, "sounds.feedback.success");
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

    public Inventory getMenu() {
        return menu;
    }

    public SellGUIMain getMain() {
        return this.main;
    }

    public boolean isConfirmMode() {
        return this.isConfirmMode;
    }

    public boolean isSold() {
        return this.sold;
    }

    public void setSold(boolean sold) {
        this.sold = sold;
    }

    public String color(String s) {
        if (s == null) return "";
        if (main.isPlaceholderAPIAvailable()) {
            s = main.setPlaceholders(player, s);
        }
        return ColorUtils.color(s);
    }

    public List<String> color(List<String> lore) {
        if (lore == null) return new ArrayList<>();
        return lore.stream().map(this::color).collect(Collectors.toList());
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}