package me.aov.sellgui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

import me.aov.sellgui.commands.SellAllCommand;
import me.aov.sellgui.commands.SellCommand;
import me.aov.sellgui.commands.SellGUITabCompleter;
import me.aov.sellgui.commands.PriceSetterCommand;
import me.aov.sellgui.commands.PriceSetterTabCompleter;
import me.aov.sellgui.listeners.InventoryListeners;
import me.aov.sellgui.listeners.SignListener;
import me.aov.sellgui.listeners.UpdateWarning;
import me.aov.sellgui.listeners.PriceSetterListener;
import me.aov.sellgui.listeners.PriceSetterChatListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class SellGUIMain extends JavaPlugin {
    private SellGUIAPI sellGUIAPI;
    private static SellGUIMain instance;
    private static Economy econ;

    private File customItemsFile;
    private FileConfiguration customItemsConfig;
    private final ConsoleCommandSender console = this.getServer().getConsoleSender();
    private File itemPrices;
    private FileConfiguration itemPricesConfig;
    private FileConfiguration langConfig;
    private File customMenuItems;
    private FileConfiguration customMenuItemsConfig;
    private boolean useEssentials;
    private EssentialsHolder essentialsHolder;
    private File log;
    private SellCommand sellCommand;
    private PriceSetterCommand priceSetterCommand;
    private boolean hasMMOItems = false;
    public boolean hasNexo = false;
    private FileConfiguration logConfiguration;
    private FileConfiguration mmoItemsPricesFileConfig;
    private File nexoPricesFile;
    private FileConfiguration nexoPricesFileConfig;
    private Map<String, Double> loadedNexoPrices = new HashMap<>();
    private Map<String, Double> loadedMMOItemPrices = new HashMap<>();

    public SellGUIMain() {
    }

    public static SellGUIMain getInstance() {
        return instance;
    }

    public boolean isMMOItemsEnabled() {
        return hasMMOItems;
    }

    public FileConfiguration getCustomItemsConfig() {
        return this.customItemsConfig;
    }

    @Override
    public void onEnable() {
        instance = this;

        this.registerConfig();
        this.createConfigs();
        checkConfigVersion();

        this.sellGUIAPI = new SellGUIAPI(this);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.sellGUIAPI.registerExpansion();
            getLogger().info("PlaceholderAPI found, SellGUI placeholders registered.");
        } else {
            getLogger().info("PlaceholderAPI not found, SellGUI placeholders will not be available.");
        }
        if (!setupEconomy()) {
            getLogger().severe("Disabled due to no Vault dependency found or no economy provider!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.useEssentials = essentials();

        this.sellCommand = new SellCommand(this);
        this.priceSetterCommand = new PriceSetterCommand(this);

        if (this.getCommand("sellgui") != null) {
            this.getCommand("sellgui").setExecutor(this.sellCommand);
            this.getCommand("sellgui").setTabCompleter(new SellGUITabCompleter());
        } else {
            getLogger().severe("Command 'sellgui' not found in plugin.yml!");
        }
        if (this.getCommand("sellall") != null) {
            this.getCommand("sellall").setExecutor(new SellAllCommand(this));
        } else {
            getLogger().severe("Command 'sellall' not found in plugin.yml!");
        }
        if (this.getCommand("sellguiprice") != null) {
            this.getCommand("sellguiprice").setExecutor(this.priceSetterCommand);
            this.getCommand("sellguiprice").setTabCompleter(new PriceSetterTabCompleter());
        } else {
            getLogger().severe("Command 'sellguiprice' not found in plugin.yml!");
        }

        this.getServer().getPluginManager().registerEvents(new InventoryListeners(this), this);
        this.getServer().getPluginManager().registerEvents(new SignListener(this), this);
        this.getServer().getPluginManager().registerEvents(new PriceSetterListener(this), this);
        this.getServer().getPluginManager().registerEvents(new PriceSetterChatListener(this), this);
        (new UpdateChecker(this, 55201)).getVersion((version) -> {
            if (this.getDescription().getVersion().equalsIgnoreCase(version)) {
                this.getLogger().info("Plugin is up to date (Version: " + version + ")");
            } else {
                this.getLogger().info("There is a new update available for SellGUI: " + version + " (Current: " + this.getDescription().getVersion() + ")");
                this.getServer().getPluginManager().registerEvents(new UpdateWarning(this), this);
            }
        });

        if (getServer().getPluginManager().getPlugin("MMOItems") != null) {
            hasMMOItems = true;
            getLogger().info("MMOItems detected! SellGUI will attempt to read MMOItem prices for selling.");
        } else {
            hasMMOItems = false;
            getLogger().warning("MMOItems not found! MMOItem selling support might be limited.");
        }

        this.createPrices();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            Iterator<Player> var1 = (Iterator<Player>) this.getServer().getOnlinePlayers().iterator();
            while(var1.hasNext()) {
                Player p = var1.next();
                if (p.getInventory() == null) continue;
                ListIterator<ItemStack> var3 = p.getInventory().iterator();
                while(var3.hasNext()) {
                    ItemStack i = var3.next();
                    if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(this, "sellgui-item"), PersistentDataType.STRING)) {
                        p.getInventory().remove(i);
                    }
                }
            }
        }, 100L, 80L);
        getLogger().info("SellGUI has been enabled!");
    }


    public void checkConfigVersion() {
        String currentVersion = this.getConfig().getString("config-version");
        String expectedVersion = "1.3";

        if (currentVersion == null || !currentVersion.equals(expectedVersion)) {
            this.getLogger().warning("Config version mismatch! Expected: " + expectedVersion + ", found: " + (currentVersion == null ? "Not set" : currentVersion));
            File oldConfig = new File(getDataFolder(), "config_old_" + System.currentTimeMillis() + ".yml");
            File currentConfigFile = new File(getDataFolder(), "config.yml");
            if (currentConfigFile.exists()) {
                if (currentConfigFile.renameTo(oldConfig)) {
                    this.getLogger().warning("Backed up old config to: " + oldConfig.getName());
                } else {
                    this.getLogger().severe("Could not back up old config!");
                }
            }
            this.saveResource("config.yml", true);
            this.reloadConfig();
            this.getLogger().warning("Generated a new config.yml. Please review and transfer your old settings if necessary.");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("SellGUI has been disabled.");
    }

    public void saveCustom() {
        try {
            this.customMenuItemsConfig.save(this.customMenuItems);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public File getLog() {
        return this.log;
    }

    public void saveLog() {
        try {
            this.logConfiguration.save(this.log);
        } catch (IOException var2) {
            var2.printStackTrace();
        }
    }

    public void registerConfig() {
        this.getConfig().options().copyDefaults(true);
        this.saveDefaultConfig();
    }

    public void createPrices() {
        if (itemPricesConfig == null) {
            getLogger().severe("itemPricesConfig is null in createPrices. This should not happen.");
            return;
        }
        Material[] var1 = Material.values();
        for(Material m : var1) {
            if (m.isItem() && !this.itemPricesConfig.contains(m.name())) {
                this.itemPricesConfig.set(m.name(), 0.0);
            }
        }
        try {
            this.itemPricesConfig.save(this.itemPrices);
        } catch (IOException var5) {
            var5.printStackTrace();
        }
    }

    public Economy getEcon() {
        return econ;
    }

    public void reload() {
        this.reloadConfig();
        this.createConfigs();
        checkConfigVersion();
        getLogger().info("SellGUI configs have been reloaded.");
    }

    private boolean setupEconomy() {
        if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault plugin not found! SellGUI requires Vault for economy features.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No economy provider found through Vault. Ensure you have an economy plugin (e.g., EssentialsX, CMI) installed.");
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    private boolean essentials() {
        if (this.getServer().getPluginManager().getPlugin("Essentials") == null) {
            this.getServer().getLogger().info("Essentials not found, disabling Essentials pricing support.");
            return false;
        } else {
            this.essentialsHolder = new EssentialsHolder();
            this.getServer().getLogger().info("Essentials found, Essentials pricing support enabled.");
            return true;
        }
    }

    public SellCommand getSellCommand() {
        return this.sellCommand;
    }

    public SellGUIMain getMain() {
        return this;
    }

    public ConsoleCommandSender getConsole() {
        return this.console;
    }

    public FileConfiguration getItemPricesConfig() {
        return this.itemPricesConfig;
    }

    public Map<String, Double> getLoadedMMOItemPrices() {
        return this.loadedMMOItemPrices;
    }

    public FileConfiguration getLangConfig() {
        return this.langConfig;
    }

    public FileConfiguration getCustomMenuItemsConfig() {
        return this.customMenuItemsConfig;
    }

    public FileConfiguration getMMOItemsPricesFileConfig() {
        return this.mmoItemsPricesFileConfig;
    }

    public FileConfiguration getNexoPricesFileConfig() {
        return this.nexoPricesFileConfig;
    }

    public PriceSetterCommand getPriceSetterCommand() {
        return this.priceSetterCommand;
    }

    public void loadMMOItemPricesFromFile() {
        loadedMMOItemPrices.clear();
        if (this.mmoItemsPricesFileConfig == null) {
            getLogger().warning("[SellGUI] mmoItemsPricesFileConfig is null. Cannot load MMOItem prices.");
            File mmoItemsPricesFile = new File(this.getDataFolder(), "mmoitems.yml");
            if (mmoItemsPricesFile.exists()) {
                this.mmoItemsPricesFileConfig = YamlConfiguration.loadConfiguration(mmoItemsPricesFile);
            } else {
                getLogger().info("[SellGUI] mmoitems.yml not found. No MMOItem prices loaded.");
                return;
            }
        }

        ConfigurationSection mmoitemsSection = this.mmoItemsPricesFileConfig.getConfigurationSection("mmoitems");
        if (mmoitemsSection != null) {
            for (String itemType : mmoitemsSection.getKeys(false)) {
                ConfigurationSection typeSection = mmoitemsSection.getConfigurationSection(itemType);
                if (typeSection != null) {
                    for (String itemId : typeSection.getKeys(false)) {
                        if (typeSection.isDouble(itemId) || typeSection.isInt(itemId)) {
                            double price = typeSection.getDouble(itemId);
                            String fullItemId = (itemType.toUpperCase() + "." + itemId.toUpperCase());
                            loadedMMOItemPrices.put(fullItemId, price);
                        } else {
                            getLogger().warning("[SellGUI] loadMMOItemPricesFromFile: Price for '" + itemType + "." + itemId + "' in mmoitems.yml is not a valid number. Skipped.");
                        }
                    }
                } else {

                    if (mmoitemsSection.isDouble(itemType) || mmoitemsSection.isInt(itemType)) {
                        if (itemType.contains(".")) {
                            loadedMMOItemPrices.put(itemType.toUpperCase(), mmoitemsSection.getDouble(itemType));
                        } else {
                            getLogger().warning("[SellGUI] loadMMOItemPricesFromFile: Found key '" + itemType + "' directly under 'mmoitems' that is not a section and not in TYPE.ID format. Skipped.");
                        }
                    } else {
                        getLogger().warning("[SellGUI] loadMMOItemPricesFromFile: Key '" + itemType + "' under 'mmoitems' in mmoitems.yml is not a valid section or TYPE.ID entry. Skipped.");
                    }
                }
            }
            getLogger().info("[SellGUI] Loaded " + loadedMMOItemPrices.size() + " MMOItem prices from mmoitems.yml using section structure.");
        } else {
            getLogger().info("[SellGUI] 'mmoitems' section not found in mmoitems.yml or file not loaded properly.");
        }
    }

    public void createConfigs() {
        // itemprices.yml
        this.itemPrices = new File(this.getDataFolder(), "itemprices.yml");
        if (!this.itemPrices.exists()) {
            this.itemPrices.getParentFile().mkdirs();
            this.saveResource("itemprices.yml", false);
        }
        this.itemPricesConfig = new YamlConfiguration();
        try {
            this.itemPricesConfig.load(this.itemPrices);
        } catch (InvalidConfigurationException | IOException e) {
            e.printStackTrace();
        }

        // customitems.yml
        this.customItemsFile = new File(this.getDataFolder(), "customitems.yml");
        if (!this.customItemsFile.exists()) {
            this.customItemsFile.getParentFile().mkdirs();
            this.saveResource("customitems.yml", false);
        }
        this.customItemsConfig = new YamlConfiguration();
        try {
            this.customItemsConfig.load(this.customItemsFile);
        } catch (InvalidConfigurationException | IOException e) {
            e.printStackTrace();
        }

        // custommenuitems.yml
        this.customMenuItems = new File(this.getDataFolder(), "custommenuitems.yml");
        if (!this.customMenuItems.exists()) {
            this.customMenuItems.getParentFile().mkdirs();
            this.saveResource("custommenuitems.yml", false);
        }
        this.customMenuItemsConfig = new YamlConfiguration();
        try {
            this.customMenuItemsConfig.load(this.customMenuItems);
        } catch (InvalidConfigurationException | IOException e) {
            e.printStackTrace();
        }

        // lang.yml
        File langFile = new File(this.getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            langFile.getParentFile().mkdirs();
            this.saveResource("lang.yml", false);
        }
        this.langConfig = new YamlConfiguration();
        try {
            this.langConfig.load(langFile);
        } catch (InvalidConfigurationException | IOException e) {
            e.printStackTrace();
        }

        // mmoitems.yml
        File mmoItemsPricesFile = new File(this.getDataFolder(), "mmoitems.yml");
        if (!mmoItemsPricesFile.exists()) {
            mmoItemsPricesFile.getParentFile().mkdirs();
            this.saveResource("mmoitems.yml", false); // Lưu file mmoitems.yml mẫu nếu chưa có
        }
        this.mmoItemsPricesFileConfig = YamlConfiguration.loadConfiguration(mmoItemsPricesFile);
        loadMMOItemPricesFromFile();

        // nexo.yml
        this.nexoPricesFile = new File(this.getDataFolder(), "nexo.yml");
        if (!this.nexoPricesFile.exists()) {
            this.nexoPricesFile.getParentFile().mkdirs();
            this.saveResource("nexo.yml", false);
        }
        this.nexoPricesFileConfig = YamlConfiguration.loadConfiguration(this.nexoPricesFile);
        loadNexoPricesFromFile();

        // log.txt
        this.log = new File(this.getDataFolder(), "log.txt");
        if (!this.log.exists()) {
            this.log.getParentFile().mkdirs();
            try {
                if (this.log.createNewFile()) {
                    getLogger().info("Created log.txt");
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.log, true))) {
                        writer.append("Type|Display Name|Amount|Price|Player|Time\n");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean hasEssentials() {
        return this.useEssentials;
    }

    public EssentialsHolder getEssentialsHolder() {
        return this.essentialsHolder;
    }

    public void loadNexoPricesFromFile() {
        loadedNexoPrices.clear();
        if (this.nexoPricesFileConfig == null) {
            getLogger().warning("[SellGUI] nexoPricesFileConfig is null. Cannot load Nexo prices.");
            return;
        }

        ConfigurationSection nexoSection = this.nexoPricesFileConfig.getConfigurationSection("nexo");
        if (nexoSection != null) {
            for (String itemId : nexoSection.getKeys(false)) {
                if (nexoSection.isDouble(itemId) || nexoSection.isInt(itemId)) {
                    double price = nexoSection.getDouble(itemId);
                    loadedNexoPrices.put(itemId, price);
                } else {
                    getLogger().warning("[SellGUI] loadNexoPricesFromFile: Price for '" + itemId + "' in nexo.yml is not a valid number. Skipped.");
                }
            }
            getLogger().info("[SellGUI] Loaded " + loadedNexoPrices.size() + " Nexo item prices from nexo.yml.");
        } else {
            getLogger().info("[SellGUI] 'nexo' section not found in nexo.yml or file not loaded properly.");
        }
    }

    public Map<String, Double> getLoadedNexoPrices() {
        return this.loadedNexoPrices;
    }
}