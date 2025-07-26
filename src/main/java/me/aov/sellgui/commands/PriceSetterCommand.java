package me.aov.sellgui.commands;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.gui.PriceSetterGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Command handler for the price setter functionality
 */
public class PriceSetterCommand implements CommandExecutor {
    
    private final SellGUIMain main;
    private static final Map<UUID, PriceSetterGUI> openGUIs = new HashMap<>();
    
    public PriceSetterCommand(SellGUIMain main) {
        this.main = main;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(color(main.getLangConfig().getString("price-setter-players-only", "&cThis command can only be used by players!")));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Handle /sellguiprice command (open GUI)
        if (command.getName().equalsIgnoreCase("sellguiprice")) {
            if (args.length == 0) {
                // Open GUI
                if (!player.hasPermission("sellgui.setprice")) {
                    player.sendMessage(color(main.getLangConfig().getString("price-setter-no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }
                
                PriceSetterGUI gui = new PriceSetterGUI(main, player);
                openGUIs.put(player.getUniqueId(), gui);
                return true;
            } else if (args.length == 1) {
                // Set price for item in GUI
                return handlePriceSet(player, args[0]);
            } else {
                player.sendMessage(color("&cUsage: /sellguiprice [price]"));
                return true;
            }
        }
        
        return false;
    }
    
    private boolean handlePriceSet(Player player, String priceString) {
        // Check if player has price setter GUI open
        PriceSetterGUI gui = openGUIs.get(player.getUniqueId());
        if (gui == null) {
            // Check if player's current inventory is a PriceSetterGUI
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (!(holder instanceof PriceSetterGUI)) {
                player.sendMessage(color("&cYou need to have the Price Setter GUI open to use this command!"));
                return true;
            }
            gui = (PriceSetterGUI) holder;
        }
        
        // Parse price
        double price;
        try {
            price = Double.parseDouble(priceString);
        } catch (NumberFormatException e) {
            player.sendMessage(color("&cInvalid price! Please enter a valid number."));
            return true;
        }
        
        if (price < 0) {
            player.sendMessage(color("&cPrice cannot be negative!"));
            return true;
        }
        
        // Set the price
        boolean success = gui.savePrice(price);
        if (success) {
            player.sendMessage(color("&aPrice set successfully! Click the Save button to confirm."));
        }
        
        return true;
    }
    
    /**
     * Get the PriceSetterGUI for a player
     */
    public static PriceSetterGUI getPriceSetterGUI(Player player) {
        return openGUIs.get(player.getUniqueId());
    }
    
    /**
     * Remove a player's PriceSetterGUI
     */
    public static void removePriceSetterGUI(Player player) {
        openGUIs.remove(player.getUniqueId());
    }
    
    /**
     * Check if a player has a PriceSetterGUI open
     */
    public static boolean hasPriceSetterGUI(Player player) {
        return openGUIs.containsKey(player.getUniqueId());
    }
    
    /**
     * Get all open PriceSetterGUIs
     */
    public static Map<UUID, PriceSetterGUI> getOpenGUIs() {
        return openGUIs;
    }
    
    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
