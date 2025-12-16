package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.gui.AutosellSettingsGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AutosellSearchListener implements Listener {

    private static final Set<UUID> searchingPlayers = new HashSet<>();
    private final SellGUIMain plugin;

    public AutosellSearchListener(SellGUIMain plugin) {
        this.plugin = plugin;
    }

    public static void addSearchingPlayer(UUID uuid) {
        searchingPlayers.add(uuid);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player eventPlayer = event.getPlayer();
        if (searchingPlayers.contains(eventPlayer.getUniqueId())) {
            event.setCancelled(true);
            searchingPlayers.remove(eventPlayer.getUniqueId());

            String message = event.getMessage();

            new BukkitRunnable() {
                @Override
                public void run() {
                    AutosellSettingsGUI searchResultGUI = new AutosellSettingsGUI(plugin, eventPlayer, message);
                    eventPlayer.openInventory(searchResultGUI.getInventory());
                }
            }.runTask(plugin);
        }
    }
}
