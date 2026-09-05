package me.aov.sellgui.utils

import me.aov.sellgui.SellGUIMain
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.util.regex.Pattern

object ItemStackNormalizer {
    private const val CURRENT_PRICE_KEY = "current_price"
    private const val EVALUATED_KEY = "evaluated"
    private const val WORTH_KEY = "worth"
    private val pricePattern = Pattern.compile("-?\\d+(?:\\.\\d+)?")
    private val transientPluginKeys = listOf(EVALUATED_KEY)

    @JvmStatic
    fun normalizePlayerInventory(plugin: SellGUIMain?, player: Player?): Boolean {
        if (plugin == null || player == null) return false
        val inventory = player.inventory
        val contents = inventory.storageContents
        var changed = false
        contents.forEach { changed = normalizeItem(plugin, it) || changed }
        changed = mergeSimilarStacks(contents) || changed
        if (changed) {
            inventory.storageContents = contents
            player.updateInventory()
        }
        return changed
    }

    @JvmStatic
    fun normalizedCopy(plugin: SellGUIMain?, item: ItemStack?): ItemStack? {
        if (item == null) return null
        return item.clone().also { normalizeItem(plugin, it) }
    }

    @JvmStatic
    fun normalizeItem(plugin: SellGUIMain?, item: ItemStack?): Boolean {
        if (plugin == null || item == null || item.type == Material.AIR || !item.hasItemMeta()) return false
        if (ItemIdentifier.getItemType(item) == ItemIdentifier.ItemType.NEXO) return false
        val meta = item.itemMeta ?: return false
        val evaluatedItem = isEvaluatedItem(plugin, meta)
        var changed = if (evaluatedItem) ensureEvaluatedKeys(plugin, meta) else removeTransientKeys(plugin, meta)
        if (!evaluatedItem) changed = removeEvaluationLore(plugin, meta) || changed
        if (changed) applyMeta(item, meta)
        return changed
    }

    private fun removeTransientKeys(plugin: SellGUIMain, meta: ItemMeta): Boolean {
        val container = meta.persistentDataContainer
        var changed = false
        transientPluginKeys.forEach { keyName ->
            try {
                val key = NamespacedKey(plugin, keyName)
                if (container.has(key)) {
                    container.remove(key)
                    changed = true
                }
            } catch (_: IllegalArgumentException) {
                // Ignore invalid legacy key names on newer servers.
            }
        }
        return changed
    }

    private fun isEvaluatedItem(plugin: SellGUIMain, meta: ItemMeta): Boolean {
        val container = meta.persistentDataContainer
        try {
            container.get(NamespacedKey(plugin, CURRENT_PRICE_KEY), PersistentDataType.DOUBLE)?.takeIf { it > 0 }?.let { return true }
        } catch (_: IllegalArgumentException) {
        }
        try {
            if (container.has(NamespacedKey(plugin, EVALUATED_KEY), PersistentDataType.BYTE)) return true
        } catch (_: IllegalArgumentException) {
        }
        try {
            container.get(NamespacedKey(plugin, WORTH_KEY), PersistentDataType.DOUBLE)?.takeIf { it > 0 }?.let { return true }
        } catch (_: IllegalArgumentException) {
        }
        return getEvaluationLorePrice(plugin, meta) != null
    }

    private fun ensureEvaluatedKeys(plugin: SellGUIMain, meta: ItemMeta): Boolean {
        val container = meta.persistentDataContainer
        var changed = false
        try {
            val key = NamespacedKey(plugin, EVALUATED_KEY)
            if (!container.has(key, PersistentDataType.BYTE)) {
                container.set(key, PersistentDataType.BYTE, 1.toByte())
                changed = true
            }
        } catch (_: IllegalArgumentException) {
        }
        try {
            val key = NamespacedKey(plugin, CURRENT_PRICE_KEY)
            if (!container.has(key, PersistentDataType.DOUBLE)) {
                getEvaluationLorePrice(plugin, meta)?.takeIf { it > 0 }?.let {
                    container.set(key, PersistentDataType.DOUBLE, it)
                    changed = true
                }
            }
        } catch (_: IllegalArgumentException) {
        }
        return changed
    }

