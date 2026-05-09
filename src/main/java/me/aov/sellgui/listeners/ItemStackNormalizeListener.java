package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.utils.ItemStackNormalizer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class ItemStackNormalizeListener implements Listener {

    private final SellGUIMain plugin;

    public ItemStackNormalizeListener(SellGUIMain plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled("stacking.normalize-on-join", true)) {
            return;
        }

        normalizeLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!isEnabled("stacking.normalize-on-quit", true)) {
            return;
        }

        ItemStackNormalizer.normalizePlayerInventory(plugin, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (!isEnabled("stacking.normalize-smelt-results", true)) {
            return;
        }

        ItemStack result = event.getResult();
        if (result != null) {
            event.setResult(ItemStackNormalizer.normalizedCopy(plugin, result));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (!isEnabled("stacking.normalize-after-furnace-extract", true)) {
            return;
        }

        normalizeLater(event.getPlayer());
    }

    private void normalizeLater(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> ItemStackNormalizer.normalizePlayerInventory(plugin, player),
                1L);
    }

    private boolean isEnabled(String path, boolean defaultValue) {
        return plugin.getConfig().getBoolean("stacking.enabled", true)
                && plugin.getConfig().getBoolean(path, defaultValue);
    }
}
