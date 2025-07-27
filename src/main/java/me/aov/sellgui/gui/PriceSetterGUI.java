package me.aov.sellgui.gui;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.utils.ItemIdentifier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GUI for setting item prices
 */
public class PriceSetterGUI implements InventoryHolder {
    
    private final SellGUIMain main;
    private final Player player;
    private final Inventory inventory;
    private final PriceManager priceManager;
    
    // GUI slots
    private static final int ITEM_SLOT = 13; // Center slot for item
    private static final int PRICE_INPUT_SLOT = 22; // Slot for price input
    private static final int SAVE_BUTTON_SLOT = 29; // Save button
    private static final int CANCEL_BUTTON_SLOT = 33; // Cancel button
    private static final int DELETE_BUTTON_SLOT = 31; // Delete price button
    private static final int CHAT_INPUT_SLOT = 40; // Chat input button
    private static final int INFO_SLOT = 4; // Info item slot
    
    public PriceSetterGUI(SellGUIMain main, Player player) {
        this.main = main;
        this.player = player;
        this.priceManager = new PriceManager(main);
        this.inventory = Bukkit.createInventory(this, 45, color("&6&lPrice Setter"));
        
        setupGUI();
        player.openInventory(inventory);
    }
    
    private void setupGUI() {
        // Fill with glass panes
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        
        // Clear specific slots
        inventory.setItem(ITEM_SLOT, null);
        inventory.setItem(PRICE_INPUT_SLOT, null);
        
        // Add info item
        ItemStack infoItem = createItem(Material.BOOK, 
            color("&e&lHow to use:"),
            Arrays.asList(
                color("&71. Drag an item to the center slot"),
                color("&72. Use /sellguiprice <price> to set price"),
                color("&73. Click Save to confirm"),
                color("&7"),
                color("&eSupports: Vanilla, MMOItems, Nexo")
            )
        );
        inventory.setItem(INFO_SLOT, infoItem);
        
        // Add control buttons
        setupControlButtons();
    }
    
    private void setupControlButtons() {
        // Save button
        ItemStack saveButton = createItem(Material.GREEN_CONCRETE, 
            color("&a&lSave Price"),
            Arrays.asList(
                color("&7Click to save the current price"),
                color("&7for the item in the center slot")
            )
        );
        addPersistentData(saveButton, "price-setter-action", "save");
        inventory.setItem(SAVE_BUTTON_SLOT, saveButton);
        
        // Cancel button
        ItemStack cancelButton = createItem(Material.RED_CONCRETE,
            color("&c&lCancel"),
            Arrays.asList(
                color("&7Click to close without saving")
            )
        );
        addPersistentData(cancelButton, "price-setter-action", "cancel");
        inventory.setItem(CANCEL_BUTTON_SLOT, cancelButton);
        
        // Delete button
        ItemStack deleteButton = createItem(Material.BARRIER,
            color("&4&lDelete Price"),
            Arrays.asList(
                color("&7Click to remove the price"),
                color("&7for the item in the center slot")
            )
        );
        addPersistentData(deleteButton, "price-setter-action", "delete");
        inventory.setItem(DELETE_BUTTON_SLOT, deleteButton);

        // Chat input button
        ItemStack chatButton = createItem(Material.WRITABLE_BOOK,
            color("&b&lSet Price via Chat"),
            Arrays.asList(
                color("&7Click to close GUI and type price in chat"),
                color("&7GUI will reopen automatically after setting"),
                color("&7Type 'cancel' to cancel input")
            )
        );
        addPersistentData(chatButton, "price-setter-action", "chat");
        inventory.setItem(CHAT_INPUT_SLOT, chatButton);
    }
    
    public void updateItemInfo() {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        if (item == null || item.getType() == Material.AIR) {
            // Clear price input slot
            inventory.setItem(PRICE_INPUT_SLOT, null);
            return;
        }

        // Debug logging
        System.out.println("[SellGUI Debug] Updating item info for: " + item.getType().name());
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            System.out.println("[SellGUI Debug] Item display name: " + item.getItemMeta().getDisplayName());
        }

        // Debug NBT tags
        ItemIdentifier.debugItemNBT(item);

        // Get current price
        double currentPrice = priceManager.getItemPrice(item);
        ItemIdentifier.ItemType itemType = ItemIdentifier.getItemType(item);
        String itemName = ItemIdentifier.getItemDisplayName(item);
        String identifier = ItemIdentifier.getItemIdentifier(item);

        // Debug logging
        System.out.println("[SellGUI Debug] Item type detected: " + itemType);
        System.out.println("[SellGUI Debug] Item identifier: " + identifier);
        System.out.println("[SellGUI Debug] Current price: " + currentPrice);
        
        // Create price info item
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Item: &f" + itemName));
        lore.add(color("&7Type: &f" + itemType.name()));
        lore.add(color("&7Identifier: &f" + (identifier != null ? identifier : "Unknown")));
        lore.add(color("&7"));
        lore.add(color("&7Current Price: &e$" + String.format("%.2f", currentPrice)));
        lore.add(color("&7"));
        lore.add(color("&eUse: &f/sellguiprice <price>"));
        lore.add(color("&eto set a new price"));
        
        ItemStack priceInfo = createItem(Material.GOLD_INGOT,
            color("&6&lPrice Information"),
            lore
        );
        
        inventory.setItem(PRICE_INPUT_SLOT, priceInfo);
    }
    
    public boolean savePrice(double price) {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(color("&cNo item found to set price for!"));
            return false;
        }
        
        if (price < 0) {
            player.sendMessage(color("&cPrice cannot be negative!"));
            return false;
        }
        
        boolean success = priceManager.setItemPrice(item, price);
        if (success) {
            String itemName = ItemIdentifier.getItemDisplayName(item);
            player.sendMessage(color("&aSuccessfully set price for &f" + itemName + " &ato &e$" + String.format("%.2f", price)));
            updateItemInfo();
            return true;
        } else {
            player.sendMessage(color("&cFailed to set price! Check console for errors."));
            return false;
        }
    }
    
    public boolean deletePrice() {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(color("&cNo item found to delete price for!"));
            return false;
        }
        
        boolean success = priceManager.removeItemPrice(item);
        if (success) {
            String itemName = ItemIdentifier.getItemDisplayName(item);
            player.sendMessage(color("&aSuccessfully removed price for &f" + itemName));
            updateItemInfo();
            return true;
        } else {
            player.sendMessage(color("&cFailed to remove price! Check console for errors."));
            return false;
        }
    }
    
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private void addPersistentData(ItemStack item, String key, String value) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                new NamespacedKey(main, key), 
                PersistentDataType.STRING, 
                value
            );
            item.setItemMeta(meta);
        }
    }
    
    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    public Player getPlayer() {
        return player;
    }
    
    public static int getItemSlot() {
        return ITEM_SLOT;
    }
    
    public static int getPriceInputSlot() {
        return PRICE_INPUT_SLOT;
    }
}
