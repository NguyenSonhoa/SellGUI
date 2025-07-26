package me.aov.sellgui.commands;

import me.aov.sellgui.SellGUI;
import me.aov.sellgui.SellGUIMain;


import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class SellCommand implements CommandExecutor {
    private final SellGUIMain main;
    private static ArrayList<SellGUI> sellGUIS;

    public SellCommand(SellGUIMain main) {
        this.main = main;
        sellGUIS = new ArrayList<>();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // /sellgui reload
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("sellgui.reload")) {
                main.reload();
                sender.sendMessage(color("&aConfigs reloaded successfully."));
            } else {
                sender.sendMessage(color("&cYou do not have permission to use this command."));
            }
            return true;
        }

        // /sellgui setprice
        if (args.length == 1 && args[0].equalsIgnoreCase("setprice")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(color("&cThis command can only be used by players!"));
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("sellgui.setprice")) {
                player.sendMessage(color("&cYou don't have permission to use this command!"));
                return true;
            }

            // Open price setter GUI
            main.getPriceSetterCommand().onCommand(sender, command, "sellguiprice", new String[0]);
            return true;
        }

        // /sellgui & /sellgui <player>
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be executed by a player.");
            return true;
        }
        Player player = (Player) sender;

        // /sellgui
        if (args.length == 0) {
            if (player.hasPermission("sellgui.use")) {
                // Nếu sellGUIS không phải static, thì không cần SellCommand.getSellGUIs()
                // mà là this.sellGUIS.add(...) hoặc một phương thức quản lý GUI khác
                sellGUIS.add(new SellGUI(this.main, player));
            } else {
                player.sendMessage(color("&cYou do not have permission to use this command."));
            }
            return true;
        }

        // Xử lý /sellgui <tên_người_chơi>
        // args.length == 1 nhưng không phải "reload" hay "mmoitems" đã được xử lý ở trên
        if (args.length == 1) {
            if (player.hasPermission("sellgui.others")) {
                Player target = main.getServer().getPlayer(args[0]);
                if (target != null) {
                    sellGUIS.add(new SellGUI(this.main, target));
                    player.sendMessage(color("&aSuccessfully opened SellGUI for " + target.getName() + "."));
                } else {
                    player.sendMessage(color("&cPlayer '" + args[0] + "' not found or is not online."));
                }
            } else {
                player.sendMessage(color("&cYou do not have permission to open SellGUI for other players."));
            }
            return true;
        }

        // Nếu không có đối số nào khớp, hiển thị thông báo sử dụng lệnh
        sender.sendMessage(color("&cInvalid command usage. Try: /" + label + " [reload|setprice|<playername>]"));
        return true;
    }

    // Phương thức color và các phương thức static khác nếu có
    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    // Các phương thức static liên quan đến sellGUIS nên được xem xét lại
    // Nếu sellGUIS là một instance field, các phương thức này cũng nên là instance methods
    // hoặc được quản lý bởi một lớp service riêng biệt.
    public static ArrayList<SellGUI> getSellGUIs() {
        return sellGUIS;
    }

    public static boolean isSellGUI(Inventory inventory) {
        for (SellGUI sellGUI : sellGUIS) {
            if (sellGUI.getMenu().equals(inventory)) // Sử dụng .equals() cho Inventory
                return true;
        }
        return false;
    }

    public static SellGUI getSellGUI(Inventory inventory) {
        for (SellGUI sellGUI : sellGUIS) {
            if (sellGUI.getMenu().equals(inventory))
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