package me.aov.sellgui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import me.aov.sellgui.cache.PriceCache;
import me.aov.sellgui.commands.PriceSetterCommand;
import me.aov.sellgui.commands.PriceSetterTabCompleter;
import me.aov.sellgui.commands.SellAllCommand;
import me.aov.sellgui.commands.SellCommand;
import me.aov.sellgui.commands.SellGUITabCompleter;
import me.aov.sellgui.config.ConfigManager;
import me.aov.sellgui.handlers.PlaceholderHandler;
import me.aov.sellgui.handlers.SellGUIPlaceholderExpansion;
import me.aov.sellgui.listeners.InventoryListeners;
import me.aov.sellgui.listeners.PlayerLeaveListener;
import me.aov.sellgui.listeners.PriceEvaluationListener;
import me.aov.sellgui.listeners.PriceSetterChatListener;
import me.aov.sellgui.listeners.PriceSetterListener;
import me.aov.sellgui.listeners.SignListener;
import me.aov.sellgui.listeners.UpdateWarning;
import me.aov.sellgui.managers.AsyncPriceCalculator;
import me.aov.sellgui.managers.NBTPriceManager;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.managers.RandomPriceManager;
import me.aov.sellgui.managers.ItemNBTManager;
import me.aov.sellgui.managers.MythicLibNBTManager;
import me.aov.sellgui.managers.PersistentDataNBTManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class SellGUIMain extends JavaPlugin {
   public boolean hasShopGUIPlus;
   private SellGUIAPI sellGUIAPI;
   private static SellGUIMain instance;
   public boolean isShopGUIPlusEnabled() { return this.hasShopGUIPlus; }
   private static Economy econ;
   private ConfigManager configManager;
   private me.aov.sellgui.gui.GUIManager guiManager;
   private RandomPriceManager randomPriceManager;
   private PriceManager priceManager;
   private PriceCache priceCache;
   private AsyncPriceCalculator asyncCalculator;
   private File customItemsFile;
   private FileConfiguration customItemsConfig;
   private final ConsoleCommandSender console = this.getServer().getConsoleSender();
   private File itemPrices;
   private FileConfiguration itemPricesConfig;
   private FileConfiguration messagesConfig;
   private File customMenuItems;
   private FileConfiguration customMenuItemsConfig;
   private boolean useEssentials;
   private EssentialsHolder essentialsHolder;
   private File log;
   private SellCommand sellCommand;
   private PriceSetterCommand priceSetterCommand;
   public boolean hasMMOItems = false;
   public boolean hasNexo = false;
   private FileConfiguration logConfiguration;
   private File guiFile;
   private FileConfiguration guiConfig;
   private FileConfiguration mmoItemsPricesFileConfig;
   private File nexoPricesFile;
   private FileConfiguration nexoPricesFileConfig;
   private Map<String, Double> loadedNexoPrices = new HashMap();
   private Map<String, Double> loadedMMOItemPrices = new HashMap();
   private PriceEvaluationListener priceEvaluationListener;
   private NBTPriceManager nbtPriceManager;
   private File randomPricesFile;
   private FileConfiguration randomPricesConfig;
   private File soundsFile;
   private FileConfiguration soundsConfig;
   private ItemNBTManager itemNBTManager;

   public static SellGUIMain getInstance() {
      return instance;
   }

   public boolean isMMOItemsEnabled() {
      return this.hasMMOItems;
   }

   public FileConfiguration getCustomItemsConfig() {
      return this.customItemsConfig;
   }

   public void onEnable() {
      instance = this;
      this.guiManager = new me.aov.sellgui.gui.GUIManager(this);
      this.getLogger().info("=== SellGUI Edition ===");
      this.getLogger().info("Specifically optimized for Minecraft 1.21+");
      this.getLogger().info("Server: " + Bukkit.getVersion());
      this.getLogger().info("Bukkit Version: " + Bukkit.getBukkitVersion());
      String bukkitVersion = Bukkit.getBukkitVersion();
      if (!bukkitVersion.contains("1.20.6")) {
         this.getLogger().warning("=== VERSION WARNING ===");
         this.getLogger().warning("This plugin is best support 1.21+");
         this.getLogger().warning("Current version: " + bukkitVersion);
         this.getLogger().warning("For other versions, author will build soon, im lazy.");
         this.getLogger().warning("=======================");
      } else {
         this.getLogger().info("Perfect! Running on Minecraft 1.21+");
      }

      this.configManager = new ConfigManager(this);
      this.configManager.initializeConfigs();
      this.registerConfig();
      this.createConfigs();
      this.checkConfigVersion();
      PlaceholderHandler.initialize(this);
      this.sellGUIAPI = new SellGUIAPI(this);
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         new SellGUIPlaceholderExpansion(this).register();
         this.getLogger().info("PlaceholderAPI found, SellGUI placeholders registered.");
      } else {
         this.getLogger().info("PlaceholderAPI not found, using built-in placeholder fallbacks.");
      }
      if (Bukkit.getPluginManager().getPlugin("ShopGUIPlus") != null) {
         this.hasShopGUIPlus = true;
         this.getLogger().info("ShopGUIPlus found, SellGUI will Support get Price.");
      } else {
         this.hasShopGUIPlus = false;
         this.getLogger().info("ShopGUIPlus not found, disabled support it.");
      }

      if (this.getServer().getPluginManager().getPlugin("MMOItems") != null) {
         this.hasMMOItems = true;
         this.getLogger().info("MMOItems detected! SellGUI will attempt to read MMOItem prices for selling.");
      } else {
         this.hasMMOItems = false;
         this.getLogger().warning("MMOItems not found! MMOItem selling support might be limited.");
      }

      if (Bukkit.getPluginManager().getPlugin("MythicLib") != null) {
         this.itemNBTManager = new MythicLibNBTManager();
         this.getLogger().info("MythicLib detected! Using MythicLibNBTManager for NBT operations.");
      } else {
         this.itemNBTManager = new PersistentDataNBTManager(this);
         this.getLogger().info("MythicLib not found. Using PersistentDataNBTManager for NBT operations.");
      }

      if (!this.setupEconomy()) {
         this.getLogger().severe("Disabled due to no Vault dependency found or no economy provider!");
         this.getServer().getPluginManager().disablePlugin(this);
      } else {
         this.useEssentials = this.essentials();
         this.sellCommand = new SellCommand(this);
         this.priceSetterCommand = new PriceSetterCommand(this);
         if (this.getCommand("sellgui") != null) {
            this.getCommand("sellgui").setExecutor(this.sellCommand);
            this.getCommand("sellgui").setTabCompleter(new SellGUITabCompleter());
         } else {
            this.getLogger().severe("Command 'sellgui' not found in plugin.yml!");
         }

         if (this.getCommand("sellall") != null) {
            this.getCommand("sellall").setExecutor(new SellAllCommand(this));
         } else {
            this.getLogger().severe("Command 'sellall' not found in plugin.yml!");
         }

         if (this.getCommand("sellguiprice") != null) {
            this.getCommand("sellguiprice").setExecutor(this.priceSetterCommand);
            this.getCommand("sellguiprice").setTabCompleter(new PriceSetterTabCompleter());
         } else {
            this.getLogger().severe("Command 'sellguiprice' not found in plugin.yml!");
         }

         this.getServer().getPluginManager().registerEvents(new InventoryListeners(this), this);
         this.getServer().getPluginManager().registerEvents(new SignListener(this), this);
         this.getServer().getPluginManager().registerEvents(new PriceSetterListener(this), this);
         this.getServer().getPluginManager().registerEvents(new PriceSetterChatListener(this), this);
         this.getServer().getPluginManager().registerEvents(new PlayerLeaveListener(this), this);
         this.priceManager = new PriceManager(this);

         if (this.hasMMOItems) {
            this.nbtPriceManager = new NBTPriceManager(this);
         } else {
            this.nbtPriceManager = null;
         }

         this.randomPriceManager = new RandomPriceManager(this);
         this.priceCache = new PriceCache(this);
         this.asyncCalculator = new AsyncPriceCalculator(this);
         this.priceEvaluationListener = new PriceEvaluationListener(this);
         this.getServer().getPluginManager().registerEvents(this.priceEvaluationListener, this);
         (new UpdateChecker(this, 127355)).getVersion((version) -> {
            if (this.getDescription().getVersion().equalsIgnoreCase(version)) {
               this.getLogger().info("Plugin is up to date (Version: " + version + ")");
            } else {
               this.getLogger().info("There is a new update available for SellGUI: " + version + " (Current: " + this.getDescription().getVersion() + ")");
               this.getServer().getPluginManager().registerEvents(new UpdateWarning(this), this);
            }

         });

         this.createPrices();
         Bukkit.getScheduler().runTaskTimer(this, () -> {
            Iterator var1 = this.getServer().getOnlinePlayers().iterator();

            while(var1.hasNext()) {
               Player p = (Player)var1.next();
               if (p.getInventory() != null) {
                  ListIterator var3 = p.getInventory().iterator();

                  while(var3.hasNext()) {
                     ItemStack i = (ItemStack)var3.next();
                     if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(this, "sellgui-item"), PersistentDataType.STRING)) {
                        p.getInventory().remove(i);
                     }
                  }
               }
            }

         }, 100L, 80L);
         this.getLogger().info("SellGUI has been enabled!");
      }
   }

   public void checkConfigVersion() {
      String currentVersion = this.getConfig().getString("general.config-version");
      if (currentVersion == null) {
         currentVersion = String.valueOf(this.getConfig().getInt("config-version"));
      }

      String expectedVersion = "4";
      if (currentVersion == null || !currentVersion.equals(expectedVersion) && !currentVersion.equals("4.0") && !currentVersion.startsWith("4")) {
         this.getLogger().warning("Config version mismatch! Expected: " + expectedVersion + ", found: " + (currentVersion == null ? "Not set" : currentVersion));
         File oldConfig = new File(this.getDataFolder(), "config_old_" + System.currentTimeMillis() + ".yml");
         File currentConfigFile = new File(this.getDataFolder(), "config.yml");
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

   public void onDisable() {
      if (this.priceCache != null) {
         this.priceCache.shutdown();
      }

      if (this.asyncCalculator != null) {
         this.asyncCalculator.shutdown();
      }

      this.getLogger().info("SellGUI has been disabled.");
   }

   public void saveCustom() {
      try {
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
      if (this.itemPricesConfig == null) {
         this.getLogger().severe("itemPricesConfig is null in createPrices. This should not happen.");
      } else {
         Material[] var1 = Material.values();
         Material[] var2 = var1;
         int var3 = var1.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            Material m = var2[var4];
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
   }

   public Economy getEcon() {
      return econ;
   }

   public Economy getEconomy() {
      return econ;
   }

   public void reload() {
      this.reloadConfig();
      this.createConfigs();
      this.checkConfigVersion();

      configManager.reloadConfig("gui");
      configManager.reloadConfig("messages");
      configManager.reloadConfig("sounds");
      configManager.reloadConfig("nexo");
      configManager.reloadConfig("mmoitems");
      configManager.reloadConfig("random-prices");

      this.nexoPricesFileConfig = YamlConfiguration.loadConfiguration(nexoPricesFile);
      this.mmoItemsPricesFileConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "mmoitems.yml"));
      this.randomPricesConfig = YamlConfiguration.loadConfiguration(randomPricesFile);

      this.loadMMOItemPricesFromFile();
      this.loadNexoPricesFromFile();

      if (this.guiManager != null) {
         this.guiManager.reload();
      }

      this.getLogger().info("All configurations (gui, messages, sounds, nexo, mmoitems, random-prices) have been reloaded!");
   }

   private boolean setupEconomy() {
      if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
         this.getLogger().severe("Vault plugin not found! SellGUI requires Vault for economy features.");
         return false;
      } else {
         RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
         if (rsp == null) {
            this.getLogger().severe("No economy provider found through Vault. Ensure you have an economy plugin (e.g., EssentialsX, CMI) installed.");
            return false;
         } else {
            econ = (Economy)rsp.getProvider();
            return econ != null;
         }
      }
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

   public FileConfiguration getMessagesConfig() {
      return this.messagesConfig;
   }
   public FileConfiguration getSoundsConfig() {
      return this.soundsConfig;
   }

   @Deprecated
   public FileConfiguration getLangConfig() {
      return this.messagesConfig;
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
      this.loadedMMOItemPrices.clear();
      if (this.mmoItemsPricesFileConfig == null) {
         this.getLogger().warning("[SellGUI] mmoItemsPricesFileConfig is null. Cannot load MMOItem prices.");
         File mmoItemsPricesFile = new File(this.getDataFolder(), "mmoitems.yml");
         if (mmoItemsPricesFile.exists()) {
            this.mmoItemsPricesFileConfig = YamlConfiguration.loadConfiguration(mmoItemsPricesFile);
         } else {
            this.getLogger().info("[SellGUI] mmoitems.yml not found. No MMOItem prices loaded.");
         }
      } else {
         ConfigurationSection mmoitemsSection = this.mmoItemsPricesFileConfig.getConfigurationSection("mmoitems");
         if (mmoitemsSection != null) {
            Iterator var2 = mmoitemsSection.getKeys(false).iterator();

            while(true) {
               while(var2.hasNext()) {
                  String itemType = (String)var2.next();
                  ConfigurationSection typeSection = mmoitemsSection.getConfigurationSection(itemType);
                  if (typeSection != null) {
                     Iterator var5 = typeSection.getKeys(false).iterator();

                     while(var5.hasNext()) {
                        String itemId = (String)var5.next();
                        if (typeSection.isDouble(itemId) || typeSection.isInt(itemId)) {
                           double price = typeSection.getDouble(itemId);
                           String fullItemId = itemType.toUpperCase() + "." + itemId.toUpperCase();
                           this.loadedMMOItemPrices.put(fullItemId, price);
                        } else {
                           this.getLogger().warning("[SellGUI] loadMMOItemPricesFromFile: Price for '" + itemType + "." + itemId + "' in mmoitems.yml is not a valid number. Skipped.");
                        }
                     }
                  } else if (mmoitemsSection.isDouble(itemType) || mmoitemsSection.isInt(itemType)) {
                     if (itemType.contains(".")) {
                        this.loadedMMOItemPrices.put(itemType.toUpperCase(), mmoitemsSection.getDouble(itemType));
                     } else {
                        this.getLogger().warning("[SellGUI] loadMMOItemPricesFromFile: Found key '" + itemType + "' directly under 'mmoitems' that is not a section and not in TYPE.ID format. Skipped.");
                     }
                  } else {
                     this.getLogger().warning("[SellGUI] loadMMOItemPricesFromFile: Key '" + itemType + "' under 'mmoitems' in mmoitems.yml is not a valid section or TYPE.ID entry. Skipped.");
                  }
               }

               this.getLogger().info("[SellGUI] Loaded " + this.loadedMMOItemPrices.size() + " MMOItem prices from mmoitems.yml using section structure.");
               break;
            }
         } else {
            this.getLogger().info("[SellGUI] 'mmoitems' section not found in mmoitems.yml or file not loaded properly.");
         }

      }
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
      } catch (IOException | InvalidConfigurationException var8) {
         var8.printStackTrace();
      }

      this.customItemsFile = new File(this.getDataFolder(), "customitems.yml");
      if (!this.customItemsFile.exists()) {
         this.customItemsFile.getParentFile().mkdirs();
         this.saveResource("customitems.yml", false);
      }

      this.customItemsConfig = new YamlConfiguration();

      try {
         this.customItemsConfig.load(this.customItemsFile);
      } catch (IOException | InvalidConfigurationException var7) {
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
      } catch (IOException | InvalidConfigurationException var6) {
         var6.printStackTrace();
      }

      File messagesFile = new File(this.getDataFolder(), "messages.yml");
      if (!messagesFile.exists()) {
         messagesFile.getParentFile().mkdirs();
         this.saveResource("messages.yml", false);
      }

      this.messagesConfig = new YamlConfiguration();

      try {
         this.messagesConfig.load(messagesFile);
      } catch (IOException | InvalidConfigurationException var5) {
         var5.printStackTrace();
      }

      File mmoItemsPricesFile = new File(this.getDataFolder(), "mmoitems.yml");
      if (!mmoItemsPricesFile.exists()) {
         mmoItemsPricesFile.getParentFile().mkdirs();
         this.saveResource("mmoitems.yml", false);
      }

      this.mmoItemsPricesFileConfig = YamlConfiguration.loadConfiguration(mmoItemsPricesFile);
      this.loadMMOItemPricesFromFile();
      this.nexoPricesFile = new File(this.getDataFolder(), "nexo.yml");
      if (!this.nexoPricesFile.exists()) {
         this.nexoPricesFile.getParentFile().mkdirs();
         this.saveResource("nexo.yml", false);
      }
      this.randomPricesFile = new File(this.getDataFolder(), "random-prices.yml");
      if (!this.randomPricesFile.exists()) {
         this.randomPricesFile.getParentFile().mkdirs();
         this.saveResource("random-prices.yml", false);
      }
      this.randomPricesConfig = new YamlConfiguration();
      try {
         this.randomPricesConfig.load(this.randomPricesFile);
      } catch (IOException | InvalidConfigurationException e) {
         e.printStackTrace();
      }
      this.guiFile = new File(this.getDataFolder(), "gui.yml");
      if (!this.guiFile.exists()) {
         this.guiFile.getParentFile().mkdirs();
         this.saveResource("gui.yml", false);
      }
      this.guiFile = new File(this.getDataFolder(), "gui.yml");
      if (!this.guiFile.exists()) {
         this.guiFile.getParentFile().mkdirs();
         this.saveResource("gui.yml", false);
      }
      this.soundsFile = new File(this.getDataFolder(), "sounds.yml");
      if (!this.soundsFile.exists()) {
         this.soundsFile.getParentFile().mkdirs();
         this.saveResource("sounds.yml", false);
      }
      this.soundsConfig = new YamlConfiguration();
      try {
         this.soundsConfig.load(this.soundsFile);
      } catch (IOException | InvalidConfigurationException e) {
         e.printStackTrace();
      }
      this.nexoPricesFileConfig = YamlConfiguration.loadConfiguration(this.nexoPricesFile);
      this.loadNexoPricesFromFile();
      this.log = new File(this.getDataFolder(), "log.txt");
      if (!this.log.exists()) {
         this.log.getParentFile().mkdirs();

         try {
            if (this.log.createNewFile()) {
               this.getLogger().info("Created log.txt");
               BufferedWriter writer = new BufferedWriter(new FileWriter(this.log, true));

               try {
                  writer.append("=== SellGUI Transaction Log === ");
                  writer.append("Format: ItemType|ItemID|DisplayName|Amount|UnitPrice|TotalPrice|Player|Timestamp");
                  writer.append("ItemTypes: VANILLA, MMOITEMS, NEXO");
                  writer.append("===================================== ");
               } catch (Throwable var10) {
                  try {
                     writer.close();
                  } catch (Throwable var9) {
                     var10.addSuppressed(var9);
                  }

                  throw var10;
               }

               writer.close();
            }
         } catch (IOException var11) {
            var11.printStackTrace();
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
      this.loadedNexoPrices.clear();
      if (this.nexoPricesFileConfig == null) {
         this.getLogger().warning("[SellGUI] nexoPricesFileConfig is null. Cannot load Nexo prices.");
      } else {
         ConfigurationSection nexoSection = this.nexoPricesFileConfig.getConfigurationSection("nexo");
         if (nexoSection != null) {
            Iterator var2 = nexoSection.getKeys(false).iterator();

            while(var2.hasNext()) {
               String itemId = (String)var2.next();
               if (nexoSection.isDouble(itemId) || nexoSection.isInt(itemId)) {
                  double price = nexoSection.getDouble(itemId);
                  this.loadedNexoPrices.put(itemId, price);
               } else {
                  this.getLogger().warning("[SellGUI] loadNexoPricesFromFile: Price for '" + itemId + "' in nexo.yml is not a valid number. Skipped.");
               }
            }

            this.getLogger().info("[SellGUI] Loaded " + this.loadedNexoPrices.size() + " Nexo item prices from nexo.yml.");
         } else {
            this.getLogger().info("[SellGUI] 'nexo' section not found in nexo.yml or file not loaded properly.");
         }

      }
   }
   public boolean shouldRoundPrices() {
      return this.getConfig().getBoolean("economy.round-prices", true);
   }
   public Map<String, Double> getLoadedNexoPrices() {
      return this.loadedNexoPrices;
   }

   public PriceEvaluationListener getPriceEvaluationListener() {
      return this.priceEvaluationListener;
   }

   public PriceManager getPriceManager() {
      return this.priceManager;
   }

   public NBTPriceManager getNBTPriceManager() {
      return this.nbtPriceManager;
   }

   public RandomPriceManager getRandomPriceManager() {
      return this.randomPriceManager;
   }

   public PriceCache getPriceCache() {
      return this.priceCache;
   }

   public AsyncPriceCalculator getAsyncCalculator() {
      return this.asyncCalculator;
   }

   public ConfigManager getConfigManager() {
      return this.configManager;
   }

   public me.aov.sellgui.gui.GUIManager getGUIManager() {
      return this.guiManager;
   }

   public ItemNBTManager getItemNBTManager() {
      return this.itemNBTManager;
   }

   public void openPriceEvaluationGUI(Player player) {
      this.guiManager.openPriceEvaluationGUI(player);
   }

   public String setPlaceholders(Player player, String text) {
      return PlaceholderHandler.setPlaceholders(player, text);
   }

   public java.util.List<String> setPlaceholders(Player player, java.util.List<String> texts) {
      return PlaceholderHandler.setPlaceholders(player, texts);
   }

   public boolean isPlaceholderAPIAvailable() {
      return PlaceholderHandler.isPlaceholderAPIAvailable();
   }
}