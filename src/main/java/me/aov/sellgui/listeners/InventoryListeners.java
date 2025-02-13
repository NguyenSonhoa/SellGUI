package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUI;
import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.commands.CustomItemsCommand;
import me.aov.sellgui.commands.SellCommand;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class InventoryListeners implements Listener {
    private static SellGUIMain main;

    public InventoryListeners(SellGUIMain main) {
        this.main = main;
    }

    @EventHandler
    public void inventoryClose(InventoryCloseEvent event) {
        if (SellCommand.isSellGUI(event.getInventory())) {
            dropItems(event.getInventory(), (Player) event.getPlayer());
            SellCommand.getSellGUIs().remove(SellCommand.getSellGUI(event.getInventory()));
        } else if (event.getInventory().equals(CustomItemsCommand.getMenu())) {
            CustomItemsCommand.addToList();
            CustomItemsCommand.saveStuff();
        }
    }

    @EventHandler
    public void inventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        if (SellCommand.openSellGUI(player)) {
            SellGUI sellGUI = SellCommand.getSellGUI(player);
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || clickedItem.getType().isAir()) return;

            if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getOpenInventory().getTopInventory())) {
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(main, "sellgui"), PersistentDataType.BYTE)) {
                event.setCancelled(true);
                return;
            }

            sellGUI.addSellItem();

            if (clickedItem.isSimilar(sellGUI.getConfirmItem())) {
                executeCommandFromConfig(player, "confirm-item-command");
                sellGUI.sellItems(sellGUI.getMenu());
                event.setCancelled(true);
            } else if (clickedItem.isSimilar(SellGUI.getSellItem())) {
                executeCommandFromConfig(player, "sell-item-command");
                sellGUI.makeConfirmItem();
                sellGUI.setConfirmItem();
                event.setCancelled(true);
            } else if (clickedItem.isSimilar(SellGUI.getFiller())) {
                event.setCancelled(true);  //
            }
        } else if (CustomItemsCommand.getMenu().equals(event.getClickedInventory())) {
            handleCustomItemsClick(event);
        }
    }

    private void executeCommandFromConfig(Player player, String commandKey) {
        String command = main.getConfig().getString(commandKey);
        if (command != null) {
            command = command.replace("%player%", player.getName());
            main.getServer().dispatchCommand(main.getServer().getConsoleSender(), command);
        }
    }
    private void handleCustomItemsClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || event.getCurrentItem() == null) return;

        ItemStack clickedItem = event.getCurrentItem();

        if (!CustomItemsCommand.clickable(clickedItem)) {
            if (clickedItem.isSimilar(CustomItemsCommand.getBack())) {
                CustomItemsCommand.lastPage();
            } else if (clickedItem.isSimilar(CustomItemsCommand.getNext())) {
                CustomItemsCommand.nextPage();
            } else if (clickedItem.isSimilar(CustomItemsCommand.getDelete())) {
                CustomItemsCommand.removeItem(event.getSlot() + 9);
            }
            event.setCancelled(true);
        }
    }
    public static void dropItems(Inventory inventory, Player player) {
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && !sellGUIItem(itemStack, player)) {
                if (main.getConfig().getBoolean("drop-items-on-close")) {
                    player.getWorld().dropItem(player.getLocation(), itemStack);
                    inventory.remove(itemStack);
                }else{
                    if(player.getInventory().firstEmpty() != -1){
                        player.getInventory().addItem(itemStack);
                        inventory.remove(itemStack);
                    }else{
                        player.getWorld().dropItem(player.getLocation(), itemStack);
                        inventory.remove(itemStack);
                    }
                }
            }
        }
    }

    public static boolean sellGUIItem(ItemStack i, Player player) {
        return i != null && (i.isSimilar(SellGUI.getSellItem()) || i.isSimilar(SellGUI.getFiller()) || i.isSimilar(SellCommand.getSellGUI(player).getConfirmItem()));
    }

}
