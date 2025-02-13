package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class UpdateWarning implements Listener {
    private SellGUIMain main;

    public UpdateWarning(SellGUIMain main) {
        this.main = main;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        if(event.getPlayer().isOp()){
            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[SellGUI] An update is available for SellGUI."));
        }
    }
}
