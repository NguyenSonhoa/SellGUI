package me.aov.sellgui.handlers;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

public class InventoryHandler {

    public static InventoryHolder getTopInventoryHolder(InventoryClickEvent event) {
        try {
            InventoryView view = event.getView();
            return view.getTopInventory().getHolder();
        } catch (Exception e) {
            return null;
        }
    }

    public static InventoryHolder getTopInventoryHolder(InventoryDragEvent event) {
        try {
            InventoryView view = event.getView();
            return view.getTopInventory().getHolder();
        } catch (Exception e) {
            return null;
        }
    }

    public static Inventory getTopInventory(InventoryClickEvent event) {
        try {
            InventoryView view = event.getView();
            return view.getTopInventory();
        } catch (Exception e) {
            return null;
        }
    }

    public static int getTopInventorySize(InventoryClickEvent event) {
        try {
            InventoryView view = event.getView();
            return view.getTopInventory().getSize();
        } catch (Exception e) {
            return 0;
        }
    }

    public static int getTopInventorySize(InventoryDragEvent event) {
        try {
            InventoryView view = event.getView();
            return view.getTopInventory().getSize();
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean isTopInventoryClick(InventoryClickEvent event) {
        try {
            Inventory clicked = event.getClickedInventory();
            Inventory top = event.getView().getTopInventory();
            return clicked != null && clicked.equals(top);
        } catch (Exception e) {
            return false;
        }
    }

    public static int getRawSlot(InventoryClickEvent event) {
        try {
            return event.getRawSlot();
        } catch (Exception e) {
            return -1;
        }
    }

    public static int getSlot(InventoryClickEvent event) {
        try {
            return event.getSlot();
        } catch (Exception e) {
            return -1;
        }
    }

    public static void closeInventory(Player player) {
        try {
            player.closeInventory();
        } catch (Exception e) {

        }
    }

    public static void openInventory(Player player, Inventory inventory) {
        try {
            player.openInventory(inventory);
        } catch (Exception e) {

        }
    }
}