package me.aov.sellgui.gui

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.listeners.AutosellSearchListener
import me.aov.sellgui.utils.ColorUtils
import me.aov.sellgui.utils.ItemIdentifier
import net.brcdev.shopgui.ShopGuiPlusApi
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

class AutosellSettingsGUI @JvmOverloads constructor(private val plugin: SellGUIMain, private val player: Player, search: String? = null) : InventoryHolder, Listener {
    private val inventory: Inventory
    private var currentPage = 0
    private var searchQuery = search?.takeUnless { it.equals("clear", true) }
    private val identifiers = arrayListOf<String>()
    private val controlTags = hashMapOf<Int, String>()
    private val itemTags = hashMapOf<Int, String>()

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        loadPricedItems()
        inventory = Bukkit.createInventory(this, plugin.configManager.autosellGuiSize, color(plugin.setPlaceholders(player, plugin.configManager.autosellGuiTitle)))
        setupGUI()
    }

    private fun loadPricedItems() {
        if (plugin.config.getString("prices.calculation-method", "auto").equals("shopguiplus", true) && plugin.hasShopGUIPlus) {
            try {
                ShopGuiPlusApi.getPlugin().shopManager.shops?.forEach { shop -> shop.shopItems?.forEach { shopItem ->
                    shopItem.item?.takeIf { !SellMenuConfig.isExclusiveToAnyMenu(plugin, it) }?.let { item -> ItemIdentifier.getItemIdentifier(item)?.takeIf { it !in identifiers }?.let(identifiers::add) }
                } }
            } catch (exception: Exception) { plugin.logger.warning("Error loading ShopGUI+ items for Autosell GUI: ${exception.message}") }
        } else {
            plugin.getPriceManager().getAllPricedItems().keys.forEach { id -> ItemIdentifier.getItemStackFromIdentifier(id)?.takeUnless { SellMenuConfig.isExclusiveToAnyMenu(plugin, it) }?.let { identifiers += id } }
        }
    }

    private fun setupGUI() {
        inventory.clear(); controlTags.clear(); itemTags.clear()
        val config = plugin.configManager; val root = config.autosellSettingsGUIConfig
        val displayed = identifiers.filter { id -> searchQuery.isNullOrEmpty() || ItemIdentifier.getItemStackFromIdentifier(id)?.let { ItemIdentifier.getItemDisplayName(it).contains(searchQuery!!, true) } == true }
        config.autosellGuiFillerItem?.let { section -> createItemFromConfig(section, " ")?.let { filler -> root?.getIntegerList("positions.filler_slots")?.forEach { inventory.setItem(it, filler) } } }
        val global = plugin.autosellManager.isGlobalAutosellEnabled(player.uniqueId)
        val toggleSection = if (global) config.autosellGuiDisableAllButton else config.autosellGuiEnableAllButton
        val toggleSlots = root?.getIntegerList(if (global) "positions.disable_all_button" else "positions.enable_all_button").orEmpty()
        toggleSection?.let { section -> createItemFromConfig(section, "Global Autosell Toggle")?.let { item -> toggleSlots.forEach { slot -> inventory.setItem(slot, item); controlTags[slot] = "global_toggle" } } }
        config.autosellGuiSearchButton?.let { section -> createItemFromConfig(section, "Search")?.let { item -> root?.getIntegerList("positions.search_button")?.forEach { slot -> inventory.setItem(slot, item); controlTags[slot] = "search" } } }
        if (displayed.isEmpty()) {
            config.autosellGuiNoPricedItems?.let { createItemFromConfig(it, "&c&l❌ No Priced Items") }?.let { inventory.setItem(22, it) }
            addPaginationControls(0); return
        }
        val slots = root?.getIntegerList("positions.item_slots").orEmpty()
        if (slots.isEmpty()) return
        val start = currentPage * slots.size; val end = minOf(start + slots.size, displayed.size)
        displayed.subList(start, end).forEachIndexed { index, id ->
            val original = ItemIdentifier.getItemStackFromIdentifier(id) ?: return@forEachIndexed
            val enabled = plugin.autosellManager.isAutosellEnabled(player.uniqueId, id)
            val section = if (enabled) config.autosellGuiEnabledAutosellItem else config.autosellGuiDisabledAutosellItem
            section?.let { createItemFromConfig(it, ItemIdentifier.getItemDisplayName(original)) }?.let { display ->
                val price = plugin.getPriceManager().getItemPriceWithPlayer(original, player)
                display.itemMeta?.let { meta ->
                    val name = ItemIdentifier.getItemDisplayName(original); val formatted = java.lang.String.format(config.moneyFormat, price)
                    if (meta.hasDisplayName()) meta.setDisplayName(meta.displayName.replace("%item_name%", name).replace("%price%", formatted))
                    meta.lore = (meta.lore ?: emptyList()).map { it.replace("%item_name%", name).replace("%price%", formatted) }
                    if (!searchQuery.isNullOrEmpty()) { meta.addEnchant(Enchantment.UNBREAKING, 1, true); meta.addItemFlags(ItemFlag.HIDE_ENCHANTS) }
                    display.itemMeta = meta
                }
                val slot = slots[index]; inventory.setItem(slot, display); itemTags[slot] = id
            }
        }
        addPaginationControls(displayed.size)
    }

    private fun addPaginationControls(total: Int) {
        val config = plugin.configManager; val root = config.autosellSettingsGUIConfig; val perPage = root?.getIntegerList("positions.item_slots")?.size ?: 0
        if (currentPage > 0) config.autosellGuiPreviousPageButton?.let { createItemFromConfig(it, "&aPrevious Page") }?.let { item -> root?.getIntegerList("positions.previous_page_button")?.forEach { slot -> inventory.setItem(slot, item); controlTags[slot] = "previous" } }
        if (perPage > 0 && (currentPage + 1) * perPage < total) config.autosellGuiNextPageButton?.let { createItemFromConfig(it, "&aNext Page") }?.let { item -> root?.getIntegerList("positions.next_page_button")?.forEach { slot -> inventory.setItem(slot, item); controlTags[slot] = "next" } }
    }

    private fun createItemFromConfig(config: ConfigurationSection, defaultName: String): ItemStack? {
        val material = Material.matchMaterial(config.getString("material", "STONE").orEmpty()) ?: Material.STONE
        return ItemStack(material).also { item -> item.itemMeta?.let { meta ->
            meta.setDisplayName(color(config.getString("name", defaultName).orEmpty()))
            meta.lore = config.getStringList("lore").map(::color)
            if (config.contains("custom-model-data")) meta.setCustomModelData(config.getInt("custom-model-data"))
            if (config.getBoolean("glow", false)) { meta.addEnchant(Enchantment.UNBREAKING, 1, true); meta.addItemFlags(ItemFlag.HIDE_ENCHANTS) }
            item.itemMeta = meta
        } }
    }

    override fun getInventory(): Inventory = inventory

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.holder !== this) return
        val clicker = event.whoClicked as? Player ?: return
        event.isCancelled = true
        if (event.currentItem == null || event.currentItem?.type == Material.AIR) return
        val slot = event.rawSlot
        when (controlTags[slot]) {
            "global_toggle" -> {
                val enabled = plugin.autosellManager.isGlobalAutosellEnabled(clicker.uniqueId); plugin.autosellManager.setGlobalAutosellEnabled(clicker.uniqueId, !enabled)
                plugin.configManager.messagesConfig.getString(if (!enabled) "autosell.enable_autosell" else "autosell.disable_autosell")?.let { clicker.sendMessage(color(it)) }; setupGUI()
            }
            "previous" -> if (currentPage > 0) { currentPage--; setupGUI() }
            "next" -> { currentPage++; setupGUI() }
            "search" -> { AutosellSearchListener.addSearchingPlayer(clicker.uniqueId); clicker.closeInventory(); plugin.configManager.messagesConfig.getString("autosell.search_prompt")?.let { clicker.sendMessage(color(it)) } }
            null -> itemTags[slot]?.let { id ->
                val enabled = plugin.autosellManager.isAutosellEnabled(clicker.uniqueId, id); plugin.autosellManager.setAutosellEnabled(clicker.uniqueId, id, !enabled)
                val name = ItemIdentifier.getItemStackFromIdentifier(id)?.let(ItemIdentifier::getItemDisplayName) ?: "Unknown Item"
                plugin.configManager.messagesConfig.getString(if (!enabled) "autosell.enabled" else "autosell.disabled")?.let { clicker.sendMessage(color(it.replace("%item_name%", name))) }; setupGUI()
            }
        }
    }

    private fun color(value: String): String = ColorUtils.color(value).orEmpty()
}
