package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.commands.PriceSetterCommand;
import me.aov.sellgui.gui.PriceSetterGUI;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.utils.ItemIdentifier;
import me.aov.sellgui.handlers.SoundHandler;
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

        if (!waitingForPrice.getOrDefault(playerId, false)) {
            return;
        }

        String message = event.getMessage().trim();

        event.setCancelled(true);

        waitingForPrice.remove(playerId);

        main.getServer().getScheduler().runTask(main, () -> {
            handlePriceInput(player, message);
        });
    }

    private void handlePriceInput(Player player, String input) {
        UUID playerId = player.getUniqueId();
        ItemStack item = playerItems.get(playerId);

        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("c")) {
            player.sendMessage(color("&cPrice input cancelled."));
            playerItems.remove(playerId);

            new PriceSetterGUI(main, player);
            return;
        }

        if (item == null) {
            player.sendMessage(color("&cNo item found! Please try again."));
            playerItems.remove(playerId);
            return;
        }

        double price;
        try {
            price = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            SoundHandler.playError(player);
            player.sendMessage(color("&cInvalid price! Please enter a valid number or 'cancel' to cancel."));
            startWaitingForPrice(player);
            return;
        }

        if (price < 0) {
            SoundHandler.playError(player);
            player.sendMessage(color("&cPrice cannot be negative! Please enter a valid price or 'cancel' to cancel."));
            startWaitingForPrice(player);
            return;
        }

        PriceManager priceManager = new PriceManager(main);
        boolean success = priceManager.setItemPrice(item, price);

        if (success) {
            SoundHandler.playSuccess(player);
            String itemName = ItemIdentifier.getItemDisplayName(item);
            String itemType = ItemIdentifier.getItemType(item).name();

            if (price == 0) {
                player.sendMessage(color("&aSuccessfully removed price for &f" + itemName + " &7(" + itemType + ")"));
            } else {
                player.sendMessage(color("&aSuccessfully set price for &f" + itemName + " &7(" + itemType + ") &ato &e$" + String.format("%.2f", price)));
            }

            playerItems.remove(playerId);

            main.getServer().getScheduler().runTaskLater(main, () -> {
                PriceSetterGUI gui = new PriceSetterGUI(main, player);
                gui.getInventory().setItem(PriceSetterGUI.getItemSlot(), item);
                gui.updateItemInfo();
            }, 1L);

        } else {
            SoundHandler.playError(player);
            player.sendMessage(color("&cFailed to set price! Check console for errors."));
            playerItems.remove(playerId);
        }
    }

    public static void startWaitingForPrice(Player player) {
        waitingForPrice.put(player.getUniqueId(), true);
        player.sendMessage(color("&eEnter the price in chat (or type 'cancel' to cancel):"));
    }

    public static void stopWaitingForPrice(Player player) {
        UUID playerId = player.getUniqueId();
        waitingForPrice.remove(playerId);
        playerItems.remove(playerId);
    }

    public static boolean isWaitingForPrice(Player player) {
        return waitingForPrice.getOrDefault(player.getUniqueId(), false);
    }

    public static void setPlayerItem(Player player, ItemStack item) {
        playerItems.put(player.getUniqueId(), item);
    }

    public static ItemStack getPlayerItem(Player player) {
        return playerItems.get(player.getUniqueId());
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}