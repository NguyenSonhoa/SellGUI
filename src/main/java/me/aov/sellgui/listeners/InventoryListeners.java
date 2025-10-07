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

            dropItems(event.getInventory(), player);

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

        SellGUI sellGUI = SellCommand.getSellGUI(player);
        if (sellGUI == null || !e.getView().getTopInventory().equals(sellGUI.getMenu())) {
            return;
        }

        NamespacedKey guiKey = new NamespacedKey(main, "sellgui");
        NamespacedKey actionKey = new NamespacedKey(main, "guiAction");

        if (currentItem != null && currentItem.hasItemMeta() &&
                currentItem.getItemMeta().getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE)) {

            e.setCancelled(true);

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
                    SoundHandler.playUIClick(player);
                    sellGUI.makeConfirmItem();
                    sellGUI.setConfirmItem();
                    break;
            }
        }

        else if (currentItem != null && isCustomMenuItem(currentItem)) {
            e.setCancelled(true);
            handleCustomMenuItemClick(player, currentItem);
        }

        else if (clickedInventory != null && clickedInventory.equals(sellGUI.getMenu())) {

            if (isGUIControlItem(currentItem, sellGUI)) {
                e.setCancelled(true);
                return;
            }

            if (e.isShiftClick() && wouldGoToReservedSlot(e.getSlot(), sellGUI)) {
                e.setCancelled(true);
                return;
            }

            if (e.getCursor() != null && e.getCursor().getType() != Material.AIR &&
                    (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR)) {
                SoundHandler.playConfigSound(player, "sounds.items.place");
            }

            if (!e.isCancelled()) {
                SoundHandler.playUIClick(player);
            }

            Bukkit.getScheduler().runTaskLater(main, () -> {
                if (sellGUI.getMenu() != null && player.isOnline() && SellCommand.getSellGUI(player) != null) {
                    sellGUI.updateButtonState();
                }
            }, 1L);
        }

        else if (clickedInventory != null && clickedInventory.equals(player.getInventory())) {

            if (e.isShiftClick() && currentItem != null && currentItem.getType() != Material.AIR) {
                SoundHandler.playConfigSound(player, "sounds.items.place");
            }

            Bukkit.getScheduler().runTaskLater(main, () -> {
                if (sellGUI.getMenu() != null && player.isOnline() && SellCommand.getSellGUI(player) != null) {
                    sellGUI.updateButtonState();
                }
            }, 1L);
        }
    }

    private boolean isGUIControlItem(ItemStack item, SellGUI sellGUI) {
        if (item == null) return false;

        NamespacedKey guiKey = new NamespacedKey(main, "sellgui");
        return item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE);
    }

    private boolean wouldGoToReservedSlot(int slot, SellGUI sellGUI) {

        if (main.getConfigManager() != null && main.getConfigManager().getGUIConfig() != null) {
            var guiConfig = main.getConfigManager().getGUIConfig();
            var fillerSlots = guiConfig.getIntegerList("sell_gui.positions.filler_slots");
            int sellButtonSlot = guiConfig.getInt("sell_gui.positions.sell-button", 49);
            int confirmButtonSlot = guiConfig.getInt("sell_gui.positions.confirm_button", 53);

            return fillerSlots.contains(slot) || slot == sellButtonSlot || slot == confirmButtonSlot;
        }

        return slot < 9 || slot > 44 || slot % 9 == 0 || slot % 9 == 8;
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

            NamespacedKey guiKey = new NamespacedKey(main, "sellgui");
            if (itemStack.hasItemMeta() &&
                    itemStack.getItemMeta().getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE)) {
                continue;
            }

            if (isCustomMenuItem(itemStack)) {
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

    public static boolean sellGUIItem(ItemStack item, Player player) {
        if (item == null || !item.hasItemMeta()) return false;

        SellGUIMain instance = SellGUIMain.getInstance();
        if (instance == null) return false;

        NamespacedKey guiKey = new NamespacedKey(instance, "sellgui");
        return item.getItemMeta().getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE);
    }
}