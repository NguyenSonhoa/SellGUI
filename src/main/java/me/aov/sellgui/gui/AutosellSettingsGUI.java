package me.aov.sellgui.gui;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.listeners.AutosellSearchListener;
import me.aov.sellgui.utils.ColorUtils;
import me.aov.sellgui.utils.ItemIdentifier;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AutosellSettingsGUI implements InventoryHolder, Listener {

    private final SellGUIMain plugin;
    private final Player player;
    private final Inventory inventory;
    private int currentPage = 0;
    private final List<String> pricedItemIdentifiers = new ArrayList<>();
    private String searchQuery = null;

    private final Map<Integer, String> controlTags = new HashMap<>();
    private final Map<Integer, String> itemTags = new HashMap<>();

    public AutosellSettingsGUI(SellGUIMain plugin, Player player) {
        this(plugin, player, null);
    }

    public AutosellSettingsGUI(SellGUIMain plugin, Player player, String searchQuery) {
        this.plugin = plugin;
        this.player = player;
        this.searchQuery = (searchQuery != null && searchQuery.equalsIgnoreCase("clear")) ? null : searchQuery;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        String calculationMethod = plugin.getConfig().getString("prices.calculation-method", "auto");
        if ("shopguiplus".equalsIgnoreCase(calculationMethod) && plugin.hasShopGUIPlus) {
            try {
                Set<Shop> shops = ShopGuiPlusApi.getPlugin().getShopManager().getShops();
                if (shops != null) {
                    for (Shop shop : shops) {
                        List<ShopItem> shopItems = shop.getShopItems();
                        if (shopItems != null) {
                            for (ShopItem shopItem : shopItems) {
                                ItemStack item = shopItem.getItem();
                                if (item != null) {
                                    String identifier = ItemIdentifier.getItemIdentifier(item);
                                    if (identifier != null && !pricedItemIdentifiers.contains(identifier)) {
                                        pricedItemIdentifiers.add(identifier);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error loading ShopGUI+ items for Autosell GUI: " + e.getMessage());
            }
        } else {
            Map<String, Double> allPricedItems = plugin.getPriceManager().getAllPricedItems();
            pricedItemIdentifiers.addAll(allPricedItems.keySet());
        }

        String title = ColorUtils.color(plugin.getConfigManager().getAutosellGuiTitle());
        int size = plugin.getConfigManager().getAutosellGuiSize();
        this.inventory = Bukkit.createInventory(this, size, title);
        setupGUI();
    }


    private void setupGUI() {
        inventory.clear();
        controlTags.clear();
        itemTags.clear();

        List<String> identifiersToDisplay = new ArrayList<>();
        if (searchQuery != null && !searchQuery.isEmpty()) {
            for (String identifier : pricedItemIdentifiers) {
                ItemStack item = ItemIdentifier.getItemStackFromIdentifier(identifier);
                if (item != null) {
                    String displayName = ItemIdentifier.getItemDisplayName(item);
                    if (displayName.toLowerCase().contains(searchQuery.toLowerCase())) {
                        identifiersToDisplay.add(identifier);
                    }
                }
            }
        } else {
            identifiersToDisplay.addAll(pricedItemIdentifiers);
        }

        ConfigurationSection fillerSection = plugin.getConfigManager().getAutosellGuiFillerItem();
        if (fillerSection != null) {
            ItemStack fillerItem = createItemFromConfig(fillerSection, " ");
            if (fillerItem != null) {
                List<Integer> fillerSlots = plugin.getConfigManager().getAutosellSettingsGUIConfig().getIntegerList("positions.filler_slots");
                for (int slot : fillerSlots) {
                    inventory.setItem(slot, fillerItem);
                }
            }
        }

        boolean isGlobalAutosellEnabled = plugin.getAutosellManager().isGlobalAutosellEnabled(player.getUniqueId());
        ConfigurationSection toggleButtonSection = isGlobalAutosellEnabled ?
                plugin.getConfigManager().getAutosellGuiDisableAllButton() :
                plugin.getConfigManager().getAutosellGuiEnableAllButton();
        List<Integer> toggleButtonSlots = isGlobalAutosellEnabled ?
                plugin.getConfigManager().getAutosellSettingsGUIConfig().getIntegerList("positions.disable_all_button") :
                plugin.getConfigManager().getAutosellSettingsGUIConfig().getIntegerList("positions.enable_all_button");

        if (toggleButtonSection != null) {
            ItemStack toggleButton = createItemFromConfig(toggleButtonSection, "Global Autosell Toggle");
            if (toggleButton != null) {
                for (int toggleButtonSlot : toggleButtonSlots) {
                    inventory.setItem(toggleButtonSlot, toggleButton);
                    controlTags.put(toggleButtonSlot, "global_toggle");
                }
            }
        }

        ConfigurationSection searchButtonSection = plugin.getConfigManager().getAutosellGuiSearchButton();
        if (searchButtonSection != null) {
            ItemStack searchButton = createItemFromConfig(searchButtonSection, "Search");
            if (searchButton != null) {
                List<Integer> searchButtonSlots = plugin.getConfigManager().getAutosellSettingsGUIConfig().getIntegerList("positions.search_button");
                for (int searchButtonSlot : searchButtonSlots) {
                    inventory.setItem(searchButtonSlot, searchButton);
                    controlTags.put(searchButtonSlot, "search");
                }
            }
        }


        if (identifiersToDisplay.isEmpty()) {
            ConfigurationSection noPricedItemsSection = plugin.getConfigManager().getAutosellGuiNoPricedItems();
            if (noPricedItemsSection != null) {
                ItemStack noItems = createItemFromConfig(noPricedItemsSection, "&c&l❌ No Priced Items");
                if (noItems != null) {
                    inventory.setItem(22, noItems);
                }
            }
            addPaginationControls(0);
            return;
        }

        List<Integer> itemSlots = plugin.getConfigManager().getAutosellSettingsGUIConfig().getIntegerList("positions.item_slots");
        int itemsPerPage = itemSlots.size();
        int start = currentPage * itemsPerPage;
        int end = Math.min(start + itemsPerPage, identifiersToDisplay.size());

        int currentSlotIndex = 0;
        for (int i = start; i < end; i++) {
            String itemIdentifier = identifiersToDisplay.get(i);
            ItemStack originalItem = ItemIdentifier.getItemStackFromIdentifier(itemIdentifier);

            if (originalItem != null) {
                boolean autosellEnabled = plugin.getAutosellManager().isAutosellEnabled(player.getUniqueId(), itemIdentifier);
                ConfigurationSection itemConfigSection = autosellEnabled ?
                        plugin.getConfigManager().getAutosellGuiEnabledAutosellItem() :
                        plugin.getConfigManager().getAutosellGuiDisabledAutosellItem();

                if (itemConfigSection != null) {
                    ItemStack displayItem = createItemFromConfig(itemConfigSection, ItemIdentifier.getItemDisplayName(originalItem));
                    if (displayItem != null) {
                        ItemMeta meta = displayItem.getItemMeta();
                        String originalItemName = ItemIdentifier.getItemDisplayName(originalItem);
                        
                        // Use getItemPriceWithPlayer to get the correct price (including ShopGUI+ if enabled)
                        double price = plugin.getPriceManager().getItemPriceWithPlayer(originalItem, player);

                        if (meta.hasDisplayName()) {
                            String displayName = meta.getDisplayName().replace("%item_name%", originalItemName)
                                    .replace("%price%", String.format(plugin.getConfigManager().getMoneyFormat(), price));
                            meta.setDisplayName(displayName);
                        }

                        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                        for (int j = 0; j < lore.size(); j++) {
                            lore.set(j, lore.get(j).replace("%item_name%", originalItemName)
                                    .replace("%price%", String.format(plugin.getConfigManager().getMoneyFormat(), price)));
                        }
                        meta.setLore(lore);

                        if (searchQuery != null && !searchQuery.isEmpty()) {
                            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                        }

                        displayItem.setItemMeta(meta);

                        int itemSlot = itemSlots.get(currentSlotIndex++);
                        inventory.setItem(itemSlot, displayItem);
                        itemTags.put(itemSlot, itemIdentifier);
                    }
                }
            }
        }
        addPaginationControls(identifiersToDisplay.size());
    }

    private void addPaginationControls(int totalItems) {
        List<Integer> itemSlots = plugin.getConfigManager().getAutosellSettingsGUIConfig().getIntegerList("positions.item_slots");
        int itemsPerPage = itemSlots.size();

        if (currentPage > 0) {
            ConfigurationSection prevButtonSection = plugin.getConfigManager().getAutosellGuiPreviousPageButton();
            if (prevButtonSection != null) {
                ItemStack prevPage = createItemFromConfig(prevButtonSection, "&aPrevious Page");
                if (prevPage != null) {
                    List<Integer> prevButtonSlots = plugin.getConfigManager().getAutosellSettingsGUIConfig().getIntegerList("positions.previous_page_button");
                    for (int prevButtonSlot : prevButtonSlots) {
                        inventory.setItem(prevButtonSlot, prevPage);
                        controlTags.put(prevButtonSlot, "previous");
                    }
                }
            }
        }

        if ((currentPage + 1) * itemsPerPage < totalItems) {
            ConfigurationSection nextButtonSection = plugin.getConfigManager().getAutosellGuiNextPageButton();
            if (nextButtonSection != null) {
                ItemStack nextPage = createItemFromConfig(nextButtonSection, "&aNext Page");
                if (nextPage != null) {
                    List<Integer> nextButtonSlots = plugin.getConfigManager().getAutosellSettingsGUIConfig().getIntegerList("positions.next_page_button");
                    for (int nextButtonSlot : nextButtonSlots) {
                        inventory.setItem(nextButtonSlot, nextPage);
                        controlTags.put(nextButtonSlot, "next");
                    }
                }
            }
        }
    }

    private ItemStack createItemFromConfig(ConfigurationSection config, String defaultName) {
        if (config == null) return null;
        Material material = Material.matchMaterial(config.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String name = config.getString("name", defaultName);
        if (name != null) {
            meta.setDisplayName(ColorUtils.color(name));
        }
        List<String> lore = config.getStringList("lore");
        if (lore != null) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ColorUtils.color(line));
            }
            meta.setLore(coloredLore);
        }
        if (config.contains("custom-model-data")) {
            meta.setCustomModelData(config.getInt("custom-model-data"));
        }
        if (config.getBoolean("glow", false)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player clicker = (Player) event.getWhoClicked();
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        int clickedSlot = event.getRawSlot();
        String controlTag = controlTags.get(clickedSlot);
        if (controlTag != null) {
            switch (controlTag) {
                case "global_toggle":
                    boolean isGlobalAutosellEnabled = plugin.getAutosellManager().isGlobalAutosellEnabled(clicker.getUniqueId());
                    plugin.getAutosellManager().setGlobalAutosellEnabled(clicker.getUniqueId(), !isGlobalAutosellEnabled);
                    String messageKey = !isGlobalAutosellEnabled ? "autosell.enable_autosell" : "autosell.disable_autosell";
                    String message = plugin.getConfigManager().getMessagesConfig().getString(messageKey);
                    if (message != null) {
                        clicker.sendMessage(ColorUtils.color(message));
                    }
                    setupGUI();
                    break;
                case "previous":
                    if (currentPage > 0) {
                        currentPage--;
                        setupGUI();
                    }
                    break;
                case "next":
                    currentPage++;
                    setupGUI();
                    break;
                case "search":
                    AutosellSearchListener.addSearchingPlayer(clicker.getUniqueId());
                    clicker.closeInventory();
                    String searchMessage = plugin.getConfigManager().getMessagesConfig().getString("autosell.search_prompt");
                    if (searchMessage != null) {
                        clicker.sendMessage(ColorUtils.color(searchMessage));
                    }
                    break;
            }
            return;
        }

        String itemIdentifier = itemTags.get(clickedSlot);
        if (itemIdentifier != null) {
            boolean currentlyEnabled = plugin.getAutosellManager().isAutosellEnabled(clicker.getUniqueId(), itemIdentifier);
            plugin.getAutosellManager().setAutosellEnabled(clicker.getUniqueId(), itemIdentifier, !currentlyEnabled);
            String messageKey = !currentlyEnabled ? "autosell.enabled" : "autosell.disabled";
            ItemStack originalItem = ItemIdentifier.getItemStackFromIdentifier(itemIdentifier);
            String itemName = ItemIdentifier.getItemDisplayName(originalItem);
            String message = plugin.getConfigManager().getMessagesConfig().getString(messageKey);
            if (message != null) {
                clicker.sendMessage(ColorUtils.color(message.replace("%item_name%", itemName)));
            }
            setupGUI();
        }
    }
}
