package me.aov.sellgui.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SellGUITabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS_ALL = Arrays.asList("reload", "mmoitems");
    // Player names will be added dynamically

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("sellgui")) {
            final List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                // Gợi ý cho đối số đầu tiên
                List<String> possibleArgs = new ArrayList<>();
                if (sender.hasPermission("sellgui.reload")) {
                    possibleArgs.add("reload");
                }
                if (sender.hasPermission("sellgui.others")) {
                    // Thêm tên người chơi đang online
                    Bukkit.getOnlinePlayers().forEach(player -> possibleArgs.add(player.getName()));
                }

                StringUtil.copyPartialMatches(args[0], possibleArgs, completions);

            }

            Collections.sort(completions);
            return completions;
        }
        return null;
    }
}