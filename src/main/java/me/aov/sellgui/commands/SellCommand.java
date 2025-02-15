package me.aov.sellgui.commands;

import me.aov.sellgui.SellGUI;
import me.aov.sellgui.SellGUIMain;

import java.util.ArrayList;
import java.util.Objects;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public class SellCommand implements CommandExecutor {
    private final SellGUIMain main;

    private static ArrayList<SellGUI> sellGUIS;

    public SellCommand(SellGUIMain main) {
        this.main = main;
        sellGUIS = new ArrayList<>();
    }

    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, String s, String[] strings) {
        // Xử lý lệnh "reload"
        if (strings.length == 1 && strings[0].equalsIgnoreCase("reload")) {
            if (commandSender.hasPermission("sellgui.reload")) {
                main.reload();
                main.reloadMMOItemsConfig();  // ✅ Reloads `mmoitems.yml`
                main.getMMOItemsPriceEditor().loadPrices();
                commandSender.sendMessage(color("&7Configs reloaded."));
            } else {
                commandSender.sendMessage(color("&8No Permission"));
            }
            return true;
        }

        //
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("Sorry, Player-only Command.");
            return true;
        }

        Player player = (Player) commandSender;

        //
        if (strings.length == 0 && player.hasPermission("sellgui.use")) {
            sellGUIS.add(new SellGUI(this.main, player));
            return true;
        }

        //
        if (strings.length == 1 && commandSender.hasPermission("sellgui.use")) {
            if (ifPlayer(strings[0])) {
                Player target = main.getServer().getPlayer(strings[0]);
                if (target != null) {
                    sellGUIS.add(new SellGUI(this.main, target));
                    commandSender.sendMessage(color("Opened SellGUI for player: " + target.getName()));
                } else {
                    commandSender.sendMessage(color("&cPlayer not found: " + strings[0]));
                }
            } else {
                commandSender.sendMessage(color("&cInvalid player name."));
            }
            return true;
        }
        commandSender.sendMessage(color("&8No Permission"));
        return true;
    }

    private void reloadMMOItemsConfig() {
    }


    public ArrayList<SellGUI> getSellGUIS() {
        return sellGUIS;
    }

    public boolean ifPlayer(String s) {
        for (Player p : this.main.getServer().getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(s))
                return true;
        }
        return false;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static ArrayList<SellGUI> getSellGUIs() {
        return sellGUIS;
    }

    public static boolean isSellGUI(Inventory inventory) {
        for (SellGUI sellGUI : sellGUIS) {
            if (inventory.equals(sellGUI.getMenu()))
                return true;
        }
        return false;
    }

    public static SellGUI getSellGUI(Inventory inventory) {
        for (SellGUI sellGUI : sellGUIS) {
            if (inventory.equals(sellGUI.getMenu()))
                return sellGUI;
        }
        return null;
    }

    public static SellGUI getSellGUI(Player player) {
        for (SellGUI sellGUI : sellGUIS) {
            if (sellGUI.getPlayer().equals(player))
                return sellGUI;
        }
        return null;
    }

    public static boolean openSellGUI(Player player) {
        return sellGUIS.stream().anyMatch(sellGUI -> sellGUI.getPlayer().equals(player));
    }
}
