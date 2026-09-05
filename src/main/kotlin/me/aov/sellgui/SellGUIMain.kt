package me.aov.sellgui

import me.aov.sellgui.addons.AddonManager
import me.aov.sellgui.cache.PriceCache
import me.aov.sellgui.commands.AutosellCommand
import me.aov.sellgui.commands.PriceSetterCommand
import me.aov.sellgui.commands.PriceSetterTabCompleter
import me.aov.sellgui.commands.SellAllCommand
import me.aov.sellgui.commands.SellCommand
import me.aov.sellgui.commands.SellGUITabCompleter
import me.aov.sellgui.config.ConfigManager
import me.aov.sellgui.handlers.PlaceholderHandler
import me.aov.sellgui.handlers.SellGUIPlaceholderExpansion
import me.aov.sellgui.listeners.AutosellPlayerListener
import me.aov.sellgui.listeners.AutosellSearchListener
import me.aov.sellgui.listeners.InventoryListeners
import me.aov.sellgui.listeners.ItemStackNormalizeListener
import me.aov.sellgui.listeners.PlayerLeaveListener
import me.aov.sellgui.listeners.PriceEvaluationListener
import me.aov.sellgui.listeners.PriceSetterChatListener
import me.aov.sellgui.listeners.PriceSetterListener
import me.aov.sellgui.listeners.SignListener
import me.aov.sellgui.listeners.UpdateWarning
import me.aov.sellgui.managers.AsyncPriceCalculator
import me.aov.sellgui.managers.AutosellManager
import me.aov.sellgui.managers.ItemNBTManager
import me.aov.sellgui.managers.MythicLibNBTManager
import me.aov.sellgui.managers.NBTPriceManager
import me.aov.sellgui.managers.PersistentDataNBTManager
import me.aov.sellgui.managers.PriceManager
import me.aov.sellgui.managers.RandomPriceManager
import me.aov.sellgui.utils.ItemStackNormalizer
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.java.JavaPlugin
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

class SellGUIMain : JavaPlugin() {
    @JvmField var hasShopGUIPlus = false
    @JvmField var hasMMOItems = false
    @JvmField var hasNexo = false
    lateinit var configManager: ConfigManager
    lateinit var itemPricesConfig: FileConfiguration
    lateinit var langConfig: FileConfiguration
    lateinit var essentialsHolder: EssentialsHolder
    lateinit var itemNBTManager: ItemNBTManager
    private lateinit var guiManager: me.aov.sellgui.gui.GUIManager
    private lateinit var sellGUIAPI: SellGUIAPI
    private lateinit var addonManager: AddonManager
    private lateinit var randomPriceManager: RandomPriceManager
    private lateinit var priceManager: PriceManager
    private lateinit var priceCache: PriceCache
    private lateinit var asyncCalculator: AsyncPriceCalculator
    lateinit var autosellManager: AutosellManager
    private lateinit var nbtPriceManager: NBTPriceManager
    private lateinit var priceEvaluationListener: PriceEvaluationListener
    private lateinit var sellCommand: SellCommand
    private lateinit var priceSetterCommand: PriceSetterCommand
    private lateinit var customItemsFile: File
    private lateinit var customItemsConfig: FileConfiguration
    private lateinit var itemPricesFile: File
    private lateinit var customMenuItemsFile: File
    private lateinit var customMenuItemsConfig: FileConfiguration
    private lateinit var mmoItemsPricesFileConfig: FileConfiguration
    private lateinit var nexoPricesFile: File
    private lateinit var nexoPricesFileConfig: FileConfiguration
    private lateinit var randomPricesFile: File
    private lateinit var randomPricesConfig: FileConfiguration
    private lateinit var soundsConfig: FileConfiguration
    private lateinit var log: File
    private var useEssentials = false
    private val loadedNexoPrices = hashMapOf<String, Double>()
    private val loadedMMOItemPrices = hashMapOf<String, Double>()

