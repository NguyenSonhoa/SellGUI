package me.aov.sellgui.commands;

import me.aov.sellgui.SellGUI;
import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.gui.PriceEvaluationGUI;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.utils.ColorUtils;
import me.aov.sellgui.utils.ItemIdentifier;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SellCommand implements CommandExecutor, TabCompleter {
    private final SellGUIMain main;
    private static ArrayList<SellGUI> sellGUIS;
    public SellCommand(SellGUIMain main) {
        this.main = main;
        sellGUIS = new ArrayList<>();
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "reload":
                    if (!sender.hasPermission("sellgui.reload")) {
                        sender.sendMessage(ColorUtils.color("&cYou do not have permission."));
                        return true;
                    }
                    main.reload();
                    sender.sendMessage(ColorUtils.color("&aSellGUI configs and GUIs have been reloaded."));
                    return true;

                case "evaluate":
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ColorUtils.color("&cOnly players can use this command."));
                        return true;
                    }
                    if (!sender.hasPermission("sellgui.evaluate")) {
                        sender.sendMessage(ColorUtils.color("&cYou do not have permission."));
                        return true;
                    }
                    main.getGUIManager().openPriceEvaluationGUI((Player) sender);
                    return true;
                case "setprice":
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ColorUtils.color("&cOnly players can use this command."));
                        return true;
                    }
                    if (!sender.hasPermission("sellgui.setprice")) {
                        sender.sendMessage(ColorUtils.color("&cYou do not have permission."));
                        return true;
                    }
                    if (args.length != 2) {
                        sender.sendMessage(ColorUtils.color("&cUsage: /sellgui setprice <amount>"));
                        return true;
                    } else {

                        return handleSetPriceInHand((Player) sender, args[1]);
                    }
                case "setrange":
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ColorUtils.color("&cOnly players can use this command."));
                        return true;}
                    if (!sender.hasPermission("sellgui.setrange")) {
                        sender.sendMessage(ColorUtils.color("&cYou do not have permission."));
                        return true;}
                    if (args.length != 3) {
                        sender.sendMessage(ColorUtils.color("&cUsage: /sellgui setrange <min> <max>"));
                        return true;}
                    return handleSetRandomPrice((Player) sender, args[1], args[2]);

                case "autosell":

                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ColorUtils.color("&cOnly players can use this command."));
                        return true;
                    }
                    if (!sender.hasPermission("sellgui.autosell")) {
                        sender.sendMessage(ColorUtils.color("&cYou do not have permission."));
                        return true;
                    }
                    main.getGUIManager().openAutosellSettingsGUI((Player) sender);

                    return true;

                case "help":
                    return handleHelpCommand(sender);

                default:

                    break;
            }
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.color("&cThis command can only be executed by a player."));
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            if (player.hasPermission("sellgui.use")) {
                sellGUIS.add(new SellGUI(this.main, player, this.main.getItemNBTManager()));} else {player.sendMessage(ColorUtils.color("&cYou do not have permission to use this command."));}
            return true;}
        if (args.length == 1) {
            if (player.hasPermission("sellgui.others")) {
                Player target = main.getServer().getPlayer(args[0]);
                if (target != null) {
                    sellGUIS.add(new SellGUI(this.main, target, this.main.getItemNBTManager()));
                    player.sendMessage(ColorUtils.color("&aSuccessfully opened SellGUI for " + target.getName() + "."));
                } else {
                    player.sendMessage(ColorUtils.color("&cPlayer '" + args[0] + "' not found or is not online."));
                }
            } else {
                player.sendMessage(ColorUtils.color("&cYou do not have permission to open SellGUI for other players."));
            }
            return true;
        }

        sender.sendMessage(ColorUtils.color("&cInvalid command usage. Try: /" + label + " help"));
        return true;
    }

    private boolean handleSetPriceInHand(Player player, String priceString) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR) {
            player.sendMessage(ColorUtils.color("&cYou must be holding an item to set its price!"));
            return true;}

        double price;
        try {
            price = Double.parseDouble(priceString);
        } catch (NumberFormatException e) {
            player.sendMessage(ColorUtils.color("&cInvalid price! Please enter a valid number."));
            return true;}

        if (price < 0) {
            player.sendMessage(ColorUtils.color("&cPrice cannot be negative!"));
            return true;}

        PriceManager priceManager = new PriceManager(main);
        boolean success = priceManager.setItemPrice(itemInHand, price);
        if (success) {
            String itemName = ItemIdentifier.getItemDisplayName(itemInHand);
            String itemType = ItemIdentifier.getItemType(itemInHand).name();
            if (price == 0) {
                player.sendMessage(ColorUtils.color("&aSuccessfully removed price for &f" + itemName + " &7(" + itemType + ")"));} else {
                player.sendMessage(ColorUtils.color("&aSuccessfully set price for &f" + itemName + " &7(" + itemType + ") &ato &e$" + String.format("%.2f", price)));}} else {
            player.sendMessage(ColorUtils.color("&cFailed to set price! Check console for errors."));}
        return true;
    }

    private boolean handleSetRandomPrice(Player player, String minString, String maxString) {
        try {
            double minPrice = Double.parseDouble(minString);
            double maxPrice = Double.parseDouble(maxString);

            if (minPrice < 0 || maxPrice < 0) {
                player.sendMessage(ColorUtils.color("&cPrices cannot be negative!"));
                return true;
            }
            if (minPrice >= maxPrice) {
                player.sendMessage(ColorUtils.color("&cMinimum price must be less than maximum price!"));
                return true;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) {
                player.sendMessage(ColorUtils.color("&cYou must be holding an item to set its price range."));
                return true;
            }

            String itemIdentifier = ItemIdentifier.getItemIdentifier(item);
            if (itemIdentifier == null) {
                player.sendMessage(ColorUtils.color("&cCould not identify the item you are holding."));
                return true;
            }

            String key = itemIdentifier;

            FileConfiguration randomPricesConfig = main.getConfigManager().getRandomPricesConfig();

            randomPricesConfig.set(key + ".min_price", minPrice);
            randomPricesConfig.set(key + ".max_price", maxPrice);
            randomPricesConfig.set(key + ".last_updated", System.currentTimeMillis());
            randomPricesConfig.set(key + ".set_by", player.getName());
            randomPricesConfig.set(key + ".item_type", ItemIdentifier.getItemType(item).name());

            main.getConfigManager().saveConfig("random-prices");

            player.sendMessage(ColorUtils.color("&aRandom price range for &e" + ItemIdentifier.getItemDisplayName(item) + " &aset to &e$" + String.format("%.2f", minPrice) + " &ato &e$" + String.format("%.2f", maxPrice)));

            PriceEvaluationGUI gui = main.getGUIManager().getActivePriceEvaluationGUI(player);
            if (gui != null) {
                gui.setRandomPrice(minPrice, maxPrice);
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ColorUtils.color("&cInvalid price format! Use numbers only."));
        }
        return true;
    }

    private boolean handleHelpCommand(CommandSender sender) {
        sender.sendMessage(ColorUtils.color("&6&l=== SellGUI Help ===="));
        sender.sendMessage("");
        sender.sendMessage(ColorUtils.color("&e/sellgui &7- Open the sell GUI"));
        sender.sendMessage(ColorUtils.color("&e/sellgui help &7- Show this help message"));

        if (sender.hasPermission("sellgui.evaluate")) {
            sender.sendMessage(ColorUtils.color("&e/sellgui evaluate &7- Open the Price Evaluation GUI."));
        }
        if (sender.hasPermission("sellgui.setprice")) {
            sender.sendMessage(ColorUtils.color("&e/sellgui setprice <amount> &7- Set fixed price in Evaluation GUI."));
        }
        if (sender.hasPermission("sellgui.setrange")) {
            sender.sendMessage(ColorUtils.color("&e/sellgui setrange <min> <max> &7- Set random range in Evaluation GUI."));
        }
        if (sender.hasPermission("sellgui.autosell")) {

            sender.sendMessage(ColorUtils.color("&e/sellgui autosell &7- Open the autosell settings menu."));
        }
        if (sender.hasPermission("sellgui.reload")) {
            sender.sendMessage(ColorUtils.color("&c/sellgui reload &7- Reload plugin configuration."));
        }
        sender.sendMessage(ColorUtils.color("&6&l=================="));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("sellgui.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("sellgui.evaluate")) {
                completions.add("evaluate");
            }
            if (sender.hasPermission("sellgui.setprice")) {
                completions.add("setprice");
            }
            if (sender.hasPermission("sellgui.setrange")) {
                completions.add("setrange");
            }
            if (sender.hasPermission("sellgui.autosell")) {
                completions.add("autosell");
            }
            completions.add("help");

            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("setprice")) {

                return Arrays.asList("0", "10", "100").stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("setrange")) {

                return Arrays.asList("0", "10", "100").stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setrange")) {

                return Arrays.asList("10", "100", "1000").stream()
                        .filter(s -> s.startsWith(args[2]))
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

    public static ArrayList<SellGUI> getSellGUIs() {
        return sellGUIS;
    }
    public static boolean isSellGUI(Inventory inventory) {
        for (SellGUI sellGUI : sellGUIS) {
            if (sellGUI.getMenu().equals(inventory))
                return true;
        }
        return false;
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

