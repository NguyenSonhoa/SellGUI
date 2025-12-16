package me.aov.sellgui.gui;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.utils.ColorUtils;
import me.aov.sellgui.utils.ItemIdentifier;
import org.bukkit.Bukkit;
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

public class PriceSetterGUI implements InventoryHolder {

    private final SellGUIMain main;
    private final Player player;
    private final Inventory inventory;
    private final PriceManager priceManager;

    private static final int ITEM_SLOT = 13;
    private static final int PRICE_INPUT_SLOT = 22;
    private static final int SAVE_BUTTON_SLOT = 29;
    private static final int CANCEL_BUTTON_SLOT = 33;
    private static final int DELETE_BUTTON_SLOT = 31;
    private static final int CHAT_INPUT_SLOT = 40;
    private static final int INFO_SLOT = 4;
    public PriceSetterGUI(SellGUIMain main, Player player) {
        this.main = main;
        this.player = player;
        this.priceManager = new PriceManager(main);
        this.inventory = Bukkit.createInventory(this, 45, ColorUtils.color("&6&lPrice Setter"));

        setupGUI();
        player.openInventory(inventory);
    }

    private void setupGUI() {
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(ITEM_SLOT, null);
        inventory.setItem(PRICE_INPUT_SLOT, null);

        ItemStack infoItem = createItem(Material.BOOK,
                ColorUtils.color("&e&lHow to use:"),
                Arrays.asList(
                        ColorUtils.color("&71. Drag an item to the center slot"),
                        ColorUtils.color("&72. Use /sellguiprice <price> to set price"),
                        ColorUtils.color("&73. Click Save to confirm"),
                        ColorUtils.color("&7"),
                        ColorUtils.color("&eSupports: Vanilla, MMOItems, Nexo")
                )
        );
        inventory.setItem(INFO_SLOT, infoItem);

        setupControlButtons();
    }

    private void setupControlButtons() {

        ItemStack saveButton = createItem(Material.GREEN_CONCRETE,
                ColorUtils.color("&a&lSave Price"),
                Arrays.asList(
                        ColorUtils.color("&7Click to save the current price"),
                        ColorUtils.color("&7for the item in the center slot")
                )
        );
        addPersistentData(saveButton, "price-setter-action", "save");
        inventory.setItem(SAVE_BUTTON_SLOT, saveButton);

        ItemStack cancelButton = createItem(Material.RED_CONCRETE,
                ColorUtils.color("&c&lCancel"),
                Arrays.asList(
                        ColorUtils.color("&7Click to close without saving")
                )
        );
        addPersistentData(cancelButton, "price-setter-action", "cancel");
        inventory.setItem(CANCEL_BUTTON_SLOT, cancelButton);

        ItemStack deleteButton = createItem(Material.BARRIER,
                ColorUtils.color("&4&lDelete Price"),
                Arrays.asList(
                        ColorUtils.color("&7Click to remove the price"),
                        ColorUtils.color("&7for the item in the center slot")
                )
        );
        addPersistentData(deleteButton, "price-setter-action", "delete");
        inventory.setItem(DELETE_BUTTON_SLOT, deleteButton);

        ItemStack chatButton = createItem(Material.WRITABLE_BOOK,
                ColorUtils.color("&b&lSet Price via Chat"),
                Arrays.asList(
                        ColorUtils.color("&7Click to close GUI and type price in chat"),
                        ColorUtils.color("&7GUI will reopen automatically after setting"),
                        ColorUtils.color("&7Type 'cancel' to cancel input")
                )
        );
        addPersistentData(chatButton, "price-setter-action", "chat");
        inventory.setItem(CHAT_INPUT_SLOT, chatButton);
    }

    public void updateItemInfo() {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        if (item == null || item.getType() == Material.AIR) {

            inventory.setItem(PRICE_INPUT_SLOT, null);
            return;
        }

        System.out.println("[SellGUI Debug] Updating item info for: " + item.getType().name());
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            System.out.println("[SellGUI Debug] Item display name: " + item.getItemMeta().getDisplayName());
        }

        ItemIdentifier.debugItemNBT(item);

        double currentPrice = priceManager.getItemPrice(item);
        ItemIdentifier.ItemType itemType = ItemIdentifier.getItemType(item);
        String itemName = ItemIdentifier.getItemDisplayName(item);
        String identifier = ItemIdentifier.getItemIdentifier(item);

        System.out.println("[SellGUI Debug] Item type detected: " + itemType);
        System.out.println("[SellGUI Debug] Item identifier: " + identifier);
        System.out.println("[SellGUI Debug] Current price: " + currentPrice);

        List<String> lore = new ArrayList<>();
        lore.add(ColorUtils.color("&7Item: &f" + itemName));
        lore.add(ColorUtils.color("&7Type: &f" + itemType.name()));
        lore.add(ColorUtils.color("&7Identifier: &f" + (identifier != null ? identifier : "Unknown")));
        lore.add(ColorUtils.color("&7"));
        lore.add(ColorUtils.color("&7Current Price: &e$" + String.format("%.2f", currentPrice)));
        lore.add(ColorUtils.color("&7"));
        lore.add(ColorUtils.color("&eUse: &f/sellguiprice <price>"));
        lore.add(ColorUtils.color("&eto set a new price"));

        ItemStack priceInfo = createItem(Material.GOLD_INGOT,
                ColorUtils.color("&6&lPrice Information"),
                lore
        );

        inventory.setItem(PRICE_INPUT_SLOT, priceInfo);
    }

    public boolean savePrice(double price) {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(ColorUtils.color("&cNo item found to set price for!"));
            return false;
        }

        if (price < 0) {
            player.sendMessage(ColorUtils.color("&cPrice cannot be negative!"));
            return false;
        }

        boolean success = priceManager.setItemPrice(item, price);
        if (success) {
            String itemName = ItemIdentifier.getItemDisplayName(item);
            player.sendMessage(ColorUtils.color("&aSuccessfully set price for &f" + itemName + " &ato &e$" + String.format("%.2f", price)));
            updateItemInfo();
            return true;
        } else {
            player.sendMessage(ColorUtils.color("&cFailed to set price! Check console for errors."));
            return false;
        }
    }

    public boolean deletePrice() {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(ColorUtils.color("&cNo item found to delete price for!"));
            return false;
        }

        boolean success = priceManager.removeItemPrice(item);
        if (success) {
            String itemName = ItemIdentifier.getItemDisplayName(item);
            player.sendMessage(ColorUtils.color("&aSuccessfully removed price for &f" + itemName));
            updateItemInfo();
            return true;
        } else {
            player.sendMessage(ColorUtils.color("&cFailed to remove price! Check console for errors."));
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
