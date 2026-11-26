package me.aov.sellgui.managers

import me.aov.sellgui.SellGUI
import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.commands.SellCommand
import me.aov.sellgui.hooks.BedrockBridge
import me.aov.sellgui.utils.ColorUtils
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.math.abs

class BedrockSupport(private val main: SellGUIMain, private val bridges: List<BedrockBridge> = discoverBridges(main)) : Listener {
    private class PendingSale(val gui: SellGUI, val total: Double) {
        var timeout: BukkitTask? = null
    }

    private val pendingSales = hashMapOf<UUID, PendingSale>()
    @Volatile private var stopped = false

    fun isBedrockPlayer(player: Player): Boolean = !stopped && main.config.getBoolean("bedrock.enabled", true) &&
        bridges.any { bridge -> safely { bridge.isBedrockPlayer(player.uniqueId) } }

    /** The chest is already closed; send the native form after the close packet is processed. */
    fun confirmOnClose(gui: SellGUI): Boolean {
        val player = gui.getPlayer()
        if (!player.isOnline || !isBedrockPlayer(player)) return false
        gui.returnInvalidItems()
        if (gui.hasUnevaluatedItems()) {
            player.sendMessage(gui.color(main.getMessagesConfig().getString("sell.evaluation_required")))
            return false
        }
        val total = gui.getTotal(gui.getMenu())
        if (!total.isFinite() || total <= 0) return false
        cancelPending(player.uniqueId)
        val pending = PendingSale(gui, total)
        pendingSales[player.uniqueId] = pending
        val seconds = main.config.getLong("bedrock.confirmation-timeout-seconds", 60).coerceIn(1, 300)
        pending.timeout = main.server.scheduler.runTaskLater(main, Runnable { cancelIfCurrent(player.uniqueId, pending) }, seconds * 20)
        main.server.scheduler.runTaskLater(main, Runnable {
            if (pendingSales[player.uniqueId] !== pending) return@Runnable
            if (!player.isOnline) { cancelIfCurrent(player.uniqueId, pending); return@Runnable }
            val sent = bridges.any { bridge -> safely {
                bridge.isBedrockPlayer(player.uniqueId) && bridge.sendConfirmation(
                    player.uniqueId,
                    text(gui, "title", "Confirm sale"),
                    text(gui, "content", "Sell the items you placed in %menu% for $%total%?", total),
                    text(gui, "sell-button", "Sell"),
                    text(gui, "cancel-button", "Cancel and return items")
                ) { confirmed ->
                    // Form responses arrive on the integration's network thread.
                    if (!stopped && main.isEnabled) main.server.scheduler.runTask(main, Runnable { respond(player.uniqueId, pending, confirmed) })
                }
            } }
            if (!sent) {
                cancelIfCurrent(player.uniqueId, pending)
                player.sendMessage(gui.color(main.getMessagesConfig().getString("bedrock.form_failed", "&cCould not open the Bedrock form. Your items were returned.")))
            }
        }, 2L)
        return true
    }

    private fun respond(playerId: UUID, pending: PendingSale, confirmed: Boolean) {
        if (pendingSales[playerId] !== pending) return
        pendingSales.remove(playerId)
        pending.timeout?.cancel()
        val gui = pending.gui
        val player = gui.getPlayer()
        try {
            val permission = gui.getMenuConfig().getPermission()
            if (!confirmed || !player.isOnline || !player.hasPermission("sellgui.use") ||
                permission.isNotBlank() && !player.hasPermission(permission)) return
            gui.returnInvalidItems()
            // A dynamic price change requires fresh consent to the new quote.
            if (abs(gui.getTotal(gui.getMenu()) - pending.total) > 0.000001) {
                confirmOnClose(gui)
                return
            }
            gui.sellItems(gui.getMenu())
        } finally {
            if (pendingSales[playerId]?.gui !== gui) gui.returnItems()
            gui.cleanup()
        }
    }

    fun cancelPending(playerId: UUID) {
        val pending = pendingSales.remove(playerId) ?: return
        pending.timeout?.cancel()
        pending.gui.returnItems()
        pending.gui.cleanup()
    }

    private fun cancelIfCurrent(playerId: UUID, pending: PendingSale) {
        if (pendingSales[playerId] === pending) cancelPending(playerId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cancelPending(event.player.uniqueId)
        SellCommand.getSellGUI(event.player)?.let { gui ->
            SellCommand.getSellGUIs().remove(gui)
            gui.returnItems()
            gui.cleanup()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onOpen(event: InventoryOpenEvent) = cancelPending(event.player.uniqueId)

    fun shutdown() {
        stopped = true
        pendingSales.keys.toList().forEach(::cancelPending)
        SellCommand.getSellGUIs().toList().forEach { gui ->
            gui.getPlayer().closeInventory()
            gui.returnItems()
            gui.cleanup()
            SellCommand.getSellGUIs().remove(gui)
        }
    }

    private fun text(gui: SellGUI, path: String, fallback: String, total: Double = 0.0): String =
        ColorUtils.stripColor(main.setPlaceholders(gui.getPlayer(), main.getMessagesConfig().getString("bedrock.$path", fallback).orEmpty()
            .replace("%total%", "%.2f".format(total)).replace("%menu%", gui.getMenuConfig().getDisplayName()))).orEmpty()

    private fun safely(action: () -> Boolean): Boolean = try { action() } catch (exception: RuntimeException) {
        main.logger.warning("Bedrock integration failed: ${exception.message}")
        false
    } catch (error: LinkageError) {
        main.logger.warning("Bedrock integration is incompatible: ${error.message}")
        false
    }

    companion object {
        private fun discoverBridges(main: SellGUIMain): List<BedrockBridge> =
            listOf("floodgate" to "FloodgateBridge", "Geyser-Spigot" to "GeyserBridge").mapNotNull { (plugin, bridge) ->
                if (!main.server.pluginManager.isPluginEnabled(plugin)) return@mapNotNull null
                try {
                    Class.forName("me.aov.sellgui.hooks.$bridge").getConstructor().newInstance() as BedrockBridge
                } catch (exception: ReflectiveOperationException) {
                    main.logger.warning("Could not load $plugin integration: ${exception.message}")
                    null
                } catch (error: LinkageError) {
                    main.logger.warning("Could not load $plugin integration: ${error.message}")
                    null
                }
            }
    }
}