    private fun removeEvaluationLore(plugin: SellGUIMain, meta: ItemMeta): Boolean {
        if (!meta.hasLore()) return false
        val lore = meta.lore?.takeIf { it.isNotEmpty() } ?: return false
        val cleaned = ArrayList<String>(lore.size)
        var changed = false
        val evaluationPrefix = getEvaluationLorePrefix(plugin)
        lore.forEach { line ->
            if (isEvaluationLoreLine(line, evaluationPrefix)) {
                removePreviousBlankLine(cleaned)
                changed = true
            } else {
                cleaned += line
            }
        }
        if (changed) meta.lore = cleaned.takeIf { it.isNotEmpty() }
        return changed
    }

    private fun getEvaluationLorePrice(plugin: SellGUIMain, meta: ItemMeta): Double? {
        if (!meta.hasLore()) return null
        val lore = meta.lore?.takeIf { it.isNotEmpty() } ?: return null
        val evaluationPrefix = getEvaluationLorePrefix(plugin)
        lore.forEach { line ->
            if (!isEvaluationLoreLine(line, evaluationPrefix)) return@forEach
            val matcher = pricePattern.matcher(stripColor(line))
            if (matcher.find()) return matcher.group().toDoubleOrNull() ?: 0.0
            return 0.0
        }
        return null
    }

    private fun getEvaluationLorePrefix(plugin: SellGUIMain): String {
        var template = "&aEvaluated: &f$%price%"
        try {
            plugin.configManager?.guiConfig?.let { template = it.getString("price_evaluation_gui.evaluation_lore_format", template) ?: template }
        } catch (_: Exception) {
        }
        return stripColor(template.substringBefore("%price%", template)).trim()
    }

    private fun isEvaluationLoreLine(line: String?, configuredPrefix: String): Boolean {
        val stripped = stripColor(line).trim()
        if (stripped.isEmpty()) return false
        return (configuredPrefix.isNotEmpty() && stripped.startsWith(configuredPrefix)) || stripped.startsWith("Evaluated:")
    }

    private fun removePreviousBlankLine(lore: MutableList<String>) {
        if (lore.isNotEmpty() && stripColor(lore.last()).trim().isEmpty()) lore.removeAt(lore.lastIndex)
    }

    private fun stripColor(text: String?): String = ChatColor.stripColor(text) ?: ""

    private fun applyMeta(item: ItemStack, meta: ItemMeta) {
        if (isDefaultMeta(item, meta)) {
            try {
                item.itemMeta = null
                return
            } catch (_: IllegalArgumentException) {
            } catch (_: NullPointerException) {
            }
        }
        item.itemMeta = meta
    }

    private fun isDefaultMeta(item: ItemStack, meta: ItemMeta): Boolean = try {
        Bukkit.getItemFactory().equals(meta, Bukkit.getItemFactory().getItemMeta(item.type))
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun mergeSimilarStacks(contents: Array<ItemStack?>): Boolean {
        var changed = false
        for (sourceSlot in contents.indices) {
            val source = contents[sourceSlot] ?: continue
            if (source.type == Material.AIR || source.amount <= 0 || source.maxStackSize <= 1) continue
            for (targetSlot in 0 until sourceSlot) {
                val target = contents[targetSlot] ?: continue
                if (target.type == Material.AIR || target.amount >= target.maxStackSize || !target.isSimilar(source)) continue
                val movable = minOf(source.amount, target.maxStackSize - target.amount)
                if (movable <= 0) continue
                target.amount += movable
                source.amount -= movable
                changed = true
                if (source.amount <= 0) {
                    contents[sourceSlot] = null
                    break
                }
            }
        }
        return changed
    }
}
