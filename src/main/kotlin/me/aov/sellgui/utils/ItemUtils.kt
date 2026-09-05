package me.aov.sellgui.utils

import me.aov.sellgui.SellGUIMain
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

object ItemUtils {
    @JvmStatic
    fun applyConfig(item: ItemStack?, config: ConfigurationSection?) {
        if (item == null || config == null) return
        val meta = item.itemMeta ?: return
        if (config.contains("name")) meta.setDisplayName(ColorUtils.color(config.getString("name")))
        if (config.contains("lore")) meta.lore = config.getStringList("lore").map { ColorUtils.color(it).orEmpty() }
        if (config.contains("custom-model-data")) meta.setCustomModelData(config.getInt("custom-model-data"))
        applyModernComponents(meta, config)
        item.itemMeta = meta
    }

    @JvmStatic
    fun applyModernComponents(meta: ItemMeta?, config: ConfigurationSection?) {
        if (meta == null || config == null) return
        if (config.contains("item-model")) parseKey(config.getString("item-model"))?.let { invokeMethod(meta, "setItemModel", arrayOf(NamespacedKey::class.java), arrayOf(it)) }
        if (config.contains("hide-tool-tip")) invokeMethod(meta, "setHideTooltip", arrayOf(Boolean::class.javaPrimitiveType!!), arrayOf(config.getBoolean("hide-tool-tip")))
        if (config.contains("tooltip-style")) parseKey(config.getString("tooltip-style"))?.let { invokeMethod(meta, "setTooltipStyle", arrayOf(NamespacedKey::class.java), arrayOf(it)) }
    }

    private fun parseKey(value: String?): NamespacedKey? {
        if (value.isNullOrEmpty()) return null
        return try {
            val normalized = value.lowercase().trim()
            if (normalized.contains(':')) NamespacedKey.fromString(normalized) else NamespacedKey.minecraft(normalized)
        } catch (_: Exception) {
            null
        }
    }

    private fun invokeMethod(target: Any, methodName: String, parameterTypes: Array<Class<*>>, args: Array<Any>) {
        try {
            ItemMeta::class.java.getMethod(methodName, *parameterTypes).invoke(target, *args)
        } catch (_: NoSuchMethodException) {
            // The component is unavailable on older server APIs.
        } catch (exception: Exception) {
            SellGUIMain.getInstance()?.logger?.warning("Failed to invoke $methodName: ${exception.message}")
        }
    }

    @JvmStatic
    fun createItem(config: ConfigurationSection?, defaultMaterial: Material): ItemStack {
        if (config == null) return ItemStack(defaultMaterial)
        val material = Material.matchMaterial(config.getString("material", defaultMaterial.name) ?: defaultMaterial.name) ?: defaultMaterial
        return ItemStack(material).also { applyConfig(it, config) }
    }
}
