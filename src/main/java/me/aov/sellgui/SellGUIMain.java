package me.aov.sellgui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.ListIterator;
import me.aov.sellgui.commands.CustomItemsCommand;
import me.aov.sellgui.commands.SellAllCommand;
import me.aov.sellgui.commands.SellCommand;
import me.aov.sellgui.listeners.InventoryListeners;
import me.aov.sellgui.listeners.SignListener;
import me.aov.sellgui.listeners.UpdateWarning;
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
    private static Economy econ;
    private final ConsoleCommandSender console = this.getServer().getConsoleSender();
    private File itemPrices;
    private FileConfiguration itemPricesConfig;
    private FileConfiguration langConfig;
    private File customItems;
    private FileConfiguration customItemsConfig;
    private File customMenuItems;
    private FileConfiguration customMenuItemsConfig;
    private boolean useEssentials;
    private EssentialsHolder essentialsHolder;
    private File log;
    private SellCommand sellCommand;
    private FileConfiguration logConfiguration;

    public SellGUIMain() {
    }

    public void onEnable() {
        this.registerConfig();
        this.createConfigs();
        checkConfigVersion();
        this.createPrices();
        this.getServer().getPluginManager().registerEvents(new InventoryListeners(this), this);
        this.getServer().getPluginManager().registerEvents(new SignListener(this), this);
        this.sellCommand = new SellCommand(this);
        this.getCommand("sellgui").setExecutor(this.sellCommand);
        this.getCommand("customitems").setExecutor(new CustomItemsCommand(this));
        this.getCommand("sellall").setExecutor(new SellAllCommand(this));
        this.setupEconomy();
        this.useEssentials = this.essentials();
        (new UpdateChecker(this, 55201)).getVersion((version) -> {
            if (this.getDescription().getVersion().equalsIgnoreCase(version)) {
                this.getLogger().info("Plugin is up to date");
            } else {
                this.getLogger().info("There is a new update available.");
                this.getServer().getPluginManager().registerEvents(new UpdateWarning(this), this);
            }

        });
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            Iterator var1 = this.getServer().getOnlinePlayers().iterator();

            while(var1.hasNext()) {
                Player p = (Player)var1.next();
                ListIterator var3 = p.getInventory().iterator();

                while(var3.hasNext()) {
                    ItemStack i = (ItemStack)var3.next();
                    if (i != null && i.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(this.getMain(), "sellgui-item"), PersistentDataType.STRING)) {
                        p.getInventory().remove(i);
                    }
                }
            }

        }, 100L, 80L);
    }
    public void checkConfigVersion() {
        String currentVersion = this.getConfig().getString("config-version");
        String expectedVersion = "1.3";

        if (currentVersion == null || !currentVersion.equals(expectedVersion)) {
            this.getLogger().warning("Config version mismatch! Expected: " + expectedVersion + ", found: " + currentVersion);
            this.getLogger().warning("Creating a backup and generating a new config...");
            this.saveResource("config.yml", true);  //
        }
    }
    public void onDisable() {
    }

    public void saveCustom() {
        try {
            this.customItemsConfig.save(this.customItems);
            this.customMenuItemsConfig.save(this.customMenuItems);
        } catch (IOException var2) {
            var2.printStackTrace();
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
        Material[] var1 = Material.values();
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            Material m = var1[var3];
            if (!this.itemPricesConfig.contains(m.name())) {
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
        this.saveDefaultConfig();
        this.createConfigs();
    }

    private boolean setupEconomy() {
        if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        } else {
            RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            } else {
                econ = (Economy)rsp.getProvider();
                return econ != null;
            }
        }
    }

    private boolean essentials() {
        if (this.getServer().getPluginManager().getPlugin("Essentials") == null) {
            this.getServer().getLogger().warning("Essentials not found, disabling essentials support");
            return false;
        } else {
            this.essentialsHolder = new EssentialsHolder();
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

    public FileConfiguration getLangConfig() {
        return this.langConfig;
    }

    public FileConfiguration getCustomItemsConfig() {
        return this.customItemsConfig;
    }

    public FileConfiguration getCustomMenuItemsConfig() {
        return this.customMenuItemsConfig;
    }

    public void createConfigs() {
        this.itemPrices = new File(this.getDataFolder(), "itemprices.yml");
        if (!this.itemPrices.exists()) {
            this.itemPrices.getParentFile().mkdirs();
            this.saveResource("itemprices.yml", false);
        }

        this.itemPricesConfig = new YamlConfiguration();

        try {
            this.itemPricesConfig.load(this.itemPrices);
        } catch (InvalidConfigurationException | IOException var7) {
            var7.printStackTrace();
        }

        this.customMenuItems = new File(this.getDataFolder(), "custommenuitems.yml");
        if (!this.customMenuItems.exists()) {
            this.customMenuItems.getParentFile().mkdirs();
            this.saveResource("custommenuitems.yml", false);
        }

        this.customMenuItemsConfig = new YamlConfiguration();

        try {
            this.customMenuItemsConfig.load(this.customMenuItems);
        } catch (InvalidConfigurationException | IOException var6) {
            var6.printStackTrace();
        }

        File lang = new File(this.getDataFolder(), "lang.yml");
        if (!lang.exists()) {
            lang.getParentFile().mkdirs();
            this.saveResource("lang.yml", false);
        }

        this.langConfig = new YamlConfiguration();

        try {
            this.langConfig.load(lang);
        } catch (InvalidConfigurationException | IOException var5) {
            var5.printStackTrace();
        }

        this.customItems = new File(this.getDataFolder(), "customitems.yml");
        if (!this.customItems.exists()) {
            this.customItems.getParentFile().mkdirs();
            this.saveResource("customitems.yml", false);
        }

        this.customItemsConfig = new YamlConfiguration();

        try {
            this.customItemsConfig.load(this.customItems);
        } catch (InvalidConfigurationException | IOException var4) {
            var4.printStackTrace();
        }

        this.log = new File(this.getDataFolder(), "log.txt");
        if (!this.log.exists()) {
            this.log.getParentFile().mkdirs();
            this.saveResource("log.txt", false);
            BufferedWriter writer = null;

            try {
                writer = new BufferedWriter(new FileWriter(this.getMain().getLog(), true));
                writer.append("Type|Display Name|Amount|Price|Player|Time\n");
                writer.close();
            } catch (IOException var3) {
                var3.printStackTrace();
            }
        }

    }

    public boolean hasEssentials() {
        return this.useEssentials;
    }

    public EssentialsHolder getEssentialsHolder() {
        return this.essentialsHolder;
    }
}
