package me.aov.sellgui.utils

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.api.SoldItem
import org.bukkit.entity.Player
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Writes completed sale snapshots without looking up mutable prices again. */
object TransactionLogger {
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    fun log(plugin: SellGUIMain, source: String, player: Player, soldItems: List<SoldItem>) {
        if (soldItems.isEmpty() || !plugin.config.getBoolean("logging.enabled", true) ||
            !plugin.config.getBoolean("log-transactions", true)) return
        val timestamp = LocalDateTime.now().format(timestampFormat)
        try {
            BufferedWriter(FileWriter(plugin.getLog(), true)).use { writer ->
                soldItems.forEach { sold ->
                    val item = sold.getItemStack() ?: return@forEach
                    val fields = listOf(
                        "[${field(source)}] ${ItemIdentifier.getItemType(item).name}",
                        field(ItemIdentifier.getItemIdentifier(item).orEmpty()),
                        field(ColorUtils.stripColor(ItemIdentifier.getItemDisplayName(item)).orEmpty()),
                        sold.getAmount().toString(),
                        String.format(Locale.ROOT, "%.2f", sold.getUnitPrice()),
                        String.format(Locale.ROOT, "%.2f", sold.getTotalPrice()),
                        field(player.name),
                        timestamp,
                    )
                    writer.appendLine(fields.joinToString("|"))
                }
            }
        } catch (exception: IOException) {
            plugin.logger.severe("Failed to write to sell log: ${exception.message}")
        }
    }

    private fun field(value: String): String = value.replace('|', '/').replace('\r', ' ').replace('\n', ' ')
}
