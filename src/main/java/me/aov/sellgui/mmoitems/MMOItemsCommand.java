package me.aov.sellgui.mmoitems;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MMOItemsCommand implements CommandExecutor {
    private final MMOItemsPriceEditor editor;

    public MMOItemsCommand(MMOItemsPriceEditor editor) {
        this.editor = editor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            editor.openEditor(player);
            player.sendMessage(ChatColor.GREEN + "Opened the MMOItems Price Editor.");
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /sellgui.mmoitems");
        }
        return true;
    }
}
