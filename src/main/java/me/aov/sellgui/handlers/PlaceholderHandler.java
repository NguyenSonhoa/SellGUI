package me.aov.sellgui.handlers;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderHandler {

    private static boolean placeholderAPIAvailable = false;
    private static SellGUIMain plugin;

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");

    public static void initialize(SellGUIMain main) {
        plugin = main;
        checkPlaceholderAPI();
    }

    private static void checkPlaceholderAPI() {
        Plugin papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        placeholderAPIAvailable = papi != null && papi.isEnabled();

        if (placeholderAPIAvailable) {
            plugin.getLogger().info("✅ PlaceholderAPI found - placeholders will be processed");
        } else {
            plugin.getLogger().info("⚠️ PlaceholderAPI not found - placeholders will show fallback messages");
        }
    }

    public static String setPlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (placeholderAPIAvailable) {
            try {

                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method setPlaceholdersMethod = papiClass.getMethod("setPlaceholders", Player.class, String.class);
                String result = (String) setPlaceholdersMethod.invoke(null, player, text);
                return result != null ? result : processWithFallback(player, text);
            } catch (Exception e) {
                plugin.getLogger().warning("Error processing PlaceholderAPI placeholders: " + e.getMessage());

                placeholderAPIAvailable = false;
                return processWithFallback(player, text);
            }
        }

        return processWithFallback(player, text);
    }

    private static String processWithFallback(Player player, String text) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = getBuiltinPlaceholder(player, placeholder);

            if (replacement == null) {

                replacement = getPlaceholderNotFoundMessage(placeholder);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String getBuiltinPlaceholder(Player player, String placeholder) {
        if (player == null) {
            return null;
        }

        switch (placeholder.toLowerCase()) {

            case "player":
            case "player_name":
                return player.getName();

            case "player_displayname":
                return player.getDisplayName();

            case "player_uuid":
                return player.getUniqueId().toString();

            case "player_world":
                return player.getWorld().getName();

            case "player_x":
                return String.valueOf((int) player.getLocation().getX());

            case "player_y":
                return String.valueOf((int) player.getLocation().getY());

            case "player_z":
                return String.valueOf((int) player.getLocation().getZ());

            case "player_health":
                return String.valueOf((int) player.getHealth());

            case "player_max_health":
                return String.valueOf((int) player.getMaxHealth());

            case "player_food":
                return String.valueOf(player.getFoodLevel());

            case "player_level":
                return String.valueOf(player.getLevel());

            case "player_exp":
                return String.valueOf((int) (player.getExp() * 100));

            case "vault_eco_balance":
            case "player_balance":
                try {
                    if (plugin != null && plugin.getEconomy() != null) {
                        return String.format("%.2f", plugin.getEconomy().getBalance(player));
                    }
                } catch (Exception e) {

                }
                return "0.00";

            case "server_name":
                return plugin.getServer().getName();

            case "server_version":
                return plugin.getServer().getVersion();

            case "server_bukkit_version":
                return plugin.getServer().getBukkitVersion();

            case "server_online":
                return String.valueOf(plugin.getServer().getOnlinePlayers().size());

            case "server_max_players":
                return String.valueOf(plugin.getServer().getMaxPlayers());

            case "sellgui_version":
                return plugin.getDescription().getVersion();

            case "sellgui_author":
                return String.join(", ", plugin.getDescription().getAuthors());

            case "time":
                return java.time.LocalTime.now().toString();

            case "date":
                return java.time.LocalDate.now().toString();

            case "timestamp":
                return java.time.LocalDateTime.now().toString();

            default:
                return null;
        }
    }

    private static String getPlaceholderNotFoundMessage(String placeholder) {

        boolean showDetailedErrors = plugin.getConfig().getBoolean("advanced.show-placeholder-errors", true);

        if (showDetailedErrors) {

            try {
                if (plugin != null && plugin.getConfigManager() != null) {
                    String message = plugin.getConfigManager().getString("messages", "general.placeholder_not_found",
                            "&c[Placeholder %placeholder% not found]");
                    return message.replace("%placeholder%", placeholder);
                }
            } catch (Exception e) {

            }
            return "&c[Placeholder %" + placeholder + "% not found]";
        } else {

            try {
                if (plugin != null && plugin.getConfigManager() != null) {
                    return plugin.getConfigManager().getString("messages", "general.placeholder_fallback", "&7[N/A]");
                }
            } catch (Exception e) {

            }
            return "&7[N/A]";
        }
    }

    public static boolean isPlaceholderAPIAvailable() {
        return placeholderAPIAvailable;
    }

    public static void refresh() {
        checkPlaceholderAPI();
    }

    public static String[] setPlaceholders(Player player, String[] texts) {
        if (texts == null) {
            return null;
        }

        String[] result = new String[texts.length];
        for (int i = 0; i < texts.length; i++) {
            result[i] = setPlaceholders(player, texts[i]);
        }

        return result;
    }

    public static java.util.List<String> setPlaceholders(Player player, java.util.List<String> texts) {
        if (texts == null) {
            return null;
        }

        java.util.List<String> result = new java.util.ArrayList<>();
        for (String text : texts) {
            result.add(setPlaceholders(player, text));
        }

        return result;
    }

    public static String getStatusInfo() {
        return String.format("PlaceholderAPI: %s, Built-in placeholders: %d",
                placeholderAPIAvailable ? "Available" : "Not Available",
                getBuiltinPlaceholderCount()
        );
    }

    private static int getBuiltinPlaceholderCount() {
        return 25;
    }
}