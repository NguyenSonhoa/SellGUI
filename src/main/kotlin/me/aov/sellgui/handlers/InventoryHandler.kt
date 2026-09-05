package me.aov.sellgui.handlers

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

object InventoryHandler {
    @JvmStatic fun getTopInventoryHolder(event: InventoryClickEvent): InventoryHolder? = try { event.view.topInventory.holder } catch (_: Exception) { null }
    @JvmStatic fun getTopInventoryHolder(event: InventoryDragEvent): InventoryHolder? = try { event.view.topInventory.holder } catch (_: Exception) { null }
    @JvmStatic fun getTopInventory(event: InventoryClickEvent): Inventory? = try { event.view.topInventory } catch (_: Exception) { null }
    @JvmStatic fun getTopInventorySize(event: InventoryClickEvent): Int = try { event.view.topInventory.size } catch (_: Exception) { 0 }
    @JvmStatic fun getTopInventorySize(event: InventoryDragEvent): Int = try { event.view.topInventory.size } catch (_: Exception) { 0 }
    @JvmStatic fun isTopInventoryClick(event: InventoryClickEvent): Boolean = try { event.clickedInventory?.equals(event.view.topInventory) == true } catch (_: Exception) { false }
    @JvmStatic fun getRawSlot(event: InventoryClickEvent): Int = try { event.rawSlot } catch (_: Exception) { -1 }
    @JvmStatic fun getSlot(event: InventoryClickEvent): Int = try { event.slot } catch (_: Exception) { -1 }
    @JvmStatic fun closeInventory(player: Player) { try { player.closeInventory() } catch (_: Exception) { } }
    @JvmStatic fun openInventory(player: Player, inventory: Inventory) { try { player.openInventory(inventory) } catch (_: Exception) { } }
}
