package me.aov.sellgui.config

import me.aov.sellgui.SellGUIMain
import org.bukkit.ChatColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

class ConfigManager(private val plugin: SellGUIMain) {
    private val configs = hashMapOf<String, FileConfiguration>()
    private val configFiles = hashMapOf<String, File>()
    private val sellBonusPermissions = hashMapOf<String, Double>()
    private val worthLoreBlacklistGuiTitles = arrayListOf<String>()
    private val worthLoreWhitelistGuiTitles = arrayListOf<String>()
    private var worthLoreWhitelistGui = false

    fun initializeConfigs() {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        listOf("config", "sounds").forEach(::loadConfig)
        loadGuiConfigs()
        listOf("messages", "itemprices", "mmoitems", "nexo", "random-prices").forEach(::loadConfig)
        loadSellBonusPermissions()
        loadWorthLoreBlacklistGuiTitles()
        loadWorthLoreWhitelistGuiTitles()
        plugin.logger.info("Loaded ${configs.size} configuration files")
    }

    private fun loadGuiConfigs() {
        val guiFolder = File(plugin.dataFolder, "gui")
        if (!guiFolder.exists() && !guiFolder.mkdirs()) plugin.logger.severe("Could not create gui config folder: ${guiFolder.path}")
        listOf("gui/sell_menus/default.yml", "gui/sell_menus/fishing.yml", "gui/price_setter.yml", "gui/price_evaluation.yml", "gui/autosell_settings.yml").forEach(::saveDefaultGuiResource)
        var merged = YamlConfiguration()
        val files = arrayListOf<File>()
        collectYamlFiles(guiFolder, files)
        files.sortBy { it.path.lowercase() }
        files.forEach { file ->
            mergeSections(merged, YamlConfiguration.loadConfiguration(file))
            plugin.logger.info("Loaded GUI config: ${guiFolder.toPath().relativize(file.toPath()).toString().replace('\\', '/')}")
        }
        if (files.isEmpty()) {
            val legacyFile = File(plugin.dataFolder, "gui.yml")
            if (legacyFile.exists()) {
                merged = YamlConfiguration.loadConfiguration(legacyFile)
                plugin.logger.warning("Loaded legacy gui.yml because gui/ folder has no YAML files.")
            }
        }
        configs["gui"] = merged
        plugin.logger.info("Loaded ${files.size} GUI configuration file(s)")
    }

    private fun saveDefaultGuiResource(resourcePath: String) {
        val target = File(plugin.dataFolder, resourcePath)
        if (!target.exists()) {
            target.parentFile.mkdirs()
            plugin.saveResource(resourcePath, false)
        }
    }

    private fun collectYamlFiles(folder: File, files: MutableList<File>) {
        folder.listFiles()?.forEach { child ->
            when {
                child.isDirectory -> collectYamlFiles(child, files)
                child.isFile && (child.name.endsWith(".yml") || child.name.endsWith(".yaml")) -> files += child
            }
        }
    }

    private fun mergeSections(target: ConfigurationSection, source: ConfigurationSection) {
        source.getKeys(false).forEach { key ->
            if (source.isConfigurationSection(key)) {
                val targetSection = target.getConfigurationSection(key) ?: target.createSection(key)
                source.getConfigurationSection(key)?.let { mergeSections(targetSection, it) }
            } else {
                target.set(key, source.get(key))
            }
        }
    }

    private fun loadConfig(configName: String) {
        val file = File(plugin.dataFolder, "$configName.yml")
        if (!file.exists()) plugin.saveResource("$configName.yml", false)
        configs[configName] = YamlConfiguration.loadConfiguration(file)
        configFiles[configName] = file
        plugin.logger.info("Loaded config: $configName.yml")
    }

    private fun loadSellBonusPermissions() {
        val section = mainConfig.getConfigurationSection("economy.sell-bonuses")
        if (section == null) {
            plugin.logger.info("No 'economy.sell-bonuses' section found in config.yml.")
            return
        }
        section.getKeys(false).forEach { permission ->
            val multiplier = section.getDouble(permission)
            if (multiplier > 0) {
                sellBonusPermissions[permission] = multiplier
                plugin.logger.info("Loaded sell bonus permission: $permission with multiplier $multiplier")
            } else {
                plugin.logger.warning("Invalid sell bonus multiplier for permission '$permission': $multiplier. Must be greater than 0.")
            }
        }
    }

