package me.aov.sellgui.config;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final SellGUIMain plugin;
    private final Map<String, FileConfiguration> configs;
    private final Map<String, File> configFiles;

    public ConfigManager(SellGUIMain plugin) {
        this.plugin = plugin;
        this.configs = new HashMap<>();
        this.configFiles = new HashMap<>();
    }

    public void initializeConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        loadConfig("config");
        loadConfig("sounds");
        loadConfig("gui");
        loadConfig("messages");
        loadConfig("itemprices");
        loadConfig("mmoitems");
        loadConfig("nexo");
        loadConfig("random-prices");

        plugin.getLogger().info("Loaded " + configs.size() + " configuration files");
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

    public void saveConfig(String configName) {
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
        File configFile = configFiles.get(configName);
        if (configFile != null && configFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            configs.put(configName, config);
            plugin.getLogger().info("Reloaded config: " + configName + ".yml");
        }
    }

    public void reloadAllConfigs() {
        for (String configName : configs.keySet()) {
            reloadConfig(configName);
        }
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
}