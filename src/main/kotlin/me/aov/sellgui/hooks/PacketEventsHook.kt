package me.aov.sellgui.hooks

import com.github.retrooper.packetevents.PacketEvents
import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.listeners.PacketEventsPacketListener

object PacketEventsHook {
    @JvmStatic
    fun register(main: SellGUIMain) {
        PacketEvents.getAPI().eventManager.registerListener(PacketEventsPacketListener(main))
    }
}