    private fun loadWorthLoreBlacklistGuiTitles() {
        worthLoreBlacklistGuiTitles.clear()
        worthLoreBlacklistGuiTitles += mainConfig.getStringList("general.worth-lore-blacklist-gui-titles").map(::stripColorCodes)
        plugin.logger.info("Loaded ${worthLoreBlacklistGuiTitles.size} worth lore blacklist GUI titles.")
    }

    private fun loadWorthLoreWhitelistGuiTitles() {
        worthLoreWhitelistGui = mainConfig.getBoolean("general.worth-lore-whitelist-gui", false)
        worthLoreWhitelistGuiTitles.clear()
        worthLoreWhitelistGuiTitles += mainConfig.getStringList("general.worth-lore-whitelist-gui-titles").map(::stripColorCodes).filter(String::isNotEmpty)
        plugin.logger.info("Loaded ${worthLoreWhitelistGuiTitles.size} worth lore whitelist GUI titles.")
    }

    fun getConfig(configName: String): FileConfiguration = configs.getValue(configName)
    val mainConfig: FileConfiguration get() = getConfig("config")
    val soundsConfig: FileConfiguration get() = getConfig("sounds")
    val guiConfig: FileConfiguration get() = getConfig("gui")
    val messagesConfig: FileConfiguration get() = getConfig("messages")
    val itemPricesConfig: FileConfiguration get() = getConfig("itemprices")
    val mMOItemsConfig: FileConfiguration get() = getConfig("mmoitems")
    val nexoConfig: FileConfiguration get() = getConfig("nexo")
    val randomPricesConfig: FileConfiguration get() = getConfig("random-prices")

    val autosellSettingsGUIConfig: ConfigurationSection? get() = guiConfig.getConfigurationSection("autosell_settings_gui")
    val autosellGuiTitle: String get() = autosellSettingsGUIConfig?.getString("title", "&6&lAutosell Settings") ?: "&6&lAutosell Settings"
    val autosellGuiSize: Int get() = autosellSettingsGUIConfig?.getInt("size", 54) ?: 54
    val autosellGuiNextPageButton: ConfigurationSection? get() = autosellSettingsGUIConfig?.getConfigurationSection("items.next_page_button")
    val autosellGuiPreviousPageButton: ConfigurationSection? get() = autosellSettingsGUIConfig?.getConfigurationSection("items.previous_page_button")
    val autosellGuiEnableAllButton: ConfigurationSection? get() = autosellSettingsGUIConfig?.getConfigurationSection("items.enable_all_button")
    val autosellGuiDisableAllButton: ConfigurationSection? get() = autosellSettingsGUIConfig?.getConfigurationSection("items.disable_all_button")
    val autosellGuiFillerItem: ConfigurationSection? get() = autosellSettingsGUIConfig?.getConfigurationSection("items.filler")
    val autosellGuiEnabledAutosellItem: ConfigurationSection? get() = autosellSettingsGUIConfig?.getConfigurationSection("items.enabled_autosell_item")
    val autosellGuiDisabledAutosellItem: ConfigurationSection? get() = autosellSettingsGUIConfig?.getConfigurationSection("items.disabled_autosell_item")
    val autosellGuiNoPricedItems: ConfigurationSection? get() = autosellSettingsGUIConfig?.getConfigurationSection("items.no_priced_items")
    val autosellGuiSearchButton: ConfigurationSection? get() = autosellSettingsGUIConfig?.getConfigurationSection("items.search_button")
    val autosellGuiNextPageButtonCustomModelData: Int get() = autosellGuiNextPageButton?.getInt("custom-model-data", 0) ?: 0
    val autosellGuiPreviousPageButtonCustomModelData: Int get() = autosellGuiPreviousPageButton?.getInt("custom-model-data", 0) ?: 0
    val autosellGuiEnableAllButtonCustomModelData: Int get() = autosellGuiEnableAllButton?.getInt("custom-model-data", 0) ?: 0
    val autosellGuiDisableAllButtonCustomModelData: Int get() = autosellGuiDisableAllButton?.getInt("custom-model-data", 0) ?: 0
    val autosellGuiFillerItemCustomModelData: Int get() = autosellGuiFillerItem?.getInt("custom-model-data", 0) ?: 0
    val autosellGuiEnabledAutosellItemCustomModelData: Int get() = autosellGuiEnabledAutosellItem?.getInt("custom-model-data", 0) ?: 0
    val autosellGuiDisabledAutosellItemCustomModelData: Int get() = autosellGuiDisabledAutosellItem?.getInt("custom-model-data", 0) ?: 0
    val autosellGuiNoPricedItemsCustomModelData: Int get() = autosellGuiNoPricedItems?.getInt("custom-model-data", 0) ?: 0
    val autosellGlobalToggleEnabledName: String get() = messagesConfig.getString("autosell.button.global_toggle.enabled.name", "&a&lENABLE AUTOSELL") ?: "&a&lENABLE AUTOSELL"
    val autosellGlobalToggleEnabledLore: List<String> get() = messagesConfig.getStringList("autosell.button.global_toggle.enabled.lore")
    val autosellGlobalToggleDisabledName: String get() = messagesConfig.getString("autosell.button.global_toggle.disabled.name", "&c&lDISABLE AUTOSELL") ?: "&c&lDISABLE AUTOSELL"
    val autosellGlobalToggleDisabledLore: List<String> get() = messagesConfig.getStringList("autosell.button.global_toggle.disabled.lore")

