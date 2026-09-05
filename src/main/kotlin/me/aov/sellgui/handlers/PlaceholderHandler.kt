package me.aov.sellgui.handlers

import me.aov.sellgui.SellGUIMain
import org.bukkit.entity.Player
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.regex.Matcher
import java.util.regex.Pattern

object PlaceholderHandler {
    private var placeholderAPIAvailable = false
    private lateinit var plugin: SellGUIMain
    private val placeholderPattern = Pattern.compile("%([^%]+)%")

    @JvmStatic fun initialize(main: SellGUIMain) { plugin = main; checkPlaceholderAPI() }
    private fun checkPlaceholderAPI() {
        placeholderAPIAvailable = plugin.server.pluginManager.getPlugin("PlaceholderAPI")?.isEnabled == true
        plugin.logger.info(if (placeholderAPIAvailable) "PlaceholderAPI found - placeholders will be processed" else "PlaceholderAPI not found - placeholders will show fallback messages")
    }

    @JvmStatic fun setPlaceholders(player: Player?, text: String?): String? {
        if (text.isNullOrEmpty()) return text
        if (placeholderAPIAvailable) try {
            val result = Class.forName("me.clip.placeholderapi.PlaceholderAPI").getMethod("setPlaceholders", Player::class.java, String::class.java).invoke(null, player, text) as? String
            return result ?: processWithFallback(player, text)
        } catch (exception: Exception) {
            plugin.logger.warning("Error processing PlaceholderAPI placeholders: ${exception.message}")
            placeholderAPIAvailable = false
        }
        return processWithFallback(player, text)
    }

    private fun processWithFallback(player: Player?, text: String): String {
        val matcher = placeholderPattern.matcher(text)
        val result = StringBuffer()
        while (matcher.find()) {
            val placeholder = matcher.group(1)
            val replacement = getBuiltinPlaceholder(player, placeholder) ?: getPlaceholderNotFoundMessage(placeholder)
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(result)
        return result.toString()
    }

    private fun getBuiltinPlaceholder(player: Player?, placeholder: String): String? {
        player ?: return null
        return when (placeholder.lowercase()) {
            "player", "player_name" -> player.name
            "player_displayname" -> player.displayName
            "player_uuid" -> player.uniqueId.toString()
            "player_world" -> player.world.name
            "player_x" -> player.location.blockX.toString()
            "player_y" -> player.location.blockY.toString()
            "player_z" -> player.location.blockZ.toString()
            "player_health" -> player.health.toInt().toString()
            "player_max_health" -> player.maxHealth.toInt().toString()
            "player_food" -> player.foodLevel.toString()
            "player_level" -> player.level.toString()
            "player_exp" -> (player.exp * 100).toInt().toString()
            "vault_eco_balance", "player_balance" -> try { "%.2f".format(plugin.getEconomy()?.getBalance(player) ?: 0.0) } catch (_: Exception) { "0.00" }
            "server_name" -> plugin.server.name
            "server_version" -> plugin.server.version
            "server_bukkit_version" -> plugin.server.bukkitVersion
            "server_online" -> plugin.server.onlinePlayers.size.toString()
            "server_max_players" -> plugin.server.maxPlayers.toString()
            "sellgui_version" -> plugin.description.version
            "sellgui_author" -> plugin.description.authors.joinToString(", ")
            "time" -> LocalTime.now().toString()
            "date" -> LocalDate.now().toString()
            "timestamp" -> LocalDateTime.now().toString()
            else -> null
        }
    }

    private fun getPlaceholderNotFoundMessage(placeholder: String): String = if (plugin.config.getBoolean("advanced.show-placeholder-errors", true)) {
        try { plugin.configManager.getString("messages", "general.placeholder_not_found", "&c[Placeholder %placeholder% not found]")?.replace("%placeholder%", placeholder) ?: "&c[Placeholder %$placeholder% not found]" } catch (_: Exception) { "&c[Placeholder %$placeholder% not found]" }
    } else {
        try { plugin.configManager.getString("messages", "general.placeholder_fallback", "&7[N/A]") ?: "&7[N/A]" } catch (_: Exception) { "&7[N/A]" }
    }

    @JvmStatic fun isPlaceholderAPIAvailable(): Boolean = placeholderAPIAvailable
    @JvmStatic fun refresh() = checkPlaceholderAPI()
    @JvmStatic fun setPlaceholders(player: Player?, texts: Array<String?>?): Array<String?>? = texts?.map { setPlaceholders(player, it) }?.toTypedArray()
    @JvmStatic fun setPlaceholders(player: Player?, texts: List<String?>?): List<String?>? = texts?.map { setPlaceholders(player, it) }
    @JvmStatic fun getStatusInfo(): String = "PlaceholderAPI: ${if (placeholderAPIAvailable) "Available" else "Not Available"}, Built-in placeholders: 25"
}
