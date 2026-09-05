package me.aov.sellgui

import com.earth2me.essentials.Essentials
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import java.math.BigDecimal

class EssentialsHolder {
    private var essentials: Essentials? = Bukkit.getPluginManager().getPlugin("Essentials") as? Essentials

    init {
        if (essentials == null) {
            println("WEEWOOO")
        }
    }

    fun getEssentials(): Essentials? = essentials

    fun setEssentials(essentials: Essentials?) {
        this.essentials = essentials
    }

    fun getPrice(itemStack: ItemStack): BigDecimal {
        val currentEssentials = essentials ?: return BigDecimal.ZERO
        return currentEssentials.worth.getPrice(currentEssentials, itemStack) ?: BigDecimal.ZERO
    }
}
