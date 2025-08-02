package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUI;
import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.commands.SellCommand;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;

public class InventoryListeners implements Listener {
    private final SellGUIMain main;

    public InventoryListeners(SellGUIMain main) {
        this.main = main;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        SellGUI sellGUI = SellCommand.getSellGUI(player);

        if (sellGUI != null && event.getInventory().equals(sellGUI.getMenu())) {
            // Drop items back to player
            dropItems(event.getInventory(), player);

            // Clean up the SellGUI
            sellGUI.cleanup();
            SellCommand.getSellGUIs().remove(sellGUI);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        Player player = (Player) e.getWhoClicked();
        Inventory clickedInventory = e.getClickedInventory();
        ItemStack currentItem = e.getCurrentItem();

        // Find the SellGUI for this player
        SellGUI sellGUI = SellCommand.getSellGUI(player);
        if (sellGUI == null || !e.getInventory().equals(sellGUI.getMenu())) {
            return; // Not a SellGUI inventory
        }

        NamespacedKey guiKey = new NamespacedKey(main, "sellgui");
        NamespacedKey actionKey = new NamespacedKey(main, "guiAction");

        // Handle clicks on GUI control items (sell button, confirm button, etc.)
        if (currentItem != null && currentItem.hasItemMeta() &&
                currentItem.getItemMeta().getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE)) {

            e.setCancelled(true);

            String action = currentItem.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            switch (action) {
                case "confirm":
                    sellGUI.sellItems(sellGUI.getMenu());
                    break;
                case "sell":
                    if (sellGUI.getTotal(sellGUI.getMenu()) <= 0) {
                        // Play sound and show message for no items
                        String soundName = main.getSoundsConfig().getString("legacy.no-items-sound", "BLOCK_NOTE_BLOCK_BASS");
                        float volume = (float) main.getSoundsConfig().getDouble("legacy.no-items-volume", 1.0);
                        float pitch = (float) main.getSoundsConfig().getDouble("legacy.no-items-pitch", 1.0);

                        try {
                            Sound sound = Sound.valueOf(soundName);
                            player.playSound(player.getLocation(), sound, volume, pitch);
                        } catch (IllegalArgumentException ex) {
                            main.getLogger().warning("Invalid sound name: " + soundName);
                        }

                        player.closeInventory();
                        player.sendTitle(
                                main.getMessagesConfig().getString("titles.no-items-title", "&cNo items to sell!"),
                                main.getMessagesConfig().getString("titles.no-items-subtitle", ""),
                                10, 70, 20
                        );
                        return;
                    }
                    sellGUI.makeConfirmItem();
                    sellGUI.setConfirmItem();
                    break;
            }
        }
        // Handle clicks on custom menu items
        else if (currentItem != null && isCustomMenuItem(currentItem)) {
            e.setCancelled(true);
            handleCustomMenuItemClick(player, currentItem);
        }
        // Handle clicks in the sellable area
        else if (clickedInventory != null && clickedInventory.equals(sellGUI.getMenu())) {
            // Check if clicking on a GUI control item
            if (isGUIControlItem(currentItem, sellGUI)) {
                e.setCancelled(true);
                return;
            }

            // Prevent shift-clicking items into reserved slots
            if (e.isShiftClick() && wouldGoToReservedSlot(e.getSlot(), sellGUI)) {
                e.setCancelled(true);
                return;
            }

            // Update the sell item total after a delay to allow the inventory change to process
            Bukkit.getScheduler().runTaskLater(main, () -> {
                if (sellGUI.getMenu() != null && player.isOnline() && SellCommand.getSellGUI(player) != null) {
                    sellGUI.updateButtonState();
                }
            }, 1L);
        }
        // Handle clicks in player inventory while SellGUI is open
        else if (clickedInventory != null && clickedInventory.equals(player.getInventory())) {
            // Allow normal player inventory interactions
            // Update total if items are moved
            Bukkit.getScheduler().runTaskLater(main, () -> {
                if (sellGUI.getMenu() != null && player.isOnline() && SellCommand.getSellGUI(player) != null) {
                    sellGUI.updateButtonState();
                }
            }, 1L);
        }
    }

    /**
     * Check if an item is a GUI control item (sell button, filler, etc.)
     */
    private boolean isGUIControlItem(ItemStack item, SellGUI sellGUI) {
        if (item == null) return false;

        NamespacedKey guiKey = new NamespacedKey(main, "sellgui");
        return item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE);
    }

    /**
     * Check if a slot is reserved for GUI controls
     */
    private boolean wouldGoToReservedSlot(int slot, SellGUI sellGUI) {
        // Get reserved slots from config
        if (main.getConfigManager() != null && main.getConfigManager().getGUIConfig() != null) {
            var guiConfig = main.getConfigManager().getGUIConfig();
            var fillerSlots = guiConfig.getIntegerList("sell_gui.positions.filler_slots");
            int sellButtonSlot = guiConfig.getInt("sell_gui.positions.sell-button", 49);
            int confirmButtonSlot = guiConfig.getInt("sell_gui.positions.confirm_button", 53);

            return fillerSlots.contains(slot) || slot == sellButtonSlot || slot == confirmButtonSlot;
        }

        // Default reserved slots for 54-slot inventory (border slots)
        return slot < 9 || slot > 44 || slot % 9 == 0 || slot % 9 == 8;
    }

    /**
     * Handle custom menu item clicks
     */
    private void handleCustomMenuItemClick(Player player, ItemStack item) {
        NamespacedKey key = new NamespacedKey(main, "custom-menu-item");
        String commands = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (commands != null && !commands.isEmpty()) {
            String[] commandArray = commands.split(";");
            for (String command : commandArray) {
                if (!command.trim().isEmpty()) {
                    // Execute the command (replace %player% placeholder)
                    String finalCommand = command.trim().replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                }
            }
        }
    }

    /**
     * Drop items back to player when inventory closes
     */
    private void dropItems(Inventory inventory, Player player) {
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack == null || itemStack.getType() == Material.AIR) continue;

            // Skip GUI control items
            NamespacedKey guiKey = new NamespacedKey(main, "sellgui");
            if (itemStack.hasItemMeta() &&
                    itemStack.getItemMeta().getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE)) {
                continue;
            }

            // Skip custom menu items
            if (isCustomMenuItem(itemStack)) {
                continue;
            }

            // Return items to player
            if (main.getConfig().getBoolean("drop-items-on-close", false)) {
                player.getWorld().dropItem(player.getLocation(), itemStack);
            } else {
                HashMap<Integer, ItemStack> notAdded = player.getInventory().addItem(itemStack);
                if (!notAdded.isEmpty()) {
                    for (ItemStack leftover : notAdded.values()) {
                        player.getWorld().dropItem(player.getLocation(), leftover);
                    }
                }
            }
        }
    }

    /**
     * Check if an item is a custom menu item
     */
    private boolean isCustomMenuItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(main, "custom-menu-item"),
                PersistentDataType.STRING
        );
    }

    /**
     * Static method for compatibility - check if an item is a SellGUI control item
     */
    public static boolean sellGUIItem(ItemStack item, Player player) {
        if (item == null || !item.hasItemMeta()) return false;

        SellGUIMain instance = SellGUIMain.getInstance();
        if (instance == null) return false;

        NamespacedKey guiKey = new NamespacedKey(instance, "sellgui");
        return item.getItemMeta().getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE);
    }
}
