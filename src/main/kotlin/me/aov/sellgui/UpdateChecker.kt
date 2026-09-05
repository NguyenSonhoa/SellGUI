package me.aov.sellgui

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.net.URL
import java.util.Scanner
import java.util.function.Consumer

class UpdateChecker(private val plugin: JavaPlugin, private val resourceId: Int) {
    fun getVersion(consumer: Consumer<String>) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                URL("https://api.spigotmc.org/legacy/update.php?resource=$resourceId").openStream().use { inputStream ->
                    Scanner(inputStream).use { scanner ->
                        if (scanner.hasNext()) {
                            consumer.accept(scanner.next())
                        }
                    }
                }
            } catch (exception: Exception) {
                plugin.logger.info("Cannot look for updates: ${exception.message}")
            }
        })
    }
}
