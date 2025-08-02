package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.commands.PriceSetterCommand;
import me.aov.sellgui.gui.PriceSetterGUI;
import me.aov.sellgui.handlers.InventoryHandler;
import me.aov.sellgui.handlers.SoundHandler;
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

        InventoryHolder topHolder = InventoryHandler.getTopInventoryHolder(event);
        if (!(topHolder instanceof PriceSetterGUI)) {
            return;
        }

        PriceSetterGUI gui = (PriceSetterGUI) topHolder;
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        if (event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof PriceSetterGUI) {

            handlePriceSetterGUIClick(event, gui, slot, clickedItem);
        } else {

            return;
        }
    }

    private void handlePriceSetterGUIClick(InventoryClickEvent event, PriceSetterGUI gui, int slot, ItemStack clickedItem) {

        if (slot == PriceSetterGUI.getItemSlot()) {

            if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {

                SoundHandler.playItemPickup((Player) event.getWhoClicked());
                event.setCancelled(false);

                main.getServer().getScheduler().runTaskLater(main, () -> {
                    gui.updateItemInfo();
                }, 1L);
                return;
            } else if (clickedItem != null && clickedItem.getType() != Material.AIR) {

                SoundHandler.playItemPickup((Player) event.getWhoClicked());
                event.setCancelled(false);

                main.getServer().getScheduler().runTaskLater(main, () -> {
                    gui.updateItemInfo();
                }, 1L);
                return;
            }
        }

        if (slot == PriceSetterGUI.getPriceInputSlot()) {
            event.setCancelled(true);
            return;
        }

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

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        InventoryHolder topHolder = InventoryHandler.getTopInventoryHolder(event);
        if (!(topHolder instanceof PriceSetterGUI)) {
            return;
        }

        PriceSetterGUI gui = (PriceSetterGUI) topHolder;

        boolean dragInGUI = false;
        boolean dragToItemSlot = false;

        int topInventorySize = InventoryHandler.getTopInventorySize(event);
        for (int rawSlot : event.getRawSlots()) {

            if (rawSlot < topInventorySize) {
                dragInGUI = true;
                if (rawSlot == PriceSetterGUI.getItemSlot()) {
                    dragToItemSlot = true;
                }
            }
        }

        if (dragInGUI) {

            if (!dragToItemSlot) {
                event.setCancelled(true);
            } else {

                main.getServer().getScheduler().runTaskLater(main, () -> {
                    gui.updateItemInfo();
                }, 1L);
            }
        }

    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof PriceSetterGUI) {

            PriceSetterCommand.removePriceSetterGUI(player);

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

                ItemStack item = gui.getInventory().getItem(PriceSetterGUI.getItemSlot());
                if (item == null || item.getType() == Material.AIR) {
                    SoundHandler.playError(player);
                    player.sendMessage(color("&cNo item found to save price for!"));
                    return;
                }

                SoundHandler.playUIClick(player);
                player.sendMessage(color("&aUse &f/sellguiprice <price> &ato set the price for this item."));
                break;

            case "cancel":
                SoundHandler.playChestClose(player);
                player.closeInventory();
                player.sendMessage(color("&cPrice setting cancelled."));
                break;

            case "delete":
                boolean success = gui.deletePrice();
                if (success) {
                    SoundHandler.playSuccess(player);
                    player.sendMessage(color("&aPrice deleted successfully!"));
                } else {
                    SoundHandler.playError(player);
                }
                break;

            case "chat":

                ItemStack e = gui.getInventory().getItem(PriceSetterGUI.getItemSlot());
                if (e == null || e.getType() == Material.AIR) {
                    SoundHandler.playError(player);
                    player.sendMessage(color("&cNo item found to set price for!"));
                    return;
                }

                PriceSetterChatListener.setPlayerItem(player, e.clone());

                SoundHandler.playUIClick(player);
                player.closeInventory();
                PriceSetterChatListener.startWaitingForPrice(player);
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