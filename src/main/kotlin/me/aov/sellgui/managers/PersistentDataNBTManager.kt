package me.aov.sellgui.managers

import me.aov.sellgui.SellGUIMain
import org.apache.commons.lang.WordUtils
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class PersistentDataNBTManager(private val plugin: SellGUIMain) : ItemNBTManager {
    private val sellPriceKey = NamespacedKey(plugin, "sellgui_sell_price")
    private val needsEvaluationKey = NamespacedKey(plugin, "sellgui_needs_evaluation")

    override fun getSellPrice(item: ItemStack?): Double {
        if (item == null || item.type.isAir || !item.hasItemMeta()) return 0.0
        return item.itemMeta.persistentDataContainer.get(sellPriceKey, PersistentDataType.DOUBLE) ?: 0.0
    }

    override fun setSellPrice(item: ItemStack?, price: Double) {
        updateMeta(item) { it.persistentDataContainer.set(sellPriceKey, PersistentDataType.DOUBLE, price) }
    }

    override fun needsEvaluation(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir || !item.hasItemMeta()) return false
        return item.itemMeta.persistentDataContainer.get(needsEvaluationKey, PersistentDataType.BOOLEAN) ?: false
    }

    override fun setNeedsEvaluation(item: ItemStack?, needsEvaluation: Boolean) {
        updateMeta(item) { it.persistentDataContainer.set(needsEvaluationKey, PersistentDataType.BOOLEAN, needsEvaluation) }
    }

    override fun getItemName(item: ItemStack?): String {
        if (item == null) return "Unknown Item"
        val meta = item.itemMeta
        return when {
            meta.hasDisplayName() -> meta.displayName
            meta.hasItemName() -> meta.itemName
            else -> WordUtils.capitalizeFully(item.type.name.replace('_', ' '))
        }
    }

    override fun addNBTTag(itemStack: ItemStack?, key: String, value: String) {
        updateMeta(itemStack) { it.persistentDataContainer.set(NamespacedKey(plugin, key), PersistentDataType.STRING, value) }
        itemStack?.let { plugin.logger.info("Added NBT Tag: $key -> $value to item ${it.type.name}") }
    }

    override fun getNBTTag(itemStack: ItemStack?, key: String): String? {
        if (itemStack == null || itemStack.type.isAir || !itemStack.hasItemMeta()) return null
        val value = itemStack.itemMeta.persistentDataContainer.get(NamespacedKey(plugin, key), PersistentDataType.STRING)
        plugin.logger.info(if (value == null) "NBT Tag not found: $key from item ${itemStack.type.name}" else "Retrieved NBT Tag: $key -> $value from item ${itemStack.type.name}")
        return value
    }

    private fun updateMeta(item: ItemStack?, mutate: (org.bukkit.inventory.meta.ItemMeta) -> Unit) {
        if (item == null || item.type.isAir || !item.hasItemMeta()) return
        item.itemMeta.let { meta ->
            mutate(meta)
            item.itemMeta = meta
        }
    }
}
