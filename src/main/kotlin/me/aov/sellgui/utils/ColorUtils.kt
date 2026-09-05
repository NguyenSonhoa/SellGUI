package me.aov.sellgui.utils

import net.md_5.bungee.api.ChatColor
import java.util.regex.Pattern

object ColorUtils {
    private val hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})")

    @JvmStatic
    fun color(text: String?): String? {
        if (text == null) return null

        val matcher = hexPattern.matcher(text)
        val buffer = StringBuffer(text.length + 4 * 8)
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#${matcher.group(1)}").toString())
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString())
    }

    @JvmStatic
    fun stripColor(text: String?): String? = text?.let { org.bukkit.ChatColor.stripColor(color(it)) }
}
