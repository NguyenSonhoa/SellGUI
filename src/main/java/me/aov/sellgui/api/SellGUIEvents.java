package me.aov.sellgui.api;

import me.aov.sellgui.SellGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
// soon
public class SellGUIEvents extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final SellGUI sellGUI;

    public SellGUIEvents(Player player, SellGUI sellGUI) {
        this.player = player;
        this.sellGUI = sellGUI;
    }

    public Player getPlayer() {
        return player;
    }

    public SellGUI getSellGUI() {
        return sellGUI;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

