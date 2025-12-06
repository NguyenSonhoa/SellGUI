package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final SellGUIMain plugin;

    public PlayerListener(SellGUIMain plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getAutosellManager().startAutosellTask(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getAutosellManager().stopAutosellTask(event.getPlayer());
    }
}
