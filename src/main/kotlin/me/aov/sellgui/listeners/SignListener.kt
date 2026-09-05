package me.aov.sellgui.listeners

import me.aov.sellgui.SellGUI
import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.commands.SellCommand
import net.md_5.bungee.api.ChatColor
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.player.PlayerInteractEvent

class SignListener(private val main: SellGUIMain) : Listener {
    @EventHandler
    fun createSign(event: SignChangeEvent) {
        if (!event.getLine(0).equals("[sellgui]", true) || !(event.player.isOp || event.player.hasPermission("sellgui.createsign"))) return
        val lines = main.langConfig.getStringList("sign-lines")
        for (index in 0 until minOf(4, lines.size)) event.setLine(index, ChatColor.translateAlternateColorCodes('&', lines[index]))
    }

    @EventHandler
    fun rightClickSign(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        if (event.action != Action.RIGHT_CLICK_BLOCK || block.state !is Sign) return
        val sign = block.state as Sign
        if (isSellGUISign(sign) && event.player.hasPermission("sellgui.usesign")) SellCommand.getSellGUIs().add(SellGUI(main, event.player, main.itemNBTManager))
    }

    private fun isSellGUISign(sign: Sign): Boolean {
        val lines = main.langConfig.getStringList("sign-lines")
        return lines.size >= 4 && (0 until 4).all { sign.getLine(it).equals(ChatColor.translateAlternateColorCodes('&', lines[it]), true) }
    }
}
