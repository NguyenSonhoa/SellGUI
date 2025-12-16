package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUI;
import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.commands.SellCommand;
import me.aov.sellgui.handlers.SoundHandler;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
            // The sellItems() method has already removed sold items.
            // Any remaining items are unsold and should be returned to the player.
            dropItems(event.getInventory(), player);
            
            sellGUI.cleanup();
            SellCommand.getSellGUIs().remove(sellGUI);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        Player player = (Player) e.getWhoClicked();
        ItemStack currentItem = e.getCurrentItem();

        SellGUI sellGUI = SellCommand.getSellGUI(player);
        if (sellGUI == null || !e.getView().getTopInventory().equals(sellGUI.getMenu())) {
            return;
        }

        // Handle clicks during confirm mode
        if (sellGUI.isConfirmMode()) {
            boolean isConfirmButton = false;
            if (isGUIControlItem(currentItem)) {
                NamespacedKey actionKey = new NamespacedKey(main, "guiAction");
                String action = currentItem.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
                if ("confirm".equals(action)) {
                    isConfirmButton = true;
                }
            }

            if (!isConfirmButton) {
                // Player is not clicking the confirm button.
                // If they clicked on a non-movable GUI item, just cancel the event.
                if (isGUIControlItem(currentItem) || isCustomMenuItem(currentItem)) {
                    e.setCancelled(true);
                    return;
                }
                
                // Otherwise, they are modifying items. Exit confirm mode to allow changes.
                sellGUI.setSellItem();
                // The event is not cancelled, so the item move will happen.
                // The logic below will schedule an update to the GUI.
            }
        }

        if (isGUIControlItem(currentItem)) {
            e.setCancelled(true);

            NamespacedKey actionKey = new NamespacedKey(main, "guiAction");
            String action = currentItem.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            switch (action) {
                case "confirm":
                    if (sellGUI.hasUnevaluatedItems()) {
                        player.closeInventory();
                        String message = main.getMessagesConfig().getString("messages.evaluation-required", "&cYou have items that need to be evaluated before selling.");
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                        return;
                    }
                    SoundHandler.playConfigSound(player, "sounds.ui.confirm");
                    sellGUI.sellItems(sellGUI.getMenu());
                    break;
                case "sell":
                    if (sellGUI.getTotal(sellGUI.getMenu()) <= 0) {
                        SoundHandler.playConfigSound(player, "sounds.feedback.fail");
                        return;
                    }
                    SoundHandler.playUIClick(player);
                    sellGUI.updateButtonState(); // This will switch to confirm mode
                    break;
            }
        } else if (isCustomMenuItem(currentItem)) {
            e.setCancelled(true);
            handleCustomMenuItemClick(player, currentItem);
        } else {
            // For any other click (moving items), schedule an update to the button total.
            Bukkit.getScheduler().runTaskLater(main, () -> {
                if (SellCommand.getSellGUI(player) != null) {
                    sellGUI.updateSellItemTotal();
                }
            }, 1L);
        }
    }

    private boolean isGUIControlItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey guiKey = new NamespacedKey(main, "sellgui");
        return item.getItemMeta().getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE);
    }

    private void handleCustomMenuItemClick(Player player, ItemStack item) {
        NamespacedKey key = new NamespacedKey(main, "custom-menu-item");
        String commands = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (commands != null && !commands.isEmpty()) {
            String[] commandArray = commands.split(";");
            for (String command : commandArray) {
                if (!command.trim().isEmpty()) {
                    String finalCommand = command.trim().replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                }
            }
        }
    }

    private void dropItems(Inventory inventory, Player player) {
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack == null || itemStack.getType() == Material.AIR) continue;
            if (isGUIControlItem(itemStack) || isCustomMenuItem(itemStack)) {
                continue;
            }

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

    private boolean isCustomMenuItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(main, "custom-menu-item"),
                PersistentDataType.STRING
        );
    }
}