    override fun onEnable() {
        instance = this
        guiManager = me.aov.sellgui.gui.GUIManager(this)
        logger.info("${ChatColor.YELLOW}SellGUI Edition enabled on ${Bukkit.getBukkitVersion()}")
        configManager = ConfigManager(this).also(ConfigManager::initializeConfigs)
        registerConfig(); createConfigs(); checkConfigVersion()
        PlaceholderHandler.initialize(this); sellGUIAPI = SellGUIAPI(this)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) SellGUIPlaceholderExpansion(this).register()
        hasShopGUIPlus = Bukkit.getPluginManager().getPlugin("ShopGUIPlus") != null
        hasMMOItems = Bukkit.getPluginManager().getPlugin("MMOItems") != null
        hasNexo = Bukkit.getPluginManager().getPlugin("Nexo") != null
        itemNBTManager = if (Bukkit.getPluginManager().getPlugin("MythicLib") != null) MythicLibNBTManager() else PersistentDataNBTManager(this)
        if (!setupEconomy()) { logger.severe("Disabled due to no Vault economy provider."); server.pluginManager.disablePlugin(this); return }
        useEssentials = essentials()
        registerCommandsAndListeners()
        priceManager = PriceManager(this); nbtPriceManager = NBTPriceManager(this); randomPriceManager = RandomPriceManager(this); priceCache = PriceCache(this); asyncCalculator = AsyncPriceCalculator(this); priceEvaluationListener = PriceEvaluationListener(this); server.pluginManager.registerEvents(priceEvaluationListener, this); autosellManager = AutosellManager(this, priceManager); server.pluginManager.registerEvents(AutosellPlayerListener(autosellManager), this); addonManager = AddonManager(this).also(AddonManager::loadAddons)
        registerWorthLoreHook(); createPrices(); cleanLegacyItems(); Bukkit.getScheduler().runTaskLater(this, Runnable { Bukkit.getOnlinePlayers().forEach { ItemStackNormalizer.normalizePlayerInventory(this, it) } }, 1L)
        logger.info("SellGUI has been enabled!")
    }

    private fun registerCommandsAndListeners() {
        sellCommand = SellCommand(this); priceSetterCommand = PriceSetterCommand(this)
        getCommand("sellgui")?.let { it.setExecutor(sellCommand); it.tabCompleter = SellGUITabCompleter(this) } ?: logger.severe("Command 'sellgui' not found in plugin.yml!")
        getCommand("autosell")?.setExecutor(AutosellCommand(this)); getCommand("sellall")?.setExecutor(SellAllCommand(this)); getCommand("sellguiprice")?.let { it.setExecutor(priceSetterCommand); it.tabCompleter = PriceSetterTabCompleter() }
        listOf(InventoryListeners(this), SignListener(this), PriceSetterListener(this), PriceSetterChatListener(this), PlayerLeaveListener(this), AutosellSearchListener(this), ItemStackNormalizeListener(this)).forEach { server.pluginManager.registerEvents(it, this) }
    }
    private fun registerWorthLoreHook() { if (!config.getBoolean("general.add-worth-lore", false)) return; if (Bukkit.getPluginManager().getPlugin("PacketEvents") == null) { logger.warning("add-worth-lore requires PacketEvents."); return }; try { Class.forName("me.aov.sellgui.hooks.PacketEventsHook").getMethod("register", SellGUIMain::class.java).invoke(null, this) } catch (exception: Exception) { logger.severe("Failed to register PacketEvents listener: ${exception.message}") } }
    private fun cleanLegacyItems() { Bukkit.getScheduler().runTaskTimer(this, Runnable { server.onlinePlayers.forEach { player -> player.inventory.contents.filterNotNull().filter { it.hasItemMeta() && it.itemMeta.persistentDataContainer.has(NamespacedKey(this, "sellgui-item"), PersistentDataType.STRING) }.forEach { player.inventory.remove(it) } } }, 100L, 80L) }
    fun checkConfigVersion() { val current = config.getString("general.config-version") ?: config.getInt("config-version").toString(); if (current != "5" && current != "5.0" && !current.startsWith("5")) { val old = File(dataFolder, "config_old_${System.currentTimeMillis()}.yml"); val source = File(dataFolder, "config.yml"); if (source.exists()) source.renameTo(old); saveResource("config.yml", true); reloadConfig(); logger.warning("Generated a new config.yml after a version mismatch.") } }
    override fun onDisable() { if (config.getBoolean("stacking.enabled", true) && config.getBoolean("stacking.normalize-on-plugin-disable", true)) Bukkit.getOnlinePlayers().forEach { ItemStackNormalizer.normalizePlayerInventory(this, it) }; if (::addonManager.isInitialized) addonManager.disableAddons(); if (::priceCache.isInitialized) priceCache.shutdown(); if (::asyncCalculator.isInitialized) asyncCalculator.shutdown(); if (::autosellManager.isInitialized) autosellManager.shutdown(); logger.info("SellGUI has been disabled.") }
    fun saveCustom() { try { customMenuItemsConfig.save(customMenuItemsFile) } catch (exception: IOException) { logger.warning("Failed to save custom menu items: ${exception.message}") } }
    fun registerConfig() { config.options().copyDefaults(true); saveDefaultConfig() }
    fun createPrices() { Material.entries.filter { it.isItem && !itemPricesConfig.contains(it.name) }.forEach { itemPricesConfig.set(it.name, 0.0) }; try { itemPricesConfig.save(itemPricesFile) } catch (exception: IOException) { logger.warning("Failed to save item prices: ${exception.message}") } }
    fun reload() { configManager.reload(); langConfig = configManager.messagesConfig; nexoPricesFileConfig = YamlConfiguration.loadConfiguration(nexoPricesFile); mmoItemsPricesFileConfig = YamlConfiguration.loadConfiguration(File(dataFolder, "mmoitems.yml")); randomPricesConfig = YamlConfiguration.loadConfiguration(randomPricesFile); loadMMOItemPricesFromFile(); loadNexoPricesFromFile(); guiManager.reload(); logger.info("All SellGUI configurations reloaded.") }
    private fun setupEconomy(): Boolean { if (server.pluginManager.getPlugin("Vault") == null) return false; val provider: RegisteredServiceProvider<Economy> = server.servicesManager.getRegistration(Economy::class.java) ?: return false; econ = provider.provider; return econ != null }
    private fun essentials(): Boolean { if (server.pluginManager.getPlugin("Essentials") == null) return false; essentialsHolder = EssentialsHolder(); return true }
    fun createConfigs() { itemPricesFile = ensureResource("itemprices.yml"); itemPricesConfig = loadYaml(itemPricesFile); customItemsFile = ensureResource("customitems.yml"); customItemsConfig = loadYaml(customItemsFile); customMenuItemsFile = ensureResource("custommenuitems.yml"); customMenuItemsConfig = loadYaml(customMenuItemsFile); langConfig = loadYaml(ensureResource("messages.yml")); val mmo = ensureResource("mmoitems.yml"); mmoItemsPricesFileConfig = loadYaml(mmo); loadMMOItemPricesFromFile(); nexoPricesFile = ensureResource("nexo.yml"); nexoPricesFileConfig = loadYaml(nexoPricesFile); randomPricesFile = ensureResource("random-prices.yml"); randomPricesConfig = loadYaml(randomPricesFile); soundsConfig = loadYaml(ensureResource("sounds.yml")); loadNexoPricesFromFile(); log = File(dataFolder, "log.txt"); if (!log.exists()) { log.parentFile.mkdirs(); log.createNewFile(); BufferedWriter(FileWriter(log, true)).use { it.appendLine("=== SellGUI Transaction Log ==="); it.appendLine("Format: ItemType|ItemID|DisplayName|Amount|UnitPrice|TotalPrice|Player|Timestamp") } } }
    private fun ensureResource(name: String): File = File(dataFolder, name).also { file -> if (!file.exists()) { file.parentFile.mkdirs(); saveResource(name, false) } }
    private fun loadYaml(file: File): FileConfiguration = YamlConfiguration.loadConfiguration(file)
    fun loadMMOItemPricesFromFile() { loadedMMOItemPrices.clear(); mmoItemsPricesFileConfig.getConfigurationSection("mmoitems")?.getKeys(false)?.forEach { type -> val section = mmoItemsPricesFileConfig.getConfigurationSection("mmoitems.$type"); if (section != null) section.getKeys(false).filter { section.isDouble(it) || section.isInt(it) }.forEach { id -> loadedMMOItemPrices["${type.uppercase()}.${id.uppercase()}"] = section.getDouble(id) } else if (type.contains(".") && (mmoItemsPricesFileConfig.isDouble("mmoitems.$type") || mmoItemsPricesFileConfig.isInt("mmoitems.$type"))) loadedMMOItemPrices[type.uppercase()] = mmoItemsPricesFileConfig.getDouble("mmoitems.$type") } }
    fun loadNexoPricesFromFile() { loadedNexoPrices.clear(); nexoPricesFileConfig.getConfigurationSection("nexo")?.getKeys(false)?.filter { nexoPricesFileConfig.isDouble("nexo.$it") || nexoPricesFileConfig.isInt("nexo.$it") }?.forEach { loadedNexoPrices[it] = nexoPricesFileConfig.getDouble("nexo.$it") } }
    fun hasEssentials(): Boolean = useEssentials
    fun isShopGUIPlusEnabled(): Boolean = hasShopGUIPlus
    fun isMMOItemsEnabled(): Boolean = hasMMOItems
    fun shouldRoundPrices(): Boolean = config.getBoolean("economy.round-prices", false)
    fun getEcon(): Economy? = econ
    fun getEconomy(): Economy? = econ
    fun getSellCommand(): SellCommand = sellCommand
    fun getMain(): SellGUIMain = this
    fun getConsole(): ConsoleCommandSender = server.consoleSender
    fun getCustomItemsConfig(): FileConfiguration = customItemsConfig
    fun getCustomMenuItemsConfig(): FileConfiguration = customMenuItemsConfig
    fun getMMOItemsPricesFileConfig(): FileConfiguration = mmoItemsPricesFileConfig
    fun getNexoPricesFileConfig(): FileConfiguration = nexoPricesFileConfig
    fun getRandomPricesConfig(): FileConfiguration = randomPricesConfig
    fun getSoundsConfig(): FileConfiguration = soundsConfig
    fun getMessagesConfig(): FileConfiguration = if (::configManager.isInitialized) configManager.messagesConfig else langConfig
    fun getLog(): File = log
    fun getLoadedMMOItemPrices(): MutableMap<String, Double> = loadedMMOItemPrices
    fun getLoadedNexoPrices(): MutableMap<String, Double> = loadedNexoPrices
    fun getPriceSetterCommand(): PriceSetterCommand = priceSetterCommand
    fun getPriceEvaluationListener(): PriceEvaluationListener = priceEvaluationListener
    fun getPriceManager(): PriceManager = priceManager
    fun getSellGUIAPI(): SellGUIAPI = sellGUIAPI
    fun getAddonManager(): AddonManager = addonManager
    fun getPriceCache(): PriceCache = priceCache
    fun getNBTPriceManager(): NBTPriceManager = nbtPriceManager
    fun getRandomPriceManager(): RandomPriceManager = randomPriceManager
    fun getAsyncCalculator(): AsyncPriceCalculator = asyncCalculator
    fun getGUIManager(): me.aov.sellgui.gui.GUIManager = guiManager
    fun openPriceEvaluationGUI(player: Player) = guiManager.openPriceEvaluationGUI(player)
    fun setPlaceholders(player: Player?, text: String?): String = PlaceholderHandler.setPlaceholders(player, text.orEmpty()).orEmpty()
    fun setPlaceholders(player: Player?, texts: List<String>?): List<String> = PlaceholderHandler.setPlaceholders(player, texts ?: emptyList()).orEmpty().filterNotNull()
    fun isPlaceholderAPIAvailable(): Boolean = PlaceholderHandler.isPlaceholderAPIAvailable()
    companion object { @JvmField var econ: Economy? = null; @JvmField var instance: SellGUIMain? = null; @JvmStatic fun getInstance(): SellGUIMain? = instance }
}
