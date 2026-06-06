package me.aov.sellgui;

import me.aov.sellgui.commands.SellCommand;
import me.aov.sellgui.api.PlayerSellItemsEvent;
import me.aov.sellgui.api.SellGUIItemsSoldEvent;
import me.aov.sellgui.api.SoldItem;
import me.aov.sellgui.gui.SellMenuConfig;
import me.aov.sellgui.handlers.SoundHandler;
import me.aov.sellgui.managers.ItemNBTManager;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.utils.ColorUtils;
import me.aov.sellgui.utils.ItemIdentifier;
import me.aov.sellgui.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
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
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SellGUI implements Listener, InventoryHolder {
    private final SellGUIMain main;
    private final Player player;
    private final SellMenuConfig menuConfig;

    private ItemStack sellItem;
    private ItemStack filler;
    private ItemStack confirmItem;
    private ItemStack noItemsItemStack;
    private String menuTitle;
    private Inventory menu;
    private List<Integer> sellButtonSlots = new ArrayList<>();
    private List<Integer> confirmButtonSlots = new ArrayList<>();
    private List<Integer> itemSlots = new ArrayList<>();
    private int updateTaskId = -1;
    private boolean isConfirmMode = false;
    private boolean sold = false;

    public SellGUI(SellGUIMain main, Player player, ItemNBTManager itemNBTManager) {
        this(main, player, itemNBTManager, SellMenuConfig.DEFAULT_MENU_ID);
    }

    public SellGUI(SellGUIMain main, Player player, ItemNBTManager itemNBTManager, String menuId) {
        this.main = main;
        this.player = player;
        this.menuConfig = SellMenuConfig.load(main, menuId);
        if (this.menuConfig == null) {
            throw new IllegalArgumentException("Unknown sell menu: " + menuId);
        }

        createItems();
        createMenu();
        addCustomItems();
        player.openInventory(menu);
        startAutoUpdateTask();
    }

    private void createMenu() {
        int size = normalizeInventorySize(menuConfig.getInt("size", 54));
        this.menuTitle = menuConfig.getString("title", "&6&lSell GUI");
        this.sellButtonSlots = menuConfig.getIntegerList("positions.sell_button");
        this.confirmButtonSlots = menuConfig.getIntegerList("positions.confirm_button");
        this.itemSlots = menuConfig.getIntegerList("positions.item_slots");

        if (sellButtonSlots.isEmpty()) {
            sellButtonSlots.add(Math.min(49, size - 1));
        }
        if (confirmButtonSlots.isEmpty()) {
            confirmButtonSlots.addAll(sellButtonSlots);
        }

        this.menu = Bukkit.createInventory(this, size, color(menuTitle));
        addFillerFromConfig();
        addSellButton();
    }

    private int normalizeInventorySize(int size) {
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        return ((size + 8) / 9) * 9;
    }

    private void createItems() {
        NamespacedKey guiKey = new NamespacedKey(main, "sellgui");
        NamespacedKey actionKey = new NamespacedKey(main, "guiAction");
        NamespacedKey menuKey = new NamespacedKey(main, "sellgui-menu");

        sellItem = createGuiItem(
                "items.sell_button",
                Material.EMERALD,
                "&a&lSell Items",
                menuConfig.getStringList("items.sell_button.lore")
        );
        ItemMeta sellItemMeta = sellItem.getItemMeta();
        if (sellItemMeta != null) {
            if (menuConfig.getBoolean("items.sell_button.glow", true)) {
                sellItemMeta.addEnchant(Enchantment.INFINITY, 1, false);
                sellItemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            sellItemMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
            sellItemMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "sell");
            sellItemMeta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, menuConfig.getId());
            sellItem.setItemMeta(sellItemMeta);
        }

        filler = createGuiItem(
                "items.filler",
                Material.GRAY_STAINED_GLASS_PANE,
                " ",
                menuConfig.getStringList("items.filler.lore")
        );
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
            fillerMeta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, menuConfig.getId());
            filler.setItemMeta(fillerMeta);
        }

        noItemsItemStack = createGuiItem(
                "items.no_items",
                Material.BARRIER,
                "&cNo items to sell!",
                menuConfig.getStringList("items.no_items.lore")
        );
        ItemMeta noItemsMeta = noItemsItemStack.getItemMeta();
        if (noItemsMeta != null) {
            noItemsMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
            noItemsMeta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, menuConfig.getId());
            noItemsItemStack.setItemMeta(noItemsMeta);
        }
    }

    private ItemStack createGuiItem(String path, Material fallbackMaterial, String fallbackName, List<String> fallbackLore) {
        Material material = Material.matchMaterial(menuConfig.getString(path + ".material", fallbackMaterial.name()));
        ItemStack item = new ItemStack(material != null ? material : fallbackMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(menuConfig.getString(path + ".name", fallbackName)));
            List<String> lore = menuConfig.getStringList(path + ".lore");
            if (lore.isEmpty()) {
                lore = fallbackLore;
            }
            meta.setLore(color(lore));
            if (menuConfig.contains(path + ".custom-model-data")) {
                int customModelData = menuConfig.getInt(path + ".custom-model-data", 0);
                if (customModelData > 0) {
                    meta.setCustomModelData(customModelData);
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addFillerFromConfig() {
        for (int slot : menuConfig.getIntegerList("positions.filler_slots")) {
            if (isValidSlot(slot)) {
                menu.setItem(slot, filler);
            }
        }
    }

    public void addSellButton() {
        for (int slot : sellButtonSlots) {
            if (isValidSlot(slot)) {
                menu.setItem(slot, sellItem);
            }
        }
        makeConfirmItem();
    }

    private void addCustomItems() {
        if (main.getCustomMenuItemsConfig() == null) {
            return;
        }

        for (String itemPath : main.getCustomMenuItemsConfig().getKeys(false)) {
            if (!main.getCustomMenuItemsConfig().contains(itemPath + ".slot")) {
                continue;
            }

            List<String> menus = main.getCustomMenuItemsConfig().getStringList(itemPath + ".menus");
            if (!menus.isEmpty() && menus.stream().noneMatch(menu -> SellMenuConfig.normalizeMenuId(menu).equals(menuConfig.getId()))) {
                continue;
            }

            int slot = main.getCustomMenuItemsConfig().getInt(itemPath + ".slot");
            if (!isValidSlot(slot)) {
                continue;
            }

            if (main.getCustomMenuItemsConfig().getBoolean(itemPath + ".disabled")) {
                menu.setItem(slot, filler);
                continue;
            }

            Material material = Material.matchMaterial(main.getCustomMenuItemsConfig().getString(itemPath + ".material", "STONE"));
            ItemStack customItem = new ItemStack(material != null ? material : Material.STONE);
            ItemMeta itemMeta = customItem.getItemMeta();
            if (itemMeta == null) {
                continue;
            }
            ConfigurationSection itemConfig = main.getCustomMenuItemsConfig().getConfigurationSection(itemPath);

            if (main.getCustomMenuItemsConfig().contains(itemPath + ".custom-model-data")) {
                int customModelData = main.getCustomMenuItemsConfig().getInt(itemPath + ".custom-model-data");
                if (customModelData > 0) {
                    itemMeta.setCustomModelData(customModelData);
                }
            }
            String name = main.getCustomMenuItemsConfig().getString(itemPath + ".name", "");
            if (!name.isEmpty()) {
                itemMeta.setDisplayName(color(name));
            }
            if (main.getCustomMenuItemsConfig().getBoolean(itemPath + ".glimmer")) {
                itemMeta.addEnchant(Enchantment.INFINITY, 1, false);
                itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            List<String> lore = main.getCustomMenuItemsConfig().getStringList(itemPath + ".lore");
            if (!lore.isEmpty()) {
                itemMeta.setLore(color(lore));
            }
            ItemUtils.applyModernComponents(itemMeta, itemConfig);

            NamespacedKey key = new NamespacedKey(main, "custom-menu-item");
            String commands = main.getCustomMenuItemsConfig().getStringList(itemPath + ".commands").stream()
                    .map(command -> command.replace("%player%", player.getName()))
                    .collect(Collectors.joining(";"));
            itemMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, commands);
            customItem.setItemMeta(itemMeta);
            menu.setItem(slot, customItem);
        }
    }

    private void startAutoUpdateTask() {
        if (updateTaskId != -1) {
            main.getServer().getScheduler().cancelTask(updateTaskId);
        }

        long updateInterval = main.getConfig().getLong("performance.gui-update-interval", 20);
        updateTaskId = main.getServer().getScheduler().runTaskTimer(main, () -> {
            if (menu != null && player != null && player.isOnline() && !isConfirmMode) {
                returnInvalidItems();
                updateSellItemTotal();
            } else if (!player.isOnline() || menu == null) {
                cleanup();
            }
        }, 0L, updateInterval).getTaskId();
    }

    public void updateSellItemTotal() {
        if (menu == null || isConfirmMode) {
            return;
        }

        double currentTotal = getTotal(menu);
        ItemStack buttonToShow = currentTotal > 0 ? sellItem.clone() : noItemsItemStack.clone();
        if (currentTotal > 0) {
            ItemMeta meta = buttonToShow.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = new ArrayList<>(meta.getLore());
                boolean updatedTotalLine = false;
                for (int i = 0; i < lore.size(); i++) {
                    String rawLine = lore.get(i);
                    if (isSellButtonTotalLine(rawLine)) {
                        if (!updatedTotalLine) {
                            lore.set(i, formatSellButtonTotalLine(rawLine, currentTotal));
                            updatedTotalLine = true;
                        } else {
                            lore.remove(i--);
                        }
                    }
                }
                meta.setLore(color(lore));
                buttonToShow.setItemMeta(meta);
            }
        }

        for (int slot : sellButtonSlots) {
            if (isValidSlot(slot)) {
                menu.setItem(slot, buttonToShow);
            }
        }
    }

    private boolean isSellButtonTotalLine(String loreLine) {
        if (loreLine == null) {
            return false;
        }

        if (loreLine.contains("%total%")) {
            return true;
        }

        String plainLine = ChatColor.stripColor(loreLine);
        return plainLine != null && plainLine.trim().toLowerCase().startsWith("total");
    }

    private String formatSellButtonTotalLine(String loreLine, double total) {
        String totalValue = String.format("%.2f", total);
        if (loreLine != null && loreLine.contains("%total%")) {
            return loreLine
                    .replace("%total%", totalValue)
                    .replace("%menu%", menuConfig.getDisplayName());
        }
        return "&eTotal Value: &a$" + totalValue;
    }

    public void updateButtonState() {
        if (menu == null) {
            return;
        }

        returnInvalidItems();
        double currentTotal = getTotal(menu);
        if (currentTotal > 0) {
            for (int slot : sellButtonSlots) {
                if (isValidSlot(slot)) {
                    menu.setItem(slot, null);
                }
            }
            makeConfirmItem();
            for (int slot : confirmButtonSlots) {
                if (isValidSlot(slot)) {
                    menu.setItem(slot, confirmItem);
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
            main.getServer().getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
        }
    }

    public void makeConfirmItem() {
        Material material = Material.matchMaterial(menuConfig.getString("items.confirm_button.material", "GREEN_CONCRETE"));
        confirmItem = new ItemStack(material != null ? material : Material.GREEN_CONCRETE);
        ItemMeta itemMeta = confirmItem.getItemMeta();
        if (itemMeta == null) {
            return;
        }

        itemMeta.setDisplayName(color(menuConfig.getString("items.confirm_button.name", "&a&lConfirm Sale")));
        if (menuConfig.getBoolean("items.confirm_button.glow", true)) {
            itemMeta.addEnchant(Enchantment.POWER, 1, false);
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (menuConfig.contains("items.confirm_button.custom-model-data")) {
            int customModelData = menuConfig.getInt("items.confirm_button.custom-model-data", 0);
            if (customModelData > 0) {
                itemMeta.setCustomModelData(customModelData);
            }
        }

        List<String> finalLore = new ArrayList<>();
        HashMap<String, Integer> itemsNeedingEvaluation = new HashMap<>();
        List<String> breakdownLore = generateItemBreakdownLore(itemsNeedingEvaluation);
        if (!breakdownLore.isEmpty()) {
            finalLore.addAll(breakdownLore);
            finalLore.add(" ");
        }

        String totalValue = String.format("%.2f", getTotal(menu));
        for (String templateLine : menuConfig.getStringList("items.confirm_button.lore")) {
            finalLore.add(templateLine.replace("%total%", totalValue).replace("%menu%", menuConfig.getDisplayName()));
        }

        if (!itemsNeedingEvaluation.isEmpty()) {
            finalLore.add(" ");
            finalLore.add("&cSome items need evaluation");
            finalLore.add("&7Use /sellgui evaluate");
        }

        itemMeta.setLore(color(finalLore));
        NamespacedKey guiKey = new NamespacedKey(main, "sellgui");
        NamespacedKey actionKey = new NamespacedKey(main, "guiAction");
        NamespacedKey menuKey = new NamespacedKey(main, "sellgui-menu");
        itemMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
        itemMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "confirm");
        itemMeta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, menuConfig.getId());
        confirmItem.setItemMeta(itemMeta);
    }

    private List<String> generateItemBreakdownLore(HashMap<String, Integer> itemsNeedingEvaluation) {
        List<String> lore = new ArrayList<>();
        Map<String, Integer> itemCounts = new HashMap<>();
        Map<String, Double> itemTotals = new HashMap<>();
        Map<String, String> itemDisplayNames = new HashMap<>();

        for (int slot = 0; slot < menu.getSize(); slot++) {
            ItemStack item = menu.getItem(slot);
            if (!isSellableMenuItem(slot, item)) {
                continue;
            }

            String itemIdentifierKey = ItemIdentifier.getItemIdentifier(item);
            String itemDisplayName = ItemIdentifier.getItemDisplayName(item);
            itemDisplayNames.put(itemIdentifierKey, itemDisplayName);

            boolean needsEvaluation = main.getRandomPriceManager() != null
                    && main.getRandomPriceManager().requiresEvaluation(item)
                    && !main.getRandomPriceManager().isEvaluated(item);
            if (needsEvaluation) {
                itemsNeedingEvaluation.put(itemIdentifierKey, itemsNeedingEvaluation.getOrDefault(itemIdentifierKey, 0) + item.getAmount());
                continue;
            }

            double price = getPrice(item, player);
            if (price <= 0) {
                continue;
            }

            itemCounts.put(itemIdentifierKey, itemCounts.getOrDefault(itemIdentifierKey, 0) + item.getAmount());
            double totalItemPrice = isShulkerBox(item) ? price : price * item.getAmount();
            itemTotals.put(itemIdentifierKey, itemTotals.getOrDefault(itemIdentifierKey, 0.0) + totalItemPrice);
        }

        String format = menuConfig.getString("item_total_format", "&7%amount%x &f%item% &8= &e$%total%");
        for (String itemIdentifierKey : itemCounts.keySet()) {
            double total = itemTotals.get(itemIdentifierKey);
            int amount = itemCounts.get(itemIdentifierKey);
            double averagePrice = amount > 0 ? total / amount : 0.0;
            String displayedItemName = itemDisplayNames.get(itemIdentifierKey);
            lore.add(format
                    .replace("%item%", displayedItemName)
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%price%", String.format("%.2f", averagePrice))
                    .replace("%total%", String.format("%.2f", total))
                    .replace("%menu%", menuConfig.getDisplayName()));
        }

        String evaluationFormat = menuConfig.getString("evaluation_required_format", "&7%amount%x &f%item% &cNeeds Evaluation");
        for (String itemIdentifierKey : itemsNeedingEvaluation.keySet()) {
            String displayedItemName = itemDisplayNames.get(itemIdentifierKey);
            lore.add(evaluationFormat
                    .replace("%item%", displayedItemName)
                    .replace("%amount%", String.valueOf(itemsNeedingEvaluation.get(itemIdentifierKey)))
                    .replace("%menu%", menuConfig.getDisplayName()));
        }

        return lore;
    }

    public boolean hasUnevaluatedItems() {
        if (main.getRandomPriceManager() == null) {
            return false;
        }

        for (int slot = 0; slot < menu.getSize(); slot++) {
            ItemStack item = menu.getItem(slot);
            if (isSellableMenuItem(slot, item)
                    && main.getRandomPriceManager().requiresEvaluation(item)
                    && !main.getRandomPriceManager().isEvaluated(item)) {
                return true;
            }
        }
        return false;
    }

    public void setConfirmMode() {
        updateButtonState();
    }

    public void setSellItem() {
        for (int slot : confirmButtonSlots) {
            if (isValidSlot(slot)) {
                menu.setItem(slot, null);
            }
        }
        isConfirmMode = false;
        updateSellItemTotal();
    }

    public double getPrice(ItemStack itemStack, @Nullable Player player) {
        if (itemStack == null || itemStack.getType() == Material.AIR || !canAcceptItem(itemStack)) {
            return 0.0;
        }

        BigDecimal itemPrice = BigDecimal.ZERO;
        ItemStack itemToPrice = itemStack.clone();

        ItemMeta meta = itemToPrice.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(main, "current_price");
            if (meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
                itemPrice = BigDecimal.valueOf(meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE));
            }
        }

        if (itemPrice.compareTo(BigDecimal.ZERO) == 0) {
            PriceManager priceManager = main.getPriceManager() != null ? main.getPriceManager() : new PriceManager(main);
            double price = priceManager.getItemPriceWithPlayer(itemToPrice, player);
            if (price > 0) {
                itemPrice = BigDecimal.valueOf(price);
            }
        }

        if (main.getRandomPriceManager() != null && !main.getRandomPriceManager().canBeSold(itemToPrice)) {
            return 0.0;
        }

        BigDecimal contentsPrice = BigDecimal.ZERO;
        if (isShulkerBox(itemStack) && itemStack.getItemMeta() instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof ShulkerBox shulker) {
            for (ItemStack contained : shulker.getInventory().getContents()) {
                if (contained != null && !contained.getType().isAir()) {
                    contentsPrice = contentsPrice.add(BigDecimal.valueOf(getPrice(contained, player)).multiply(BigDecimal.valueOf(contained.getAmount())));
                }
            }
        }

        BigDecimal totalPrice = itemPrice.add(contentsPrice);
        if (totalPrice.compareTo(BigDecimal.ZERO) > 0) {
            totalPrice = applyPermissionBonuses(player, totalPrice);
        }
        return totalPrice.doubleValue();
    }

    private BigDecimal applyPermissionBonuses(Player player, BigDecimal price) {
        if (player == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return price;
        }

        BigDecimal bonusPercent = BigDecimal.ZERO;
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            if (!pai.getPermission().startsWith("sellgui.bonus.") || !pai.getValue()) {
                continue;
            }
            if (player.isOp() && pai.getAttachment() == null) {
                continue;
            }
            try {
                String percentStr = pai.getPermission().substring("sellgui.bonus.".length());
                bonusPercent = bonusPercent.add(new BigDecimal(percentStr));
            } catch (NumberFormatException e) {
                main.getLogger().warning("Invalid sell bonus permission format: " + pai.getPermission());
            }
        }

        if (bonusPercent.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal multiplier = BigDecimal.ONE.add(bonusPercent.divide(new BigDecimal("100")));
            return price.multiply(multiplier);
        }
        return price;
    }

    public double getTotal(Inventory inventory) {
        BigDecimal total = BigDecimal.ZERO;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack itemStack = inventory.getItem(slot);
            if (!isSellableMenuItem(slot, itemStack)) {
                continue;
            }
            if (main.getRandomPriceManager() != null
                    && (!main.getRandomPriceManager().canBeSold(itemStack)
                    || (main.getRandomPriceManager().hasRandomPrice(itemStack) && !main.getRandomPriceManager().isEvaluated(itemStack)))) {
                continue;
            }

            double pricePerItem = getPrice(itemStack, player);
            if (pricePerItem <= 0) {
                continue;
            }

            if (isShulkerBox(itemStack)) {
                total = total.add(BigDecimal.valueOf(pricePerItem));
            } else {
                total = total.add(BigDecimal.valueOf(pricePerItem).multiply(BigDecimal.valueOf(itemStack.getAmount())));
            }
        }

        return total.doubleValue();
    }

    public void sellItems(Inventory inventory) {
        returnInvalidItems();
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

        main.getEcon().depositPlayer(player, total);
        sold = true;

        List<SoldItem> soldItems = new ArrayList<>();
        List<ItemStack> eventItems = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            double unitPrice = getPrice(item, player);
            if (!isSellableMenuItem(slot, item) || unitPrice <= 0) {
                continue;
            }

            if (main.getConfig().getBoolean("logging.enabled")) {
                logSell(item);
            }
            int amount = item.getAmount();
            double lineTotal = isShulkerBox(item) ? unitPrice : unitPrice * amount;
            ItemStack soldItem = item.clone();
            soldItems.add(new SoldItem(soldItem, amount, unitPrice, lineTotal));
            eventItems.add(soldItem);
            inventory.setItem(slot, null);
        }

        if (!soldItems.isEmpty()) {
            Bukkit.getPluginManager().callEvent(new PlayerSellItemsEvent(player, this, eventItems, total));
            Bukkit.getPluginManager().callEvent(new SellGUIItemsSoldEvent(player, total));
            if (main.getSellGUIAPI() != null) {
                main.getSellGUIAPI().notifyItemsSold(player, soldItems, total);
            }
        }

        if (main.getConfig().getBoolean("general.close-after-sell")) {
            player.closeInventory();
            SellCommand.getSellGUIs().remove(this);
        } else {
            setSellItem();
        }

        String soldMessage = main.getMessagesConfig().getString("sell.sold_success", "&aSold items for &e$%total%!");
        player.sendMessage(color(soldMessage
                .replace("%total%", String.format("%.2f", total))
                .replace("%menu%", menuConfig.getDisplayName())));
        SoundHandler.playConfigSound(player, "sounds.feedback.success");
    }

    public void returnInvalidItems() {
        if (menu == null) {
            return;
        }

        int returned = 0;
        for (int slot = 0; slot < menu.getSize(); slot++) {
            ItemStack item = menu.getItem(slot);
            if (item == null || item.getType() == Material.AIR || isGuiItem(item) || isCustomMenuItem(item)) {
                continue;
            }
            if (isItemSlot(slot) && canAcceptItem(item)) {
                continue;
            }

            menu.setItem(slot, null);
            returned += item.getAmount();
            giveOrDrop(item);
        }

        if (returned > 0) {
            String message = main.getMessagesConfig().getString(
                    "sell.item_not_allowed_in_menu",
                    "&cSome items cannot be sold in %menu% and were returned."
            );
            player.sendMessage(color(message
                    .replace("%count%", String.valueOf(returned))
                    .replace("%menu%", menuConfig.getDisplayName())));
        }
    }

    private void giveOrDrop(ItemStack item) {
        Map<Integer, ItemStack> notAdded = player.getInventory().addItem(item);
        for (ItemStack leftover : notAdded.values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }

    public boolean canAcceptItem(ItemStack item) {
        return menuConfig.allowsItem(item);
    }

    private boolean isSellableMenuItem(int slot, ItemStack item) {
        return item != null
                && item.getType() != Material.AIR
                && isItemSlot(slot)
                && !isGuiItem(item)
                && !isCustomMenuItem(item)
                && canAcceptItem(item);
    }

    private boolean isItemSlot(int slot) {
        return itemSlots.isEmpty() || itemSlots.contains(slot);
    }

    private boolean isValidSlot(int slot) {
        return menu != null && slot >= 0 && slot < menu.getSize();
    }

    private boolean isGuiItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        NamespacedKey guiKey = new NamespacedKey(main, "sellgui");
        return item.getItemMeta().getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE);
    }

    private boolean isCustomMenuItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(main, "custom-menu-item"), PersistentDataType.STRING);
    }

    private boolean isShulkerBox(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return item.getType().name().endsWith("SHULKER_BOX");
    }

    public void logSell(ItemStack itemStack) {
        if (itemStack == null) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getMain().getLog(), true))) {
            Date now = new Date();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            ItemIdentifier.ItemType itemTypeEnum = ItemIdentifier.getItemType(itemStack);
            String itemType = itemTypeEnum.name();
            String itemId = ItemIdentifier.getItemIdentifier(itemStack);
            String displayName = ChatColor.stripColor(ItemIdentifier.getItemDisplayName(itemStack));
            double unitPrice = getPrice(itemStack, player);
            double totalPrice = isShulkerBox(itemStack) ? unitPrice : unitPrice * itemStack.getAmount();
            String playerName = getPlayer().getName();
            String logEntry = String.format("[SELLGUI:%s] %s|%s|%s|%d|%.2f|%.2f|%s|%s",
                    menuConfig.getId(),
                    itemType,
                    itemId,
                    displayName,
                    itemStack.getAmount(),
                    unitPrice,
                    totalPrice,
                    playerName,
                    format.format(now)
            );
            writer.append(logEntry).append(System.lineSeparator());
            writer.flush();
        } catch (IOException e) {
            getMain().getLogger().severe("Failed to write to sell log: " + e.getMessage());
        }
    }

    public ItemStack getConfirmItem() {
        return confirmItem;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getSellItem() {
        return sellItem;
    }

    public Inventory getMenu() {
        return menu;
    }

    @Override
    public Inventory getInventory() {
        return menu;
    }

    public SellGUIMain getMain() {
        return main;
    }

    public String getMenuId() {
        return menuConfig.getId();
    }

    public SellMenuConfig getMenuConfig() {
        return menuConfig;
    }

    public boolean isConfirmMode() {
        return isConfirmMode;
    }

    public boolean isSold() {
        return sold;
    }

    public void setSold(boolean sold) {
        this.sold = sold;
    }

    public String color(String value) {
        if (value == null) {
            return "";
        }
        if (main.isPlaceholderAPIAvailable()) {
            value = main.setPlaceholders(player, value);
        }
        return ColorUtils.color(value);
    }

    public List<String> color(List<String> lore) {
        if (lore == null) {
            return new ArrayList<>();
        }
        return lore.stream().map(this::color).collect(Collectors.toList());
    }
}
