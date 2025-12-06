package me.aov.sellgui.managers;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.utils.ColorUtils;
import me.aov.sellgui.utils.ItemIdentifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AutosellManager {

    private final SellGUIMain plugin;
    private final PriceManager priceManager;
    private final Map<UUID, BukkitTask> autosellTasks = new HashMap<>();
    private final Map<UUID, Map<String, Boolean>> playerAutosellToggles = new HashMap<>();
    private final Map<UUID, Boolean> globalAutosellStatus = new HashMap<>();
    private static final long AUTOSELL_DELAY_TICKS = 5 * 20L;

    private File autosellDataFile;
    private org.bukkit.configuration.file.FileConfiguration autosellDataConfig;

    public AutosellManager(SellGUIMain plugin, PriceManager priceManager) {
        this.plugin = plugin;
        this.priceManager = priceManager;
        setupAutosellDataFile();
    }

    private void setupAutosellDataFile() {
        autosellDataFile = new File(plugin.getDataFolder(), "autosell_data.yml");
        if (!autosellDataFile.exists()) {
            try { autosellDataFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("Could not create autosell_data.yml! " + e.getMessage()); }
        }
        autosellDataConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(autosellDataFile);
    }

    private void saveAutosellData() {
        try { autosellDataConfig.save(autosellDataFile); }
        catch (IOException e) { plugin.getLogger().severe("Could not save autosell_data.yml! " + e.getMessage()); }
    }

    public void startAutosellTask(Player player) {
        UUID uuid = player.getUniqueId();
        if (autosellTasks.containsKey(uuid)) return;

        loadPlayerAutosellData(uuid);

        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!player.isOnline()) {
                stopAutosellTask(player);
                return;
            }
            processAutosell(player);
        }, AUTOSELL_DELAY_TICKS, AUTOSELL_DELAY_TICKS);

        autosellTasks.put(uuid, task);
    }

    public void stopAutosellTask(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = autosellTasks.remove(uuid);
        if (task != null) task.cancel();
        savePlayerAutosellData(uuid);
        playerAutosellToggles.remove(uuid);
        globalAutosellStatus.remove(uuid);
    }

    private void loadPlayerAutosellData(UUID uuid) {
        boolean global = autosellDataConfig.getBoolean(uuid + ".global_autosell", false);
        globalAutosellStatus.put(uuid, global);

        Map<String, Boolean> toggles = new HashMap<>();
        if (autosellDataConfig.contains(uuid + ".item_toggles")) {
            for (String key : autosellDataConfig.getConfigurationSection(uuid + ".item_toggles").getKeys(false)) {
                toggles.put(key, autosellDataConfig.getBoolean(uuid + ".item_toggles." + key));
            }
        }
        playerAutosellToggles.put(uuid, toggles);
    }

    private void savePlayerAutosellData(UUID uuid) {
        autosellDataConfig.set(uuid + ".global_autosell", globalAutosellStatus.getOrDefault(uuid, false));
        if (playerAutosellToggles.containsKey(uuid)) {
            for (Map.Entry<String, Boolean> e : playerAutosellToggles.get(uuid).entrySet()) {
                autosellDataConfig.set(uuid + ".item_toggles." + e.getKey(), e.getValue());
            }
        } else {
            autosellDataConfig.set(uuid + ".item_toggles", null);
        }
        saveAutosellData();
    }

    // =====================================================================
    // process tho
    // =====================================================================
    public void processAutosell(Player player) {
        if (!isGlobalAutosellEnabled(player.getUniqueId())) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            double totalEarned = 0.0;
            Map<String, Integer> soldSummary = new HashMap<>();

            String calcMethod = plugin.getConfig().getString("prices.calculation-method", "auto").toLowerCase();

            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item == null || item.getType().isAir()) continue;

                String identifier = ItemIdentifier.getItemIdentifier(item);
                if (identifier == null || !priceManager.hasPrice(identifier)) continue;
                if (!isAutosellEnabled(player.getUniqueId(), identifier)) continue;

                // bonus
                double priceWithBonus = priceManager.getItemPriceWithPlayer(item, player);
                if (priceWithBonus <= 0) continue;

                int amount = item.getAmount();

                double finalPrice = calcMethod.equals("shopguiplus") ? priceWithBonus : priceWithBonus * amount;
                totalEarned += finalPrice;
                soldSummary.merge(ItemIdentifier.getItemDisplayName(item), amount, Integer::sum);

                player.getInventory().setItem(i, null);
            }

            if (totalEarned > 0) {
                plugin.getEcon().depositPlayer(player, totalEarned);

                double multiplier = totalEarned / calculateRawTotal(player);
                String bonusText = multiplier > 1.0001 ? " &7(×&a" + String.format("%.2f", multiplier) + "&7)" : "";

                player.sendMessage(ColorUtils.color(
                        plugin.getConfigManager().getMessagesConfig()
                                .getString("autosell.sold", "Autosell: +$%amount%%bonus%")
                                .replace("%amount%", String.format(plugin.getConfigManager().getMoneyFormat(), totalEarned))
                                .replace("%bonus%", bonusText)
                ));

                if (plugin.getConfig().getBoolean("autosell.show-item-summary", true)) {
                    for (Map.Entry<String, Integer> e : soldSummary.entrySet()) {
                        player.sendMessage(ColorUtils.color(
                                " &7- %count%x %item%".replace("%count%", String.valueOf(e.getValue()))
                                        .replace("%item%", e.getKey())
                        ));
                    }
                }
            }
        });
    }

    //
    private double calculateRawTotal(Player player) {
        double total = 0.0;
        String calcMethod = plugin.getConfig().getString("prices.calculation-method", "auto").toLowerCase();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            String id = ItemIdentifier.getItemIdentifier(item);
            if (id == null || !priceManager.hasPrice(id)) continue;
            if (!isAutosellEnabled(player.getUniqueId(), id)) continue;
            double price = priceManager.getItemPrice(item); // giá gốc, không bonus
            if (price <= 0) continue;
            total += calcMethod.equals("shopguiplus") ? price : price * item.getAmount();
        }
        return total;
    }

    // =====================================================================
    // --------
    // =====================================================================
    public boolean isAutosellEnabled(UUID playerUUID, String itemIdentifier) {
        return playerAutosellToggles.getOrDefault(playerUUID, new HashMap<>()).getOrDefault(itemIdentifier, false);
    }

    public void setAutosellEnabled(UUID playerUUID, String itemIdentifier, boolean enabled) {
        playerAutosellToggles.computeIfAbsent(playerUUID, k -> new HashMap<>()).put(itemIdentifier, enabled);
        savePlayerAutosellData(playerUUID);
    }

    public boolean isGlobalAutosellEnabled(UUID playerUUID) {
        return globalAutosellStatus.getOrDefault(playerUUID, false);
    }

    public void setGlobalAutosellEnabled(UUID playerUUID, boolean enabled) {
        globalAutosellStatus.put(playerUUID, enabled);
        savePlayerAutosellData(playerUUID);
    }

    public void shutdown() {
        for (UUID uuid : new HashMap<>(autosellTasks).keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) stopAutosellTask(p);
        }
    }
}