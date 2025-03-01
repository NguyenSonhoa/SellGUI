package me.aov.sellgui.mmoitems;

import io.lumine.mythic.lib.api.item.NBTItem;
import me.aov.sellgui.SellGUIMain;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import static io.lumine.mythic.lib.api.util.ui.SilentNumbers.getItemName;

public class MMOItemsPriceEditor implements Listener {
    private static Inventory editorMenu;
    private static Map<String, Double> itemPrices = new HashMap<>();
    private static Map<Player, String> priceSetMap = new HashMap<>();
    private static FileConfiguration config;
    private static File configFile;
    private final SellGUIMain plugin;

    public Map<String, Double> getItemPrices() {
        return itemPrices;
    }

    public MMOItemsPriceEditor(SellGUIMain plugin) {
        this.plugin = plugin;
        editorMenu = Bukkit.createInventory(null, 54, ChatColor.GOLD + "MMOItems Price Editor");
        configFile = new File(plugin.getDataFolder(), "mmoitems.yml");

        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        loadPrices();
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("SellGUI"));
    }
    private static final int MAX_ITEMS_PER_PAGE = 51; //
    private static final ItemStack NEXT_PAGE_BUTTON = createNextPageItem();
    private static final ItemStack PREVIOUS_PAGE_BUTTON = createPreviousPageItem();

    private int currentPage = 0;
    public void openEditor(Player player) {
        editorMenu.clear();
        int startIndex = currentPage * MAX_ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + MAX_ITEMS_PER_PAGE, itemPrices.size());
        List<Map.Entry<String, Double>> entries = new ArrayList<>(itemPrices.entrySet());

        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<String, Double> entry = entries.get(i);
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();

            if (meta == null) {
                continue;
            }

            meta.setDisplayName(ChatColor.AQUA + "MMOItems: " + entry.getKey());

            meta.setLore(Arrays.asList(
                    ChatColor.GREEN + "Price: " + entry.getValue(),
                    ChatColor.YELLOW + "Click to update price.",
                    ChatColor.YELLOW + "To delete, edit & remove in mmoitems.yml"
            ));

            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "mmoitems_id"), PersistentDataType.STRING, entry.getKey());
            paper.setItemMeta(meta);
            editorMenu.addItem(paper);
        }

        if (currentPage > 0) {
            editorMenu.setItem(51, PREVIOUS_PAGE_BUTTON);
        }

        if (endIndex < itemPrices.size()) {
            editorMenu.setItem(52, NEXT_PAGE_BUTTON);
        } else {
            editorMenu.setItem(52, new ItemStack(Material.AIR));
        }

        editorMenu.setItem(53, createInstructionBook());
        player.openInventory(editorMenu);
    }

    private static ItemStack createPreviousPageItem() {
        ItemStack previousPage = new ItemStack(Material.ARROW);
        ItemMeta meta = previousPage.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Previous Page");
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "Click to go back to previous page."));
        previousPage.setItemMeta(meta);
        return previousPage;
    }

    private static ItemStack createNextPageItem() {
        ItemStack nextPage = new ItemStack(Material.ARROW);
        ItemMeta meta = nextPage.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Next Page");
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "Click to view more prices."));
        nextPage.setItemMeta(meta);
        return nextPage;
    }



    private static ItemStack createInstructionBook() {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "MMOItems Price Editor Instructions");
        meta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Click on a paper to edit its price.",
                ChatColor.YELLOW + "Click an mmoitems in inventory",
                ChatColor.YELLOW + "then to set a price!",
                ChatColor.YELLOW + "Beta!!"
        ));
        book.setItemMeta(meta);
        return book;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();
        if (!event.getView().getTitle().equals(ChatColor.GOLD + "MMOItems Price Editor")) {
            return;
        }


        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        if (clickedInventory.equals(player.getInventory())) {
            NBTItem nbtItem = NBTItem.get(clickedItem);
            if (nbtItem.hasTag("MMOITEMS_ITEM_ID")) {
                String itemId = nbtItem.getString("MMOITEMS_ITEM_ID");

                if (!itemPrices.containsKey(itemId)) {
                    itemPrices.put(itemId, 0.0);
                    config.set("mmoitems." + itemId, 0.0);
                    saveConfig();

                    player.sendMessage(ChatColor.GREEN + "✔ Added MMOItem: " + itemId);
                    player.sendMessage(ChatColor.YELLOW + "Enter a price for this item in chat.");
                    priceSetMap.put(player, itemId);
                } else {
                    player.sendMessage(ChatColor.RED + "This item already has a price.");
                }
            }
            event.setCancelled(true);
        }

        if (clickedItem.getType() == Material.PAPER) {
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "mmoitems_id"), PersistentDataType.STRING)) {
                String fullItemId = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "mmoitems_id"), PersistentDataType.STRING);
                if (fullItemId != null) {
                    priceSetMap.put(player, fullItemId);
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.sendMessage(ChatColor.YELLOW + "Enter a new price for this MMOItem in chat.");
                    }, 5L);
                }
            }
            event.setCancelled(true);
        }
    }






    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (priceSetMap.containsKey(player)) {
            event.setCancelled(true);

            try {
                double newPrice = Double.parseDouble(event.getMessage());
                newPrice = Math.max(0, newPrice);

                String itemId = priceSetMap.get(player);

                if (!itemPrices.containsKey(itemId)) {
                    player.sendMessage(ChatColor.RED + "Error: This is not a valid MMOItem.");
                    return;
                }

                itemPrices.put(itemId, newPrice);
                config.set("mmoitems." + itemId, newPrice);
                saveConfig();

                player.sendMessage(ChatColor.GREEN + "✔ Price set to: " + newPrice);
                priceSetMap.remove(player);

                Bukkit.getScheduler().runTask(plugin, () -> openEditor(player));

            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "❌ Invalid number. Please enter a valid price.");
            }
        }
    }





    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(ChatColor.GOLD + "MMOItems Price Editor")) {
            if (pricesChanged()) {
                savePrices();
            }
        }
    }

    private boolean pricesChanged() {
        for (String itemId : itemPrices.keySet()) {
            double currentPrice = itemPrices.get(itemId);
            double savedPrice = config.getDouble("mmoitems." + itemId, -1);
            if (currentPrice != savedPrice) {
                return true;
            }
        }
        return false;
    }

    public void reloadMMOItemsConfig() {
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
        }
        loadPrices();
    }

    public void loadPrices() {
        itemPrices.clear();

        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
        }

        if (config.contains("mmoitems")) {
            for (String itemId : config.getConfigurationSection("mmoitems").getKeys(false)) {
                itemPrices.put(itemId, config.getDouble("mmoitems." + itemId));
            }
        }
    }

    private void savePrices() {
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
        }
        for (Map.Entry<String, Double> entry : itemPrices.entrySet()) {
            config.set("mmoitems." + entry.getKey(), entry.getValue());
        }
        for (String itemId : config.getConfigurationSection("mmoitems").getKeys(false)) {
            if (!itemPrices.containsKey(itemId)) {
                config.set("mmoitems." + itemId, null);
            }
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}