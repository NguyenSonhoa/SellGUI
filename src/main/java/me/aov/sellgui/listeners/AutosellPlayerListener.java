package me.aov.sellgui.listeners;

import me.aov.sellgui.managers.AutosellManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AutosellPlayerListener implements Listener {

    private final AutosellManager autosellManager;

    public AutosellPlayerListener(AutosellManager autosellManager) {
        this.autosellManager = autosellManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        autosellManager.startAutosellTask(event.getPlayer());
    }
}
