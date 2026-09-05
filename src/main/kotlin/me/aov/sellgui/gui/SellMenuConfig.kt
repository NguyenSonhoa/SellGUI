package me.aov.sellgui.gui

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.utils.ItemIdentifier
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import java.util.Locale

class SellMenuConfig private constructor(private val plugin: SellGUIMain, private val id: String, private val section: ConfigurationSection) {
    private val allowedItems = loadItemSet(section, "item-filter.allowed-items", "allowed-items")
    private val deniedItems = loadItemSet(section, "item-filter.denied-items", "denied-items", "item-filter.blocked-items", "blocked-items")
    private val exclusiveItems = section.getBoolean("item-filter.exclusive", section.getBoolean("exclusive-items", false))
    fun allowsItem(item: ItemStack?): Boolean = item != null && item.type != Material.AIR && !matchesDeniedItem(item) && (allowedItems.isEmpty() && !isExclusiveToAnotherMenu(item) || allowedItems.isNotEmpty() && matchesAllowedItem(item))
    private fun isExclusiveToAnotherMenu(item: ItemStack): Boolean = getMenuIds(plugin).any { menuId -> menuId != id && load(plugin, menuId)?.let { it.isExclusiveItems() && it.matchesAllowedItem(item) } == true }
    fun matchesAllowedItem(item: ItemStack?): Boolean = matchesItemSet(item, allowedItems)
    fun matchesDeniedItem(item: ItemStack?): Boolean = matchesItemSet(item, deniedItems)
    private fun matchesItemSet(item: ItemStack?, itemSet: Set<String>): Boolean { if (item == null || item.type == Material.AIR || itemSet.isEmpty()) return false; return normalizeItemKey(ItemIdentifier.getItemIdentifier(item))?.let(itemSet::contains) == true || itemSet.contains(normalizeItemKey(item.type.name)) }
    fun getId(): String = id; fun getDisplayName(): String = section.getString("name", id) ?: id; fun getPermission(): String = section.getString("permission", "") ?: ""; fun getString(path: String, fallback: String): String = section.getString(path, fallback) ?: fallback; fun getInt(path: String, fallback: Int): Int = section.getInt(path, fallback); fun getBoolean(path: String, fallback: Boolean): Boolean = section.getBoolean(path, fallback); fun contains(path: String): Boolean = section.contains(path); fun getIntegerList(path: String): List<Int> = section.getIntegerList(path); fun getStringList(path: String): List<String> = section.getStringList(path); fun isExclusiveItems(): Boolean = exclusiveItems
    companion object {
        @JvmField val DEFAULT_MENU_ID = "default"
        @JvmStatic fun load(plugin: SellGUIMain, requestedId: String?): SellMenuConfig? { val config = plugin.configManager.guiConfig ?: return null; val id = normalizeMenuId(requestedId); val section = config.getConfigurationSection("sell_menus.$id") ?: if (id == DEFAULT_MENU_ID) config.getConfigurationSection("sell_gui") else null; return section?.let { SellMenuConfig(plugin, id, it) } }
        @JvmStatic fun getMenuIds(plugin: SellGUIMain): List<String> { val config = plugin.configManager.guiConfig ?: return listOf(DEFAULT_MENU_ID); val ids = linkedSetOf<String>(); config.getConfigurationSection("sell_menus")?.getKeys(false)?.filter { config.isConfigurationSection("sell_menus.$it") }?.forEach { ids += normalizeMenuId(it) }; if (ids.isEmpty() && config.isConfigurationSection("sell_gui")) ids += DEFAULT_MENU_ID; return if (ids.isEmpty()) listOf(DEFAULT_MENU_ID) else ids.toList() }
        @JvmStatic fun menuExists(plugin: SellGUIMain, requestedId: String?): Boolean = load(plugin, requestedId) != null
        @JvmStatic fun normalizeMenuId(rawId: String?): String = rawId?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT) ?: DEFAULT_MENU_ID
        @JvmStatic fun normalizeItemKey(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT)?.let { if (it.contains(':')) it else "VANILLA:$it" }
        @JvmStatic fun isExclusiveToAnyMenu(plugin: SellGUIMain, item: ItemStack?): Boolean = getMenuIds(plugin).any { load(plugin, it)?.let { config -> config.isExclusiveItems() && config.matchesAllowedItem(item) } == true }
        private fun loadItemSet(section: ConfigurationSection, vararg paths: String): Set<String> = paths.flatMap { section.getStringList(it) }.mapNotNull(::normalizeItemKey).toSet()
    }
}
