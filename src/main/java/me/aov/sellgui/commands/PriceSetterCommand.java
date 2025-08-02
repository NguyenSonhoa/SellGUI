package me.aov.sellgui.commands;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.gui.PriceSetterGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PriceSetterCommand implements CommandExecutor, TabCompleter {

    private final SellGUIMain main;
    private static final Map<UUID, PriceSetterGUI> openGUIs = new HashMap<>();

    public PriceSetterCommand(SellGUIMain main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(color(main.getLangConfig().getString("price-setter-players-only", "&cThis command can only be used by players!")));
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("sellguiprice")) {
            if (args.length == 0) {

                if (!player.hasPermission("sellgui.setprice")) {
                    player.sendMessage(color(main.getLangConfig().getString("price-setter-no-permission", "&cYou don't have permission to use this command!")));
                    return true;
                }

                PriceSetterGUI gui = new PriceSetterGUI(main, player);
                openGUIs.put(player.getUniqueId(), gui);
                return true;
            } else if (args.length == 1) {

                return handlePriceSet(player, args[0]);
            } else {
                player.sendMessage(color("&cUsage: /sellguiprice [price]"));
                return true;
            }
        }

        return false;
    }

    private boolean handlePriceSet(Player player, String priceString) {

        PriceSetterGUI gui = openGUIs.get(player.getUniqueId());
        if (gui == null) {

            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (!(holder instanceof PriceSetterGUI)) {
                player.sendMessage(color("&cYou need to have the Price Setter GUI open to use this command!"));
                return true;
            }
            gui = (PriceSetterGUI) holder;
        }

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

        boolean success = gui.savePrice(price);
        if (success) {
            player.sendMessage(color("&aPrice set successfully! Click the Save button to confirm."));
        }

        return true;
    }

    public static PriceSetterGUI getPriceSetterGUI(Player player) {
        return openGUIs.get(player.getUniqueId());
    }

    public static void removePriceSetterGUI(Player player) {
        openGUIs.remove(player.getUniqueId());
    }

    public static boolean hasPriceSetterGUI(Player player) {
        return openGUIs.containsKey(player.getUniqueId());
    }

    public static Map<UUID, PriceSetterGUI> getOpenGUIs() {
        return openGUIs;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {

            String input = args[0].toLowerCase();

            List<String> priceSuggestions = Arrays.asList(
                    "0.1", "0.5", "1.0", "5.0", "10.0", "25.0", "50.0", "100.0", "250.0", "500.0", "1000.0",
                    "remove", "delete", "clear", "0"
            );

            for (String suggestion : priceSuggestions) {
                if (suggestion.toLowerCase().startsWith(input)) {
                    completions.add(suggestion);
                }
            }

            if (sender instanceof Player) {
                Player player = (Player) sender;
                ItemStack heldItem = player.getInventory().getItemInMainHand();
                if (heldItem != null && heldItem.getType() != Material.AIR) {

                    addContextualPriceSuggestions(completions, heldItem, input);
                }
            }
        }

        completions.sort(String.CASE_INSENSITIVE_ORDER);
        return completions;
    }

    private void addContextualPriceSuggestions(List<String> completions, ItemStack item, String input) {
        Material material = item.getType();

        if (isOre(material)) {
            addIfStartsWith(completions, Arrays.asList("1.0", "2.5", "5.0", "10.0"), input);
        } else if (isFood(material)) {
            addIfStartsWith(completions, Arrays.asList("0.5", "1.0", "2.0", "3.0"), input);
        } else if (isTool(material)) {
            addIfStartsWith(completions, Arrays.asList("10.0", "25.0", "50.0", "100.0"), input);
        } else if (isArmor(material)) {
            addIfStartsWith(completions, Arrays.asList("15.0", "30.0", "75.0", "150.0"), input);
        } else if (isBlock(material)) {
            addIfStartsWith(completions, Arrays.asList("0.1", "0.5", "1.0", "2.0"), input);
        } else if (isRare(material)) {
            addIfStartsWith(completions, Arrays.asList("50.0", "100.0", "250.0", "500.0"), input);
        }
    }

    private void addIfStartsWith(List<String> completions, List<String> suggestions, String input) {
        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase().startsWith(input.toLowerCase()) && !completions.contains(suggestion)) {
                completions.add(suggestion);
            }
        }
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.contains("_ORE") || name.contains("COAL") || name.contains("IRON_INGOT") ||
                name.contains("GOLD_INGOT") || name.contains("DIAMOND") || name.contains("EMERALD");
    }

    private boolean isFood(Material material) {
        return material.isEdible() || material.name().contains("BREAD") || material.name().contains("MEAT") ||
                material.name().contains("FISH") || material.name().contains("APPLE");
    }

    private boolean isTool(Material material) {
        String name = material.name();
        return name.contains("_SWORD") || name.contains("_AXE") || name.contains("_PICKAXE") ||
                name.contains("_SHOVEL") || name.contains("_HOE") || name.contains("BOW") ||
                name.contains("CROSSBOW") || name.contains("TRIDENT");
    }

    private boolean isArmor(Material material) {
        String name = material.name();
        return name.contains("_HELMET") || name.contains("_CHESTPLATE") ||
                name.contains("_LEGGINGS") || name.contains("_BOOTS") || name.contains("SHIELD");
    }

    private boolean isBlock(Material material) {
        return material.isBlock() && !isOre(material) && !isRare(material);
    }

    private boolean isRare(Material material) {
        String name = material.name();
        return name.contains("NETHERITE") || name.contains("ELYTRA") || name.contains("TOTEM") ||
                name.contains("DRAGON") || name.contains("STAR") || name.equals("BEACON") ||
                name.equals("CONDUIT") || name.contains("SHULKER");
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}