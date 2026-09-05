package me.aov.sellgui.addons

import me.aov.sellgui.SellGUIMain
import org.bukkit.Bukkit
import org.bukkit.plugin.InvalidDescriptionException
import org.bukkit.plugin.InvalidPluginException
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.plugin.UnknownDependencyException
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.jar.JarFile
import java.util.logging.Level

class AddonManager(private val main: SellGUIMain) {
    private val loadedAddons = ArrayList<Plugin>()
    private var addonsFolder: File? = null

    fun loadAddons() {
        if (!main.config.getBoolean("addons.enabled", true)) {
            main.logger.info("Addon loading is disabled.")
            return
        }
        val folderName = main.config.getString("addons.folder", "addons")?.trim().takeUnless { it.isNullOrEmpty() } ?: "addons"
        val folder = File(main.dataFolder, folderName)
        addonsFolder = folder
        if (!folder.exists() && !folder.mkdirs()) {
            main.logger.warning("Could not create addons folder: ${folder.absolutePath}")
            return
        }
        val addonJars = folder.listFiles { file -> file.isFile && file.name.lowercase(Locale.ROOT).endsWith(".jar") }?.sortedBy { it.name.lowercase(Locale.ROOT) } ?: emptyList()
        if (addonJars.isEmpty()) {
            if (main.config.getBoolean("general.debug", false)) main.logger.info("No addon jars found in ${folder.absolutePath}")
            return
        }
        addonJars.forEach(::loadAddon)
    }

    fun disableAddons() {
        loadedAddons.asReversed().forEach { addon ->
            if (addon.isEnabled) try { Bukkit.getPluginManager().disablePlugin(addon) } catch (throwable: Throwable) { main.logger.log(Level.WARNING, "Failed to disable addon ${addon.name}", throwable) }
        }
        loadedAddons.clear()
    }

    fun getAddonsFolder(): File = addonsFolder ?: File(main.dataFolder, "addons")

    private fun loadAddon(addonJar: File) {
        readPluginName(addonJar)?.let { addonName ->
            Bukkit.getPluginManager().getPlugin(addonName)?.let {
                main.logger.info("Addon ${it.description.fullName} is already loaded, skipping ${addonJar.name}.")
                return
            }
        }
        try {
            val addon = Bukkit.getPluginManager().loadPlugin(addonJar)
            if (addon == null) {
                main.logger.warning("Could not load addon jar: ${addonJar.name}")
                return
            }
            Bukkit.getPluginManager().enablePlugin(addon)
            loadedAddons += addon
            main.logger.info("Loaded addon ${addon.description.fullName} from addons/${addonJar.name}")
        } catch (exception: UnknownDependencyException) {
            main.logger.warning("Could not load addon ${addonJar.name} because a dependency is missing: ${exception.message}")
        } catch (exception: InvalidPluginException) {
            main.logger.warning("Invalid addon jar ${addonJar.name}: ${exception.message}")
        } catch (exception: InvalidDescriptionException) {
            main.logger.warning("Invalid addon jar ${addonJar.name}: ${exception.message}")
        } catch (throwable: Throwable) {
            main.logger.log(Level.WARNING, "Failed to load addon ${addonJar.name}", throwable)
        }
    }

    private fun readPluginName(addonJar: File): String? = try {
        JarFile(addonJar).use { jarFile ->
            val pluginYml = jarFile.getJarEntry("plugin.yml") ?: return null
            jarFile.getInputStream(pluginYml).use { PluginDescriptionFile(it).name }
        }
    } catch (_: IOException) {
        null
    } catch (_: InvalidDescriptionException) {
        null
    }
}
