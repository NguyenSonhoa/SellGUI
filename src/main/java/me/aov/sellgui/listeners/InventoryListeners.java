package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUI;
import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.commands.SellCommand;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.*;
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
        }
    }



    @EventHandler
    public void inventoryClick(InventoryClickEvent e) {
        String soundName = main.getLangConfig().getString("no-items-sound", "BLOCK_NOTE_BLOCK_BASS");
        float volume = (float) main.getLangConfig().getDouble("no-items-volume", 1.0);
        float pitch = (float) main.getLangConfig().getDouble("no-items-pitch", 1.0);
        Sound sound = Sound.valueOf(soundName);
        ItemStack cursorItem = e.getCursor();

        if (!(e.getWhoClicked() instanceof Player)) return;

        Player player = (Player) e.getWhoClicked();
        ItemStack currentItem = e.getCurrentItem();

        if (SellCommand.openSellGUI(player)) {
            SellGUI sellGUI = SellCommand.getSellGUI(player);
            ItemStack clickedItem = e.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) return;
            if (clickedItem.getItemMeta() != null &&
                    clickedItem.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(main, "custom-menu-item"), PersistentDataType.STRING)) {
                executeCommandFromItem(player, clickedItem);
                e.setCancelled(true);
                return;
            }
            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(main, "sellgui"), PersistentDataType.BYTE)) {
                e.setCancelled(true);
                return;
            }
            sellGUI.addSellItem();
            if (clickedItem.isSimilar(sellGUI.getConfirmItem())) {
                executeCommandFromConfig(player, "confirm-item-command");
                sellGUI.sellItems(sellGUI.getMenu());
                e.setResult(Event.Result.DENY);
                e.setCancelled(true);
            } else if (clickedItem.isSimilar(SellGUI.getSellItem())) {
                if (sellGUI.getTotal(sellGUI.getMenu()) <= 0) {
                    String title = main.getLangConfig().getString("no-items-title", "&cNo items to sell!");
                    String subtitle = main.getLangConfig().getString("no-items-subtitle", "");

                    e.getWhoClicked().getWorld().playSound(e.getWhoClicked().getLocation(), sound, volume, pitch);
                    player.closeInventory();
                    player.sendTitle(ChatColor.translateAlternateColorCodes('&', title), ChatColor.translateAlternateColorCodes('&', subtitle), 10, 70, 20);
                    return;
                }
                executeCommandFromConfig(player, "sell-item-command");
                sellGUI.makeConfirmItem();
                sellGUI.setConfirmItem();
                e.setResult(Event.Result.DENY);
                e.setCancelled(true);
            } else if (clickedItem.isSimilar(SellGUI.getFiller())) {
                e.setResult(Event.Result.DENY);
                e.setCancelled(true);
            }
            if (e.getInventory().getHolder() instanceof SellGUI && cursorItem != null && cursorItem.getType() != Material.AIR) {
                e.getWhoClicked().getWorld().playSound(e.getWhoClicked().getLocation(), Sound.BLOCK_BARREL_OPEN, 0.8F, 1.2F);
            }
        }
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message.replace("§", "&"));
    }

    private void executeCommandFromItem(Player player, ItemStack item) {
        String commands = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(main, "custom-menu-item"), PersistentDataType.STRING);
        if (commands == null) return;

        for (String command : commands.split(";")) {
            command = command.replace("%player%", player.getName());
            main.getServer().dispatchCommand(main.getServer().getConsoleSender(), command);
        }
    }
    private void executeCommandFromConfig(Player player, String commandKey) {
        String command = main.getConfig().getString(commandKey);
        if (command != null) {
            command = command.replace("%player%", player.getName());
            main.getServer().dispatchCommand(main.getServer().getConsoleSender(), command);
        }
    }

    public static void dropItems(Inventory inventory, Player player) {
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && !sellGUIItem(itemStack, player)) {
                if (itemStack.hasItemMeta() && itemStack.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(main, "custom-menu-item"), PersistentDataType.STRING)) {
                    continue;
                }

                if (main.getConfig().getBoolean("drop-items-on-close")) {
                    player.getWorld().dropItem(player.getLocation(), itemStack);
                } else {
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(itemStack);
                        inventory.remove(itemStack);
                    } else {
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