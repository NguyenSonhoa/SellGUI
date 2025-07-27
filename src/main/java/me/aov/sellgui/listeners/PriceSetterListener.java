package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.commands.PriceSetterCommand;
import me.aov.sellgui.gui.PriceSetterGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listener for PriceSetterGUI events
 */
public class PriceSetterListener implements Listener {
    
    private final SellGUIMain main;
    
    public PriceSetterListener(SellGUIMain main) {
        this.main = main;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check if player has PriceSetterGUI open
        InventoryHolder topHolder = player.getOpenInventory().getTopInventory().getHolder();
        if (!(topHolder instanceof PriceSetterGUI)) {
            return;
        }

        PriceSetterGUI gui = (PriceSetterGUI) topHolder;
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        // Check if click is in the top inventory (PriceSetterGUI)
        if (event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof PriceSetterGUI) {
            // Click in PriceSetterGUI inventory
            handlePriceSetterGUIClick(event, gui, slot, clickedItem);
        } else {
            // Click in player inventory - allow normal interaction
            // Only cancel if trying to move items that would break the GUI
            return;
        }
    }

    private void handlePriceSetterGUIClick(InventoryClickEvent event, PriceSetterGUI gui, int slot, ItemStack clickedItem) {
        // Handle item slot (center slot for drag & drop)
        if (slot == PriceSetterGUI.getItemSlot()) {
            // Allow placing items in the center slot
            if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
                // Player is placing an item
                event.setCancelled(false);
                // Update info after a short delay to ensure item is placed
                main.getServer().getScheduler().runTaskLater(main, () -> {
                    gui.updateItemInfo();
                }, 1L);
                return;
            } else if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                // Player is taking an item
                event.setCancelled(false);
                // Update info after a short delay
                main.getServer().getScheduler().runTaskLater(main, () -> {
                    gui.updateItemInfo();
                }, 1L);
                return;
            }
        }
        
        // Handle price info slot (read-only)
        if (slot == PriceSetterGUI.getPriceInputSlot()) {
            event.setCancelled(true);
            return;
        }

        // Handle action buttons
        if (clickedItem != null && clickedItem.hasItemMeta()) {
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta.getPersistentDataContainer().has(new NamespacedKey(main, "price-setter-action"), PersistentDataType.STRING)) {
                event.setCancelled(true);

                String action = meta.getPersistentDataContainer().get(new NamespacedKey(main, "price-setter-action"), PersistentDataType.STRING);
                Player player = (Player) event.getWhoClicked();
                handleActionButton(player, gui, action);
                return;
            }
        }

        // Cancel all other clicks to prevent item movement
        event.setCancelled(true);
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check if player has PriceSetterGUI open
        InventoryHolder topHolder = player.getOpenInventory().getTopInventory().getHolder();
        if (!(topHolder instanceof PriceSetterGUI)) {
            return;
        }

        PriceSetterGUI gui = (PriceSetterGUI) topHolder;

        // Check if any dragged slots are in the PriceSetterGUI
        boolean dragInGUI = false;
        boolean dragToItemSlot = false;

        for (int rawSlot : event.getRawSlots()) {
            // Raw slots 0-53 are typically the top inventory (GUI)
            if (rawSlot < player.getOpenInventory().getTopInventory().getSize()) {
                dragInGUI = true;
                if (rawSlot == PriceSetterGUI.getItemSlot()) {
                    dragToItemSlot = true;
                }
            }
        }

        if (dragInGUI) {
            // Only allow dragging to the item slot
            if (!dragToItemSlot) {
                event.setCancelled(true);
            } else {
                // Update info after drag
                main.getServer().getScheduler().runTaskLater(main, () -> {
                    gui.updateItemInfo();
                }, 1L);
            }
        }
        // If drag is only in player inventory, allow it
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof PriceSetterGUI) {
            // Remove the GUI from tracking
            PriceSetterCommand.removePriceSetterGUI(player);
            
            // Return any item in the center slot to player
            ItemStack item = event.getInventory().getItem(PriceSetterGUI.getItemSlot());
            if (item != null && item.getType() != Material.AIR) {
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(item);
                } else {
                    player.getWorld().dropItem(player.getLocation(), item);
                    player.sendMessage(color("&eItem dropped on ground as your inventory is full!"));
                }
            }
        }
    }
    
    private void handleActionButton(Player player, PriceSetterGUI gui, String action) {
        switch (action.toLowerCase()) {
            case "save":
                // Check if there's an item to save price for
                ItemStack item = gui.getInventory().getItem(PriceSetterGUI.getItemSlot());
                if (item == null || item.getType() == Material.AIR) {
                    player.sendMessage(color("&cNo item found to save price for!"));
                    return;
                }
                
                player.sendMessage(color("&aUse &f/sellguiprice <price> &ato set the price for this item."));
                break;
                
            case "cancel":
                player.closeInventory();
                player.sendMessage(color("&cPrice setting cancelled."));
                break;
                
            case "delete":
                boolean success = gui.deletePrice();
                if (success) {
                    player.sendMessage(color("&aPrice deleted successfully!"));
                }
                break;
                
            default:
                player.sendMessage(color("&cUnknown action: " + action));
                break;
        }
    }
    
    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
