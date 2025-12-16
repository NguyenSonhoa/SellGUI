package me.aov.sellgui.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.gui.PriceEvaluationGUI;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerLeaveListener implements Listener {

    private final SellGUIMain plugin;
    private static final Map<UUID, ItemStack> pendingItems = new HashMap<>();

    public PlayerLeaveListener(SellGUIMain plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        ItemStack pendingItem = pendingItems.remove(playerId);
        if (pendingItem != null) {

            if (event.getPlayer().getInventory().firstEmpty() != -1) {
                event.getPlayer().getInventory().addItem(pendingItem);
            } else {

                event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), pendingItem);
            }

            plugin.getLogger().info("Returned evaluation item to " + event.getPlayer().getName() + " on quit");
        }
    }

    public static void storePendingItem(UUID playerId, ItemStack item) {
        if (item != null) {
            pendingItems.put(playerId, item.clone());
        }
    }

    public static void removePendingItem(UUID playerId) {
        pendingItems.remove(playerId);
    }

    public static boolean hasPendingItem(UUID playerId) {
        return pendingItems.containsKey(playerId);
    }
}