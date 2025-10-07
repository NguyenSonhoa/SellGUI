package me.aov.sellgui.gui;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GUIManager {

    private final SellGUIMain plugin;
    private final Map<UUID, PriceEvaluationGUI> activeEvaluationGUIs = new HashMap<>();

    public GUIManager(SellGUIMain plugin) {
        this.plugin = plugin;
    }

    public void openPriceEvaluationGUI(Player player) {

        if (activeEvaluationGUIs.containsKey(player.getUniqueId())) {
            player.closeInventory();
        }
        PriceEvaluationGUI gui = new PriceEvaluationGUI(plugin, player, plugin.getNBTPriceManager());
        activeEvaluationGUIs.put(player.getUniqueId(), gui);
        player.openInventory(gui.getInventory());
    }

    public PriceEvaluationGUI getActivePriceEvaluationGUI(Player player) {
        return activeEvaluationGUIs.get(player.getUniqueId());
    }

    public PriceEvaluationGUI getActivePriceEvaluationGUI(Inventory inventory) {
        for (PriceEvaluationGUI gui : activeEvaluationGUIs.values()) {
            if (gui.getInventory().equals(inventory)) {
                return gui;
            }
        }
        return null;
    }

    public void removePlayer(Player player) {
        activeEvaluationGUIs.remove(player.getUniqueId());
    }

    public void reload() {

        for (UUID playerUUID : Set.copyOf(activeEvaluationGUIs.keySet())) {
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof PriceEvaluationGUI) {
                player.closeInventory();
            }
        }
        activeEvaluationGUIs.clear();
    }
}