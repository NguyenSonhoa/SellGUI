package me.aov.sellgui.gui

import me.aov.sellgui.SellGUIMain
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.util.UUID

class GUIManager(private val plugin: SellGUIMain) {
    private val activeEvaluationGUIs = HashMap<UUID, PriceEvaluationGUI>()
    private val activeAutosellGUIs = HashMap<UUID, AutosellSettingsGUI>()

    fun openPriceEvaluationGUI(player: Player) {
        if (activeEvaluationGUIs.containsKey(player.uniqueId)) player.closeInventory()
        val gui = PriceEvaluationGUI(plugin, player, plugin.getNBTPriceManager())
        activeEvaluationGUIs[player.uniqueId] = gui
        player.openInventory(gui.inventory)
    }

    fun getActivePriceEvaluationGUI(player: Player): PriceEvaluationGUI? = activeEvaluationGUIs[player.uniqueId]

    fun getActivePriceEvaluationGUI(inventory: Inventory): PriceEvaluationGUI? =
        activeEvaluationGUIs.values.firstOrNull { it.inventory == inventory }

    fun openAutosellSettingsGUI(player: Player) {
        if (activeAutosellGUIs.containsKey(player.uniqueId)) player.closeInventory()
        val gui = AutosellSettingsGUI(plugin, player)
        activeAutosellGUIs[player.uniqueId] = gui
        player.openInventory(gui.inventory)
    }

    fun getActiveAutosellSettingsGUI(player: Player): AutosellSettingsGUI? = activeAutosellGUIs[player.uniqueId]

    fun getActiveAutosellSettingsGUI(inventory: Inventory): AutosellSettingsGUI? =
        activeAutosellGUIs.values.firstOrNull { it.inventory == inventory }

    fun removePlayer(player: Player) {
        activeEvaluationGUIs.remove(player.uniqueId)
        activeAutosellGUIs.remove(player.uniqueId)
    }

    fun reload() {
        activeEvaluationGUIs.keys.toSet().forEach { playerId ->
            plugin.server.getPlayer(playerId)?.takeIf { it.openInventory.topInventory.holder is PriceEvaluationGUI }?.closeInventory()
        }
        activeEvaluationGUIs.clear()
        activeAutosellGUIs.keys.toSet().forEach { playerId ->
            plugin.server.getPlayer(playerId)?.takeIf { it.openInventory.topInventory.holder is AutosellSettingsGUI }?.closeInventory()
        }
        activeAutosellGUIs.clear()
    }
}
