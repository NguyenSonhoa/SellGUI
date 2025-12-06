package me.aov.sellgui.commands;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AutosellCommand implements CommandExecutor {

    private final SellGUIMain main;

    public AutosellCommand(SellGUIMain main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.color("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("sellgui.autosell")) {
            player.sendMessage(ColorUtils.color("&cYou do not have permission to use this command."));
            return true;
        }

        main.getGUIManager().openAutosellSettingsGUI(player);
        return true;
    }
}
