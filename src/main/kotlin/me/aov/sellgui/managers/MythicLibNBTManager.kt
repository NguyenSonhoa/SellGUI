package me.aov.sellgui.managers

import io.lumine.mythic.lib.api.item.NBTItem
import org.bukkit.inventory.ItemStack

class MythicLibNBTManager : ItemNBTManager {
    override fun getSellPrice(item: ItemStack?): Double =
        if (item == null || item.itemMeta == null) 0.0 else NBTItem.get(item).getDouble(SELL_PRICE_TAG)

    override fun setSellPrice(item: ItemStack?, price: Double) {
        if (item != null && item.itemMeta != null) NBTItem.get(item).setDouble(SELL_PRICE_TAG, price)
    }

    override fun needsEvaluation(item: ItemStack?): Boolean =
        item != null && item.itemMeta != null && NBTItem.get(item).getBoolean(NEEDS_EVALUATION_TAG)

    override fun setNeedsEvaluation(item: ItemStack?, needsEvaluation: Boolean) {
        if (item != null && item.itemMeta != null) NBTItem.get(item).setBoolean(NEEDS_EVALUATION_TAG, needsEvaluation)
    }

    override fun getItemName(item: ItemStack?): String = when {
        item == null -> "Unknown Item"
        item.hasItemMeta() && item.itemMeta.hasDisplayName() -> item.itemMeta.displayName
        else -> item.type.name.replace("_", " ").lowercase()
    }

    override fun addNBTTag(itemStack: ItemStack?, key: String, value: String) {
        itemStack?.let { NBTItem.get(it).setString(key, value) }
    }

    override fun getNBTTag(itemStack: ItemStack?, key: String): String? = itemStack?.let { NBTItem.get(it).getString(key) }

    private companion object {
        const val SELL_PRICE_TAG = "sellPrice"
        const val NEEDS_EVALUATION_TAG = "needsEvaluation"
    }
}