    fun saveConfig(configName: String) {
        if (configName.equals("gui", ignoreCase = true)) {
            plugin.logger.warning("GUI config is split across the gui/ folder. Save the individual YAML files instead.")
            return
        }
        val config = configs[configName]
        val file = configFiles[configName]
        if (config != null && file != null) try {
            config.save(file)
        } catch (exception: IOException) {
            plugin.logger.severe("Failed to save config $configName.yml: ${exception.message}")
        }
    }

    fun saveAllConfigs() = configs.keys.toList().forEach(::saveConfig)
    fun reloadConfig(configName: String) {
        if (configName.equals("gui", ignoreCase = true)) {
            loadGuiConfigs()
            return
        }
        configFiles[configName]?.takeIf(File::exists)?.let {
            configs[configName] = YamlConfiguration.loadConfiguration(it)
            plugin.logger.info("Reloaded config: $configName.yml")
        }
    }

    fun reloadAllConfigs() {
        configs.keys.toList().filterNot { it.equals("gui", ignoreCase = true) }.forEach(::reloadConfig)
        loadGuiConfigs()
        sellBonusPermissions.clear()
        loadSellBonusPermissions()
        loadWorthLoreBlacklistGuiTitles()
        loadWorthLoreWhitelistGuiTitles()
        plugin.logger.info("Reloaded all configuration files")
    }

    fun getString(configName: String, path: String, fallback: String): String = configs[configName]?.getString(path, fallback) ?: fallback
    fun getDouble(configName: String, path: String, fallback: Double): Double = configs[configName]?.getDouble(path, fallback) ?: fallback
    fun getBoolean(configName: String, path: String, fallback: Boolean): Boolean = configs[configName]?.getBoolean(path, fallback) ?: fallback
    fun getInt(configName: String, path: String, fallback: Int): Int = configs[configName]?.getInt(path, fallback) ?: fallback
    val moneyFormat: String get() = mainConfig.getString("settings.money-format", "%.2f") ?: "%.2f"
    fun getSellBonusPermissions(): Map<String, Double> = sellBonusPermissions
    fun getWorthLoreBlacklistGuiTitles(): List<String> = worthLoreBlacklistGuiTitles
    fun isWorthLoreWhitelistGuiEnabled(): Boolean = worthLoreWhitelistGui
    fun getWorthLoreWhitelistGuiTitles(): List<String> = worthLoreWhitelistGuiTitles

    fun reload() {
        configs.clear()
        configFiles.clear()
        sellBonusPermissions.clear()
        worthLoreBlacklistGuiTitles.clear()
        worthLoreWhitelistGuiTitles.clear()
        worthLoreWhitelistGui = false
        initializeConfigs()
    }

    companion object {
        private val hexColorPattern = Pattern.compile("&#([0-9a-fA-F]{6})|&x(&[0-9a-fA-F]){6}")
        private val sectionHexColorPattern = Pattern.compile("\u00a7#([0-9a-fA-F]{6})|\u00a7x(\u00a7[0-9a-fA-F]){6}")
        private val miniMessageHexColorPattern = Pattern.compile("<#([0-9a-fA-F]{6})>")

        @JvmStatic
        fun stripColorCodes(text: String?): String {
            if (text.isNullOrEmpty()) return ""
            var stripped = miniMessageHexColorPattern.matcher(text).replaceAll("")
            stripped = hexColorPattern.matcher(stripped).replaceAll("")
            stripped = ChatColor.translateAlternateColorCodes('&', stripped)
            stripped = sectionHexColorPattern.matcher(stripped).replaceAll("")
            return ChatColor.stripColor(stripped) ?: ""
        }
    }
}
