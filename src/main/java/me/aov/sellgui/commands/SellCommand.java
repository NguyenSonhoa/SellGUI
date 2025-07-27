package me.aov.sellgui.commands;

import me.aov.sellgui.SellGUI;
import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.utils.ItemIdentifier;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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

        // /sellgui setprice [price]
        if (args.length >= 1 && args[0].equalsIgnoreCase("setprice")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(color("&cThis command can only be used by players!"));
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("sellgui.setprice")) {
                player.sendMessage(color("&cYou don't have permission to use this command!"));
                return true;
            }

            if (args.length == 1) {
                // /sellgui setprice - Open price setter GUI
                main.getPriceSetterCommand().onCommand(sender, command, "sellguiprice", new String[0]);
            } else if (args.length == 2) {
                // /sellgui setprice <price> - Set price for item in hand
                return handleSetPriceInHand(player, args[1]);
            } else {
                player.sendMessage(color("&cUsage: /sellgui setprice [price]"));
            }
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

    private boolean handleSetPriceInHand(Player player, String priceString) {
        // Check if player has item in hand
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            player.sendMessage(color("&cYou must be holding an item to set its price!"));
            return true;
        }

        // Parse price
        double price;
        try {
            price = Double.parseDouble(priceString);
        } catch (NumberFormatException e) {
            player.sendMessage(color("&cInvalid price! Please enter a valid number."));
            return true;
        }

        if (price < 0) {
            player.sendMessage(color("&cPrice cannot be negative!"));
            return true;
        }

        // Set the price using PriceManager
        PriceManager priceManager = new PriceManager(main);
        boolean success = priceManager.setItemPrice(itemInHand, price);

        if (success) {
            String itemName = ItemIdentifier.getItemDisplayName(itemInHand);
            String itemType = ItemIdentifier.getItemType(itemInHand).name();

            if (price == 0) {
                player.sendMessage(color("&aSuccessfully removed price for &f" + itemName + " &7(" + itemType + ")"));
            } else {
                player.sendMessage(color("&aSuccessfully set price for &f" + itemName + " &7(" + itemType + ") &ato &e$" + String.format("%.2f", price)));
            }
        } else {
            player.sendMessage(color("&cFailed to set price! Check console for errors."));
        }

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