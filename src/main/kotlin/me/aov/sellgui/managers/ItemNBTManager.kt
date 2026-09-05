package me.aov.sellgui.managers

import org.bukkit.inventory.ItemStack

interface ItemNBTManager {
    fun getSellPrice(item: ItemStack?): Double

    fun setSellPrice(item: ItemStack?, price: Double)

    fun needsEvaluation(item: ItemStack?): Boolean

    fun setNeedsEvaluation(item: ItemStack?, needsEvaluation: Boolean)

    fun getItemName(item: ItemStack?): String

    fun addNBTTag(itemStack: ItemStack?, key: String, value: String)

    fun getNBTTag(itemStack: ItemStack?, key: String): String?
}
