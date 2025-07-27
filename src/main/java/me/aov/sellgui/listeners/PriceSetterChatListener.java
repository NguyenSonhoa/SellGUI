package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.commands.PriceSetterCommand;
import me.aov.sellgui.gui.PriceSetterGUI;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.utils.ItemIdentifier;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener for chat input in PriceSetterGUI
 */
public class PriceSetterChatListener implements Listener {
    
    private final SellGUIMain main;
    private static final Map<UUID, Boolean> waitingForPrice = new HashMap<>();
    private static final Map<UUID, ItemStack> playerItems = new HashMap<>();
    
    public PriceSetterChatListener(SellGUIMain main) {
        this.main = main;
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player is waiting for price input
        if (!waitingForPrice.getOrDefault(playerId, false)) {
            return;
        }

        String message = event.getMessage().trim();

        // Cancel the chat event so it doesn't appear in chat
        event.setCancelled(true);

        // Stop waiting for price input
        waitingForPrice.remove(playerId);

        // Handle the price input on main thread
        main.getServer().getScheduler().runTask(main, () -> {
            handlePriceInput(player, message);
        });
    }
    
    private void handlePriceInput(Player player, String input) {
        UUID playerId = player.getUniqueId();
        ItemStack item = playerItems.get(playerId);

        // Handle cancel
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("c")) {
            player.sendMessage(color("&cPrice input cancelled."));
            playerItems.remove(playerId);
            // Reopen GUI
            new PriceSetterGUI(main, player);
            return;
        }

        // Check if we have the item
        if (item == null) {
            player.sendMessage(color("&cNo item found! Please try again."));
            playerItems.remove(playerId);
            return;
        }

        // Parse price
        double price;
        try {
            price = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            player.sendMessage(color("&cInvalid price! Please enter a valid number or 'cancel' to cancel."));
            startWaitingForPrice(player);
            return;
        }

        if (price < 0) {
            player.sendMessage(color("&cPrice cannot be negative! Please enter a valid price or 'cancel' to cancel."));
            startWaitingForPrice(player);
            return;
        }

        // Set the price using PriceManager
        PriceManager priceManager = new PriceManager(main);
        boolean success = priceManager.setItemPrice(item, price);

        if (success) {
            String itemName = ItemIdentifier.getItemDisplayName(item);
            String itemType = ItemIdentifier.getItemType(item).name();

            if (price == 0) {
                player.sendMessage(color("&aSuccessfully removed price for &f" + itemName + " &7(" + itemType + ")"));
            } else {
                player.sendMessage(color("&aSuccessfully set price for &f" + itemName + " &7(" + itemType + ") &ato &e$" + String.format("%.2f", price)));
            }

            // Clean up and reopen GUI
            playerItems.remove(playerId);

            // Reopen GUI with the item already placed
            main.getServer().getScheduler().runTaskLater(main, () -> {
                PriceSetterGUI gui = new PriceSetterGUI(main, player);
                gui.getInventory().setItem(PriceSetterGUI.getItemSlot(), item);
                gui.updateItemInfo();
            }, 1L);

        } else {
            player.sendMessage(color("&cFailed to set price! Check console for errors."));
            playerItems.remove(playerId);
        }
    }
    
    /**
     * Start waiting for price input from a player
     */
    public static void startWaitingForPrice(Player player) {
        waitingForPrice.put(player.getUniqueId(), true);
        player.sendMessage(color("&eEnter the price in chat (or type 'cancel' to cancel):"));
    }
    
    /**
     * Stop waiting for price input from a player
     */
    public static void stopWaitingForPrice(Player player) {
        UUID playerId = player.getUniqueId();
        waitingForPrice.remove(playerId);
        playerItems.remove(playerId);
    }

    /**
     * Check if a player is waiting for price input
     */
    public static boolean isWaitingForPrice(Player player) {
        return waitingForPrice.getOrDefault(player.getUniqueId(), false);
    }

    /**
     * Set the item for a player
     */
    public static void setPlayerItem(Player player, ItemStack item) {
        playerItems.put(player.getUniqueId(), item);
    }

    /**
     * Get the item for a player
     */
    public static ItemStack getPlayerItem(Player player) {
        return playerItems.get(player.getUniqueId());
    }
    
    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
