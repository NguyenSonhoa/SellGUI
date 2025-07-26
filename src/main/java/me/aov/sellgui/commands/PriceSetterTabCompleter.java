package me.aov.sellgui.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tab completer for the sellguiprice command
 */
public class PriceSetterTabCompleter implements TabCompleter {
    
    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("sellguiprice")) {
            final List<String> completions = new ArrayList<>();
            
            if (args.length == 1) {
                // Suggest some common price values
                List<String> priceExamples = Arrays.asList(
                    "1.0", "5.0", "10.0", "25.0", "50.0", "100.0", "250.0", "500.0", "1000.0"
                );
                
                StringUtil.copyPartialMatches(args[0], priceExamples, completions);
            }
            
            Collections.sort(completions);
            return completions;
        }
        return null;
    }
}
