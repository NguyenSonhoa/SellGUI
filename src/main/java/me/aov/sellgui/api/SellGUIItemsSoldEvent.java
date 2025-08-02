package me.aov.sellgui.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

// soon lol
public class SellGUIItemsSoldEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final double totalSoldValue;

    public SellGUIItemsSoldEvent(Player player, double totalSoldValue) {
        this.player = player;
        this.totalSoldValue = totalSoldValue;
    }

    public Player getPlayer() {
        return player;
    }

    public double getTotalSoldValue() {
        return totalSoldValue;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
