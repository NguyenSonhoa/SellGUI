package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.gui.PriceEvaluationGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class PriceEvaluationListener implements Listener {

    private final SellGUIMain main;

    public PriceEvaluationListener(SellGUIMain main) {
        this.main = main;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        PriceEvaluationGUI gui = main.getGUIManager().getActivePriceEvaluationGUI(player);

        if (gui == null || !event.getInventory().equals(gui.getInventory())) {
            return;
        }

        if (gui.isLocked()) {
            if (event.getRawSlot() < gui.getInventory().getSize() || event.isShiftClick()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cEvaluation in progress! Please wait..."));
            }
            return;
        }

        int slot = event.getRawSlot();

        if (slot >= gui.getInventory().getSize()) {
            return;
        }

        if (slot == gui.getItemSlot()) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        NamespacedKey key = new NamespacedKey(main, "sellgui-nbt-id");

        if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            String nbtId = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            handleButtonClick(player, gui, nbtId);
        }
    }

    private void handleButtonClick(Player player, PriceEvaluationGUI gui, String nbtId) {
        if (nbtId == null) return;

        switch (nbtId) {
            case "evaluate_button":
                gui.startEvaluation();
                break;
            case "cancel_button":
                player.closeInventory();
                break;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        PriceEvaluationGUI gui = main.getGUIManager().getActivePriceEvaluationGUI(player);

        if (gui == null || !event.getInventory().equals(gui.getInventory())) {
            return;
        }

        if (gui.isLocked()) {
            event.setCancelled(true);
            return;
        }

        boolean allowStack = main.getConfigManager().getConfig("config").getBoolean("general.allow-player-evaluation-stack", true);

        if (event.getRawSlots().contains(gui.getItemSlot())) {
            event.setCancelled(true);

            ItemStack currentItemInSlot = gui.getInventory().getItem(gui.getItemSlot());
            if (currentItemInSlot != null && currentItemInSlot.getType() != Material.AIR) {
                return;
            }
            ItemStack draggedStack = event.getOldCursor();
            if (draggedStack != null && draggedStack.getType() != Material.AIR) {
                if (!allowStack && draggedStack.getAmount() > 1) {
                    String errorMessage = main.getConfigManager().getConfig("messages").getString("price-evaluation.drag-stack-not-allowed", "&cYou can't Evaluate more than 1 amount at a time.");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', errorMessage));
                    return;
                }

                gui.getInventory().setItem(gui.getItemSlot(), draggedStack.clone());
                player.setItemOnCursor(null);
            }
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();

        PriceEvaluationGUI gui = main.getGUIManager().getActivePriceEvaluationGUI(player);

        if (gui != null && event.getInventory().equals(gui.getInventory())) {
            gui.cleanup();
            gui.returnItemToPlayer();
            main.getGUIManager().removePlayer(player);
        }
    }
}