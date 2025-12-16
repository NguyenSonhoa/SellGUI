package me.aov.sellgui.commands;

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

public class PriceSetterTabCompleter implements TabCompleter {

    private static final List<String> COMMON_PRICES = Arrays.asList(
            "0.1", "0.5", "1.0", "2.5", "5.0", "10.0", "25.0", "50.0", "100.0", "250.0", "500.0", "1000.0"
    );

    private static final List<String> SPECIAL_COMMANDS = Arrays.asList(
            "remove", "delete", "clear", "0"
    );

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("sellguiprice")) {
            return null;
        }

        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {

            List<String> suggestions = new ArrayList<>(COMMON_PRICES);
            suggestions.addAll(SPECIAL_COMMANDS);

            addContextualSuggestions(suggestions, player);

            StringUtil.copyPartialMatches(args[0], suggestions, completions);
        }

        Collections.sort(completions);
        return completions;
    }

    private void addContextualSuggestions(List<String> suggestions, Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            return;
        }

        Material material = heldItem.getType();

        if (isOre(material)) {
            suggestions.addAll(Arrays.asList("1.5", "3.0", "7.5", "15.0"));
        } else if (isFood(material)) {
            suggestions.addAll(Arrays.asList("0.25", "0.75", "1.5", "2.25"));
        } else if (isTool(material)) {
            suggestions.addAll(Arrays.asList("12.5", "37.5", "75.0", "150.0"));
        } else if (isArmor(material)) {
            suggestions.addAll(Arrays.asList("8.0", "20.0", "40.0", "80.0"));
        } else if (isRare(material)) {
            suggestions.addAll(Arrays.asList("75.0", "200.0", "500.0", "1500.0"));
        }
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.contains("_ORE") || name.contains("COAL") || name.contains("IRON_INGOT") ||
                name.contains("GOLD_INGOT") || name.contains("DIAMOND") || name.contains("EMERALD");
    }

    private boolean isFood(Material material) {
        return material.isEdible() || material.name().contains("BREAD") ||
                material.name().contains("MEAT") || material.name().contains("FISH");
    }

    private boolean isTool(Material material) {
        String name = material.name();
        return name.contains("_SWORD") || name.contains("_AXE") || name.contains("_PICKAXE") ||
                name.contains("_SHOVEL") || name.contains("_HOE") || name.contains("BOW");
    }

    private boolean isArmor(Material material) {
        String name = material.name();
        return name.contains("_HELMET") || name.contains("_CHESTPLATE") ||
                name.contains("_LEGGINGS") || name.contains("_BOOTS");
    }

    private boolean isRare(Material material) {
        String name = material.name();
        return name.contains("NETHERITE") || name.contains("ELYTRA") || name.contains("TOTEM") ||
                name.contains("DRAGON") || name.equals("BEACON") || name.contains("SHULKER");
    }
}