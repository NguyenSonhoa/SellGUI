package me.aov.sellgui.commands

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.utils.ColorUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class AutosellCommand(private val main: SellGUIMain) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(ColorUtils.color("&cOnly players can use this command.").orEmpty())
            return true
        }
        if (!player.hasPermission("sellgui.autosell")) {
            player.sendMessage(ColorUtils.color("&cYou do not have permission to use this command.").orEmpty())
            return true
        }
        main.getGUIManager().openAutosellSettingsGUI(player)
        return true
    }
}
