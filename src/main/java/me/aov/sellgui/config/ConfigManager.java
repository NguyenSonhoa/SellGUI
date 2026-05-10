package me.aov.sellgui.config;
import me.aov.sellgui.SellGUIMain;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
public class ConfigManager {
    private final SellGUIMain plugin;
    private final Map<String, FileConfiguration> configs;
    private final Map<String, File> configFiles;
    private Map<String, Double> sellBonusPermissions; 
    private List<String> worthLoreBlacklistGuiTitles; 
    private List<String> worthLoreWhitelistGuiTitles;
    private boolean worthLoreWhitelistGui;
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})|&x(&[0-9a-fA-F]){6}");
    private static final Pattern SECTION_HEX_COLOR_PATTERN = Pattern.compile("\u00A7#([0-9a-fA-F]{6})|\u00A7x(\u00A7[0-9a-fA-F]){6}");
    private static final Pattern MINI_MESSAGE_HEX_COLOR_PATTERN = Pattern.compile("<#([0-9a-fA-F]{6})>");
    public ConfigManager(SellGUIMain plugin) {
        this.plugin = plugin;
        this.configs = new HashMap<>();
        this.configFiles = new HashMap<>();
        this.sellBonusPermissions = new HashMap<>(); 
        this.worthLoreBlacklistGuiTitles = new ArrayList<>(); 
        this.worthLoreWhitelistGuiTitles = new ArrayList<>();
    }
    public void initializeConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        loadConfig("config");
        loadConfig("sounds");
        loadGuiConfigs();
        loadConfig("messages");
        loadConfig("itemprices");
        loadConfig("mmoitems");
        loadConfig("nexo");
        loadConfig("random-prices");
        loadSellBonusPermissions(); 
        loadWorthLoreBlacklistGuiTitles(); 
        loadWorthLoreWhitelistGuiTitles();
        plugin.getLogger().info("Loaded " + configs.size() + " configuration files");
    }

    private void loadGuiConfigs() {
        File guiFolder = new File(plugin.getDataFolder(), "gui");
        if (!guiFolder.exists() && !guiFolder.mkdirs()) {
            plugin.getLogger().severe("Could not create gui config folder: " + guiFolder.getPath());
        }

        saveDefaultGuiResource("gui/sell_menus/default.yml");
        saveDefaultGuiResource("gui/sell_menus/fishing.yml");
        saveDefaultGuiResource("gui/price_setter.yml");
        saveDefaultGuiResource("gui/price_evaluation.yml");
        saveDefaultGuiResource("gui/autosell_settings.yml");

        YamlConfiguration mergedGuiConfig = new YamlConfiguration();
        List<File> guiFiles = new ArrayList<>();
        collectYamlFiles(guiFolder, guiFiles);
        guiFiles.sort((left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));

        for (File file : guiFiles) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            mergeSections(mergedGuiConfig, config);
            plugin.getLogger().info("Loaded GUI config: " + guiFolder.toPath().relativize(file.toPath()).toString().replace("\\", "/"));
        }

        if (guiFiles.isEmpty()) {
            File legacyGuiFile = new File(plugin.getDataFolder(), "gui.yml");
            if (legacyGuiFile.exists()) {
                mergedGuiConfig = YamlConfiguration.loadConfiguration(legacyGuiFile);
                plugin.getLogger().warning("Loaded legacy gui.yml because gui/ folder has no YAML files.");
            }
        }

        configs.put("gui", mergedGuiConfig);
        plugin.getLogger().info("Loaded " + guiFiles.size() + " GUI configuration file(s)");
    }

    private void saveDefaultGuiResource(String resourcePath) {
        File target = new File(plugin.getDataFolder(), resourcePath);
        if (!target.exists()) {
            target.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
    }

    private void collectYamlFiles(File folder, List<File> files) {
        File[] children = folder.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                collectYamlFiles(child, files);
            } else if (child.isFile() && (child.getName().endsWith(".yml") || child.getName().endsWith(".yaml"))) {
                files.add(child);
            }
        }
    }

    private void mergeSections(ConfigurationSection target, ConfigurationSection source) {
        for (String key : source.getKeys(false)) {
            if (source.isConfigurationSection(key)) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    targetSection = target.createSection(key);
                }
                mergeSections(targetSection, source.getConfigurationSection(key));
            } else {
                target.set(key, source.get(key));
            }
        }
    }

    private void loadConfig(String configName) {
        File configFile = new File(plugin.getDataFolder(), configName + ".yml");
        if (!configFile.exists()) {
            plugin.saveResource(configName + ".yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        configs.put(configName, config);
        configFiles.put(configName, configFile);
        plugin.getLogger().info("Loaded config: " + configName + ".yml");
    }
    private void loadSellBonusPermissions() {
        FileConfiguration mainConfig = getMainConfig();
        if (mainConfig == null) {
            plugin.getLogger().warning("Main config (config.yml) not loaded, cannot load sell bonus permissions.");
            return;
        }
        ConfigurationSection bonusSection = mainConfig.getConfigurationSection("economy.sell-bonuses");
        if (bonusSection != null) {
            for (String permission : bonusSection.getKeys(false)) {
                double multiplier = bonusSection.getDouble(permission);
                if (multiplier > 0) {
                    sellBonusPermissions.put(permission, multiplier);
                    plugin.getLogger().info("Loaded sell bonus permission: " + permission + " with multiplier " + multiplier);
                } else {
                    plugin.getLogger().warning("Invalid sell bonus multiplier for permission '" + permission + "': " + multiplier + ". Must be greater than 0.");
                }
            }
        } else {
            plugin.getLogger().info("No 'economy.sell-bonuses' section found in config.yml.");
        }
    }
    private void loadWorthLoreBlacklistGuiTitles() {
        FileConfiguration mainConfig = getMainConfig();
        if (mainConfig == null) {
            plugin.getLogger().warning("Main config (config.yml) not loaded, cannot load worth lore blacklist.");
            return;
        }
        List<String> rawTitles = mainConfig.getStringList("general.worth-lore-blacklist-gui-titles");
        worthLoreBlacklistGuiTitles.clear();
        for (String title : rawTitles) {
            worthLoreBlacklistGuiTitles.add(stripColorCodes(title));
        }
        plugin.getLogger().info("Loaded " + worthLoreBlacklistGuiTitles.size() + " worth lore blacklist GUI titles.");
    }
    private void loadWorthLoreWhitelistGuiTitles() {
        FileConfiguration mainConfig = getMainConfig();
        if (mainConfig == null) {
            plugin.getLogger().warning("Main config (config.yml) not loaded, cannot load worth lore whitelist.");
            return;
        }
        worthLoreWhitelistGui = mainConfig.getBoolean("general.worth-lore-whitelist-gui", false);
        List<String> rawTitles = mainConfig.getStringList("general.worth-lore-whitelist-gui-titles");
        worthLoreWhitelistGuiTitles.clear();
        for (String title : rawTitles) {
            String normalizedTitle = stripColorCodes(title);
            if (normalizedTitle != null && !normalizedTitle.isEmpty()) {
                worthLoreWhitelistGuiTitles.add(normalizedTitle);
            }
        }
        plugin.getLogger().info("Loaded " + worthLoreWhitelistGuiTitles.size() + " worth lore whitelist GUI titles.");
    }
    public FileConfiguration getConfig(String configName) {
        return configs.get(configName);
    }
    public FileConfiguration getMainConfig() {
        return getConfig("config");
    }
    public FileConfiguration getSoundsConfig() {
        return getConfig("sounds");
    }
    public FileConfiguration getGUIConfig() {
        return getConfig("gui");
    }
    public FileConfiguration getMessagesConfig() {
        return getConfig("messages");
    }
    public FileConfiguration getItemPricesConfig() {
        return getConfig("itemprices");
    }
    public FileConfiguration getMMOItemsConfig() {
        return getConfig("mmoitems");
    }
    public FileConfiguration getNexoConfig() {
        return getConfig("nexo");
    }
    public FileConfiguration getRandomPricesConfig() {
        return getConfig("random-prices");
    }
    public ConfigurationSection getAutosellSettingsGUIConfig() {
        return getGUIConfig().getConfigurationSection("autosell_settings_gui");
    }
    public String getAutosellGuiTitle() {
        return getAutosellSettingsGUIConfig().getString("title", "&6&lAutosell Settings");
    }
    public int getAutosellGuiSize() {
        return getAutosellSettingsGUIConfig().getInt("size", 54);
    }
    public ConfigurationSection getAutosellGuiNextPageButton() {
        return getAutosellSettingsGUIConfig().getConfigurationSection("items.next_page_button");
    }
    public ConfigurationSection getAutosellGuiPreviousPageButton() {
        return getAutosellSettingsGUIConfig().getConfigurationSection("items.previous_page_button");
    }
    public ConfigurationSection getAutosellGuiEnableAllButton() {
        return getAutosellSettingsGUIConfig().getConfigurationSection("items.enable_all_button");
    }
    public ConfigurationSection getAutosellGuiDisableAllButton() {
        return getAutosellSettingsGUIConfig().getConfigurationSection("items.disable_all_button");
    }
    public ConfigurationSection getAutosellGuiFillerItem() {
        return getAutosellSettingsGUIConfig().getConfigurationSection("items.filler");
    }
    public ConfigurationSection getAutosellGuiEnabledAutosellItem() {
        return getAutosellSettingsGUIConfig().getConfigurationSection("items.enabled_autosell_item");
    }
    public ConfigurationSection getAutosellGuiDisabledAutosellItem() {
        return getAutosellSettingsGUIConfig().getConfigurationSection("items.disabled_autosell_item");
    }
    public ConfigurationSection getAutosellGuiNoPricedItems() {
        return getAutosellSettingsGUIConfig().getConfigurationSection("items.no_priced_items");
    }
    public ConfigurationSection getAutosellGuiSearchButton() {
        return getAutosellSettingsGUIConfig().getConfigurationSection("items.search_button");
    }
    public int getAutosellGuiNextPageButtonCustomModelData() {
        ConfigurationSection section = getAutosellGuiNextPageButton();
        return section != null ? section.getInt("custom-model-data", 0) : 0;
    }
    public int getAutosellGuiPreviousPageButtonCustomModelData() {
        ConfigurationSection section = getAutosellGuiPreviousPageButton();
        return section != null ? section.getInt("custom-model-data", 0) : 0;
    }
    public int getAutosellGuiEnableAllButtonCustomModelData() {
        ConfigurationSection section = getAutosellGuiEnableAllButton();
        return section != null ? section.getInt("custom-model-data", 0) : 0;
    }
    public int getAutosellGuiDisableAllButtonCustomModelData() {
        ConfigurationSection section = getAutosellGuiDisableAllButton();
        return section != null ? section.getInt("custom-model-data", 0) : 0;
    }
    public int getAutosellGuiFillerItemCustomModelData() {
        ConfigurationSection section = getAutosellGuiFillerItem();
        return section != null ? section.getInt("custom-model-data", 0) : 0;
    }
    public int getAutosellGuiEnabledAutosellItemCustomModelData() {
        ConfigurationSection section = getAutosellGuiEnabledAutosellItem();
        return section != null ? section.getInt("custom-model-data", 0) : 0;
    }
    public int getAutosellGuiDisabledAutosellItemCustomModelData() {
        ConfigurationSection section = getAutosellGuiDisabledAutosellItem();
        return section != null ? section.getInt("custom-model-data", 0) : 0;
    }
    public int getAutosellGuiNoPricedItemsCustomModelData() {
        ConfigurationSection section = getAutosellGuiNoPricedItems();
        return section != null ? section.getInt("custom-model-data", 0) : 0;
    }
    public String getAutosellGlobalToggleEnabledName() {
        return getMessagesConfig().getString("autosell.button.global_toggle.enabled.name", "&a&lENABLE AUTOSELL");
    }
    public java.util.List<String> getAutosellGlobalToggleEnabledLore() {
        return getMessagesConfig().getStringList("autosell.button.global_toggle.enabled.lore");
    }
    public String getAutosellGlobalToggleDisabledName() {
        return getMessagesConfig().getString("autosell.button.global_toggle.disabled.name", "&c&lDISABLE AUTOSELL");
    }
    public java.util.List<String> getAutosellGlobalToggleDisabledLore() {
        return getMessagesConfig().getStringList("autosell.button.global_toggle.disabled.lore");
    }
    public void saveConfig(String configName) {
        if ("gui".equalsIgnoreCase(configName)) {
            plugin.getLogger().warning("GUI config is split across the gui/ folder. Save the individual YAML files instead.");
            return;
        }

        FileConfiguration config = configs.get(configName);
        File configFile = configFiles.get(configName);
        if (config != null && configFile != null) {
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save config " + configName + ".yml: " + e.getMessage());
            }
        }
    }
    public void saveAllConfigs() {
        for (String configName : configs.keySet()) {
            saveConfig(configName);
        }
    }
    public void reloadConfig(String configName) {
        if ("gui".equalsIgnoreCase(configName)) {
            loadGuiConfigs();
            return;
        }

        File configFile = configFiles.get(configName);
        if (configFile != null && configFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            configs.put(configName, config);
            plugin.getLogger().info("Reloaded config: " + configName + ".yml");
        }
    }
    public void reloadAllConfigs() {
        for (String configName : configs.keySet()) {
            if ("gui".equalsIgnoreCase(configName)) {
                continue;
            }
            reloadConfig(configName);
        }
        loadGuiConfigs();
        loadSellBonusPermissions(); 
        loadWorthLoreBlacklistGuiTitles(); 
        loadWorthLoreWhitelistGuiTitles();
        plugin.getLogger().info("Reloaded all configuration files");
    }
    public String getString(String configName, String path, String fallback) {
        FileConfiguration config = getConfig(configName);
        if (config != null) {
            return config.getString(path, fallback);
        }
        return fallback;
    }
    public double getDouble(String configName, String path, double fallback) {
        FileConfiguration config = getConfig(configName);
        if (config != null) {
            return config.getDouble(path, fallback);
        }
        return fallback;
    }
    public boolean getBoolean(String configName, String path, boolean fallback) {
        FileConfiguration config = getConfig(configName);
        if (config != null) {
            return config.getBoolean(path, fallback);
        }
        return fallback;
    }
    public int getInt(String configName, String path, int fallback) {
        FileConfiguration config = getConfig(configName);
        if (config != null) {
            return config.getInt(path, fallback);
        }
        return fallback;
    }
    public String getMoneyFormat() {
        return getMainConfig().getString("settings.money-format", "%.2f");
    }
    public Map<String, Double> getSellBonusPermissions() {
        return sellBonusPermissions;
    }
    public List<String> getWorthLoreBlacklistGuiTitles() {
        return worthLoreBlacklistGuiTitles;
    }
    public boolean isWorthLoreWhitelistGuiEnabled() {
        return worthLoreWhitelistGui;
    }
    public List<String> getWorthLoreWhitelistGuiTitles() {
        return worthLoreWhitelistGuiTitles;
    }
    public void reload() {
        configs.clear();
        configFiles.clear();
        sellBonusPermissions.clear();
        worthLoreBlacklistGuiTitles.clear();
        worthLoreWhitelistGuiTitles.clear();
        worthLoreWhitelistGui = false;
        initializeConfigs();
    }
    public static String stripColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String strippedText = MINI_MESSAGE_HEX_COLOR_PATTERN.matcher(text).replaceAll("");
        strippedText = HEX_COLOR_PATTERN.matcher(strippedText).replaceAll("");
        strippedText = ChatColor.translateAlternateColorCodes('&', strippedText);
        strippedText = SECTION_HEX_COLOR_PATTERN.matcher(strippedText).replaceAll("");
        strippedText = ChatColor.stripColor(strippedText);
        return strippedText;
    }
}
