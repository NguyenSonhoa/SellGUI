package me.aov.sellgui.commands;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SellGUITabCompleter implements TabCompleter {

    private static final List<String> MAIN_COMMANDS = Arrays.asList(
            "reload", "setprice", "setrange", "evaluate", "placeholder", "help", "version", "debug"
    );

    private static final List<String> COMMON_PRICES = Arrays.asList(
            "0.1", "0.5", "1.0", "2.5", "5.0", "10.0", "25.0", "50.0", "100.0", "250.0", "500.0", "1000.0"
    );

    private static final List<String> PRICE_COMMANDS = Arrays.asList(
            "remove", "delete", "clear", "0"
    );

    private static final List<String> PLACEHOLDER_COMMANDS = Arrays.asList(
            "test", "info", "status", "help"
    );

    private static final List<String> DEBUG_COMMANDS = Arrays.asList(
            "info", "config", "economy", "placeholders", "sounds"
    );

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("sellgui")) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {

            return getFirstArgumentCompletions(sender, args[0]);

        } else if (args.length == 2) {

            return getSecondArgumentCompletions(sender, args[0], args[1]);

        } else if (args.length == 3) {

            return getThirdArgumentCompletions(sender, args[0], args[1], args[2]);

        } else if (args.length >= 4) {

            return getFurtherArgumentCompletions(sender, args);
        }

        return completions;
    }

    private List<String> getFirstArgumentCompletions(CommandSender sender, String input) {
        List<String> completions = new ArrayList<>();
        List<String> possibleArgs = new ArrayList<>();

        if (sender.hasPermission("sellgui.reload")) {
            possibleArgs.add("reload");
        }
        if (sender.hasPermission("sellgui.setprice")) {
            possibleArgs.addAll(Arrays.asList("setprice", "setrange"));
        }
        if (sender.hasPermission("sellgui.evaluate")) {
            possibleArgs.add("evaluate");
        }

        if (sender.hasPermission("sellgui.others")) {
            Bukkit.getOnlinePlayers().forEach(player -> possibleArgs.add(player.getName()));
        }

        StringUtil.copyPartialMatches(input, possibleArgs, completions);
        Collections.sort(completions);
        return completions;
    }
    private List<String> getSecondArgumentCompletions(CommandSender sender, String firstArg, String input) {
        List<String> completions = new ArrayList<>();

        switch (firstArg.toLowerCase()) {
            case "setprice":
                if (sender.hasPermission("sellgui.setprice")) {
                    List<String> priceOptions = new ArrayList<>(COMMON_PRICES);
                    priceOptions.addAll(PRICE_COMMANDS);

                    if (sender instanceof Player) {
                        addContextualPrices(priceOptions, (Player) sender);
                    }

                    StringUtil.copyPartialMatches(input, priceOptions, completions);
                }
                break;

            case "setrange":
                if (sender.hasPermission("sellgui.setprice")) {
                    List<String> minPrices = Arrays.asList(
                            "0.1", "0.5", "1.0", "5.0", "10.0", "25.0", "50.0", "100.0"
                    );
                    StringUtil.copyPartialMatches(input, minPrices, completions);
                }
                break;

            case "debug":
                if (sender.hasPermission("sellgui.admin")) {
                    StringUtil.copyPartialMatches(input, DEBUG_COMMANDS, completions);
                }
                break;
        }

        Collections.sort(completions);
        return completions;
    }

    private List<String> getThirdArgumentCompletions(CommandSender sender, String firstArg, String secondArg, String input) {
        List<String> completions = new ArrayList<>();

        if (firstArg.equalsIgnoreCase("setrange") && sender.hasPermission("sellgui.setprice")) {

            try {
                double minPrice = Double.parseDouble(secondArg);
                List<String> maxPrices = Arrays.asList(
                        String.valueOf(minPrice * 1.5),
                        String.valueOf(minPrice * 2.0),
                        String.valueOf(minPrice * 3.0),
                        String.valueOf(minPrice * 5.0),
                        String.valueOf(minPrice * 10.0)
                );
                StringUtil.copyPartialMatches(input, maxPrices, completions);
            } catch (NumberFormatException e) {

                List<String> maxPrices = Arrays.asList(
                        "2.0", "5.0", "10.0", "25.0", "50.0", "100.0", "500.0", "1000.0"
                );
                StringUtil.copyPartialMatches(input, maxPrices, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }

    private List<String> getFurtherArgumentCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args[0].equalsIgnoreCase("placeholder") && sender.hasPermission("sellgui.admin")) {

            List<String> placeholders = Arrays.asList(
                    "%player%", "%vault_eco_balance%", "%player_world%", "%time%", "%date%",
                    "%server_online%", "%sellgui_version%", "%player_level%"
            );

            String input = args[args.length - 1];
            StringUtil.copyPartialMatches(input, placeholders, completions);
        }

        Collections.sort(completions);
        return completions;
    }
    private void addContextualPrices(List<String> prices, Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            return;
        }

        Material material = heldItem.getType();
        String name = material.name();

        if (name.contains("_ORE") || name.contains("DIAMOND") || name.contains("EMERALD")) {
            prices.addAll(Arrays.asList("2.0", "5.0", "10.0", "20.0"));
        } else if (material.isEdible()) {
            prices.addAll(Arrays.asList("0.5", "1.0", "2.0", "3.0"));
        } else if (name.contains("_SWORD") || name.contains("_AXE") || name.contains("_PICKAXE")) {
            prices.addAll(Arrays.asList("15.0", "30.0", "75.0", "150.0"));
        } else if (name.contains("_HELMET") || name.contains("_CHESTPLATE") || name.contains("_LEGGINGS") || name.contains("_BOOTS")) {
            prices.addAll(Arrays.asList("10.0", "25.0", "50.0", "100.0"));
        } else if (name.contains("NETHERITE") || name.contains("ELYTRA")) {
            prices.addAll(Arrays.asList("100.0", "250.0", "500.0", "1000.0"));
        }
    }
}