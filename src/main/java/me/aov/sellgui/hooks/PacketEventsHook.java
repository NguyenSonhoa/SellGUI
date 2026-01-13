package me.aov.sellgui.hooks;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.listeners.PacketEventsPacketListener;
import com.github.retrooper.packetevents.PacketEvents;

public class PacketEventsHook {
    public static void register(SellGUIMain main) {
        PacketEvents.getAPI().getEventManager().registerListener(new PacketEventsPacketListener(main));
    }
}
