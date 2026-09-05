package me.aov.sellgui

import me.aov.sellgui.api.PlayerSellItemsEvent
import me.aov.sellgui.api.SellGUIItemsSoldEvent
import me.aov.sellgui.api.SoldItem
import me.aov.sellgui.commands.SellCommand
import me.aov.sellgui.gui.SellMenuConfig
import me.aov.sellgui.handlers.SoundHandler
import me.aov.sellgui.managers.ItemNBTManager
import me.aov.sellgui.managers.PriceManager
import me.aov.sellgui.utils.ColorUtils
import me.aov.sellgui.utils.ItemIdentifier
import me.aov.sellgui.utils.ItemUtils
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.ShulkerBox
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.persistence.PersistentDataType
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date

class SellGUI @JvmOverloads constructor(private val main: SellGUIMain, private val player: Player, itemNBTManager: ItemNBTManager, menuId: String = SellMenuConfig.DEFAULT_MENU_ID) : Listener, InventoryHolder {
    private val menuConfig = SellMenuConfig.load(main, menuId) ?: throw IllegalArgumentException("Unknown sell menu: $menuId")
    private lateinit var sellItem: ItemStack; private lateinit var filler: ItemStack; private lateinit var confirmItem: ItemStack; private lateinit var noItemsItem: ItemStack
    private lateinit var menu: Inventory; private var sellSlots = listOf<Int>(); private var confirmSlots = listOf<Int>(); private var itemSlots = listOf<Int>(); private var updateTaskId = -1; private var confirmMode = false; private var sold = false
    init { createItems(); createMenu(); addCustomItems(); player.openInventory(menu); startAutoUpdateTask() }
    private fun createMenu() { val size = menuConfig.getInt("size", 54).coerceIn(9, 54).let { ((it + 8) / 9) * 9 }; sellSlots = menuConfig.getIntegerList("positions.sell_button").ifEmpty { listOf(minOf(49, size - 1)) }; confirmSlots = menuConfig.getIntegerList("positions.confirm_button").ifEmpty { sellSlots }; itemSlots = menuConfig.getIntegerList("positions.item_slots"); menu = Bukkit.createInventory(this, size, color(menuConfig.getString("title", "&6&lSell GUI").orEmpty())); menuConfig.getIntegerList("positions.filler_slots").filter(::isValidSlot).forEach { menu.setItem(it, filler) }; addSellButton() }
    private fun createItems() {
        sellItem = guiItem("items.sell_button", Material.EMERALD, "&a&lSell Items").also { tagGui(it, "sell"); if (menuConfig.getBoolean("items.sell_button.glow", true)) glow(it) }
        filler = guiItem("items.filler", Material.GRAY_STAINED_GLASS_PANE, " ").also(::tagGui)
        noItemsItem = guiItem("items.no_items", Material.BARRIER, "&cNo items to sell!").also(::tagGui)
    }
    private fun guiItem(path: String, fallback: Material, fallbackName: String): ItemStack = ItemStack(Material.matchMaterial(menuConfig.getString("$path.material", fallback.name).orEmpty()) ?: fallback).also { item -> item.itemMeta?.let { meta -> meta.setDisplayName(color(menuConfig.getString("$path.name", fallbackName).orEmpty())); meta.lore = color(menuConfig.getStringList("$path.lore")); menuConfig.getInt("$path.custom-model-data", 0).takeIf { it > 0 }?.let(meta::setCustomModelData); item.itemMeta = meta } }
    private fun tagGui(item: ItemStack, action: String? = null) { item.itemMeta?.let { meta -> meta.persistentDataContainer.set(NamespacedKey(main, "sellgui"), PersistentDataType.BYTE, 1); meta.persistentDataContainer.set(NamespacedKey(main, "sellgui-menu"), PersistentDataType.STRING, menuConfig.getId()); action?.let { meta.persistentDataContainer.set(NamespacedKey(main, "guiAction"), PersistentDataType.STRING, it) }; item.itemMeta = meta } }
    private fun glow(item: ItemStack) { item.itemMeta?.let { meta -> meta.addEnchant(Enchantment.INFINITY, 1, false); meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); item.itemMeta = meta } }
    fun addSellButton() { sellSlots.filter(::isValidSlot).forEach { menu.setItem(it, sellItem) }; makeConfirmItem() }
    private fun addCustomItems() { val config = main.getCustomMenuItemsConfig() ?: return; config.getKeys(false).forEach { path ->
        if (!config.contains("$path.slot")) return@forEach; val menus = config.getStringList("$path.menus"); if (menus.isNotEmpty() && menus.none { SellMenuConfig.normalizeMenuId(it) == menuConfig.getId() }) return@forEach
        val slot = config.getInt("$path.slot"); if (!isValidSlot(slot)) return@forEach; if (config.getBoolean("$path.disabled")) { menu.setItem(slot, filler); return@forEach }
        val item = ItemStack(Material.matchMaterial(config.getString("$path.material", "STONE").orEmpty()) ?: Material.STONE); item.itemMeta?.let { meta ->
            config.getString("$path.name", "")?.takeIf(String::isNotEmpty)?.let { meta.setDisplayName(color(it)) }; meta.lore = color(config.getStringList("$path.lore")); config.getInt("$path.custom-model-data", 0).takeIf { it > 0 }?.let(meta::setCustomModelData); if (config.getBoolean("$path.glimmer")) { meta.addEnchant(Enchantment.INFINITY, 1, false); meta.addItemFlags(ItemFlag.HIDE_ENCHANTS) }; ItemUtils.applyModernComponents(meta, config.getConfigurationSection(path)); meta.persistentDataContainer.set(NamespacedKey(main, "custom-menu-item"), PersistentDataType.STRING, config.getStringList("$path.commands").joinToString(";") { it.replace("%player%", player.name) }); meta.persistentDataContainer.set(NamespacedKey(main, "custom-menu-item-sender"), PersistentDataType.STRING, config.getString("$path.sender", "console").orEmpty().lowercase()); meta.persistentDataContainer.set(NamespacedKey(main, "custom-menu-item-close-menu"), PersistentDataType.BYTE, if (config.getBoolean("$path.close-menu", false)) 1 else 0); item.itemMeta = meta }; menu.setItem(slot, item)
    } }
    private fun startAutoUpdateTask() { if (updateTaskId != -1) main.server.scheduler.cancelTask(updateTaskId); updateTaskId = main.server.scheduler.runTaskTimer(main, Runnable { if (player.isOnline && !confirmMode) { returnInvalidItems(); updateSellItemTotal() } else if (!player.isOnline) cleanup() }, 0L, main.config.getLong("performance.gui-update-interval", 20)).taskId }
    fun updateSellItemTotal() { if (confirmMode) return; val total = getTotal(menu); val button = if (total > 0) sellItem.clone() else noItemsItem.clone(); if (total > 0) button.itemMeta?.let { meta -> meta.lore = color((meta.lore ?: emptyList()).map { if (it.contains("%total%") || (ChatColor.stripColor(it) ?: "").trim().lowercase().startsWith("total")) it.replace("%total%", total.format()).replace("%menu%", menuConfig.getDisplayName()) else it }); button.itemMeta = meta }; sellSlots.filter(::isValidSlot).forEach { menu.setItem(it, button) } }
    fun updateButtonState() { returnInvalidItems(); if (getTotal(menu) > 0) { sellSlots.filter(::isValidSlot).forEach { menu.setItem(it, null) }; makeConfirmItem(); confirmSlots.filter(::isValidSlot).forEach { menu.setItem(it, confirmItem) }; confirmMode = true } else { confirmMode = false; updateSellItemTotal() } }
    fun cleanup() { if (updateTaskId != -1) { main.server.scheduler.cancelTask(updateTaskId); updateTaskId = -1 } }
    fun makeConfirmItem() { confirmItem = guiItem("items.confirm_button", Material.GREEN_CONCRETE, "&a&lConfirm Sale").also { item -> item.itemMeta?.let { meta -> if (menuConfig.getBoolean("items.confirm_button.glow", true)) { meta.addEnchant(Enchantment.POWER, 1, false); meta.addItemFlags(ItemFlag.HIDE_ENCHANTS) }; val total = getTotal(menu); val lore = menuConfig.getStringList("items.confirm_button.lore").map { it.replace("%total%", total.format()).replace("%menu%", menuConfig.getDisplayName()) }.toMutableList(); if (hasUnevaluatedItems()) lore += listOf(" ", "&cSome items need evaluation", "&7Use /sellgui evaluate"); meta.lore = color(lore); item.itemMeta = meta }; tagGui(item, "confirm") } }
    fun hasUnevaluatedItems(): Boolean = main.getRandomPriceManager()?.let { manager -> (0 until menu.size).any { slot -> menu.getItem(slot)?.let { isSellable(slot, it) && manager.requiresEvaluation(it) && !manager.isEvaluated(it) } == true } } ?: false
    fun setConfirmMode() = updateButtonState()
    fun setSellItem() { confirmSlots.filter(::isValidSlot).forEach { menu.setItem(it, null) }; confirmMode = false; updateSellItemTotal() }
    fun getPrice(item: ItemStack?, player: Player?): Double { if (item == null || item.type == Material.AIR || !canAcceptItem(item)) return 0.0; val single = item.clone(); var price = single.itemMeta?.persistentDataContainer?.get(NamespacedKey(main, "current_price"), PersistentDataType.DOUBLE) ?: 0.0; if (price == 0.0) price = (main.getPriceManager() ?: PriceManager(main)).getItemPriceWithPlayer(single, player); if (main.getRandomPriceManager()?.canBeSold(single) == false) return 0.0; val contents = ((item.itemMeta as? BlockStateMeta)?.blockState as? ShulkerBox)?.inventory?.contents?.filterNotNull()?.filter { !it.type.isAir }?.sumOf { getPrice(it, player) * it.amount } ?: 0.0; return applyBonuses(player, BigDecimal.valueOf(price + contents)).toDouble() }
    private fun applyBonuses(player: Player?, price: BigDecimal): BigDecimal { player ?: return price; if (price <= BigDecimal.ZERO) return price; val bonus = player.effectivePermissions.filter { it.value && it.permission.startsWith("sellgui.bonus.") && (!player.isOp || it.attachment != null) }.fold(BigDecimal.ZERO) { total, info -> total + (info.permission.removePrefix("sellgui.bonus.").toBigDecimalOrNull() ?: BigDecimal.ZERO) }; return if (bonus > BigDecimal.ZERO) price * (BigDecimal.ONE + bonus.divide(BigDecimal("100"))) else price }
    fun getTotal(inventory: Inventory): Double = (0 until inventory.size).sumOf { slot -> val item = inventory.getItem(slot); if (item == null || !isSellable(slot, item) || main.getRandomPriceManager()?.canBeSold(item) == false || (main.getRandomPriceManager()?.hasRandomPrice(item) == true && main.getRandomPriceManager()?.isEvaluated(item) == false)) 0.0 else getPrice(item, player).let { if (isShulker(item)) it else it * item.amount } }
    fun sellItems(inventory: Inventory) { returnInvalidItems(); if (hasUnevaluatedItems()) { player.sendMessage(color(main.getMessagesConfig().getString("sell.evaluation_required", "&cSome items must be evaluated before selling.").orEmpty())); setSellItem(); return }; val total = getTotal(inventory); if (total <= 0) { player.sendMessage(color(main.getMessagesConfig().getString("sell.no_items", "&cNothing to sell!").orEmpty())); setSellItem(); return }; main.getEcon()?.depositPlayer(player, total); sold = true; val soldItems = arrayListOf<SoldItem>(); val eventItems = arrayListOf<ItemStack>(); for (slot in 0 until inventory.size) { val item = inventory.getItem(slot) ?: continue; val price = getPrice(item, player); if (!isSellable(slot, item) || price <= 0) continue; if (main.config.getBoolean("logging.enabled")) logSell(item); val amount = item.amount; val clone = item.clone(); soldItems += SoldItem(clone, amount, price, if (isShulker(item)) price else price * amount); eventItems += clone; inventory.setItem(slot, null) }; if (soldItems.isNotEmpty()) { Bukkit.getPluginManager().callEvent(PlayerSellItemsEvent(player, this, eventItems, total)); Bukkit.getPluginManager().callEvent(SellGUIItemsSoldEvent(player, total)); main.getSellGUIAPI()?.notifyItemsSold(player, soldItems, total) }; if (main.config.getBoolean("general.close-after-sell")) { player.closeInventory(); SellCommand.getSellGUIs().remove(this) } else setSellItem(); player.sendMessage(color(main.getMessagesConfig().getString("sell.sold_success", "&aSold items for &e$%total%!").orEmpty().replace("%total%", total.format()).replace("%menu%", menuConfig.getDisplayName()))); SoundHandler.playConfigSound(player, "sounds.feedback.success") }
    fun returnInvalidItems() { var returned = 0; for (slot in 0 until menu.size) { val item = menu.getItem(slot) ?: continue; if (item.type == Material.AIR || isGuiItem(item) || isCustomItem(item) || (isItemSlot(slot) && canAcceptItem(item))) continue; menu.setItem(slot, null); returned += item.amount; player.inventory.addItem(item).values.forEach { player.world.dropItem(player.location, it) } }; if (returned > 0) player.sendMessage(color(main.getMessagesConfig().getString("sell.item_not_allowed_in_menu", "&cSome items cannot be sold in %menu% and were returned.").orEmpty().replace("%count%", returned.toString()).replace("%menu%", menuConfig.getDisplayName()))) }
    fun canAcceptItem(item: ItemStack): Boolean = menuConfig.allowsItem(item)
    private fun isSellable(slot: Int, item: ItemStack): Boolean = item.type != Material.AIR && isItemSlot(slot) && !isGuiItem(item) && !isCustomItem(item) && canAcceptItem(item)
    private fun isItemSlot(slot: Int): Boolean = itemSlots.isEmpty() || slot in itemSlots
    private fun isValidSlot(slot: Int): Boolean = ::menu.isInitialized && slot in 0 until menu.size
    private fun isGuiItem(item: ItemStack): Boolean = item.hasItemMeta() && item.itemMeta.persistentDataContainer.has(NamespacedKey(main, "sellgui"), PersistentDataType.BYTE)
    private fun isCustomItem(item: ItemStack): Boolean = item.hasItemMeta() && item.itemMeta.persistentDataContainer.has(NamespacedKey(main, "custom-menu-item"), PersistentDataType.STRING)
    private fun isShulker(item: ItemStack): Boolean = item.type.name.endsWith("SHULKER_BOX")
    fun logSell(item: ItemStack?) { item ?: return; try { BufferedWriter(FileWriter(main.getLog(), true)).use { it.append("[SELLGUI:${menuConfig.getId()}] ${ItemIdentifier.getItemType(item).name}|${ItemIdentifier.getItemIdentifier(item)}|${ChatColor.stripColor(ItemIdentifier.getItemDisplayName(item))}|${item.amount}|${getPrice(item, player).format()}|${(if (isShulker(item)) getPrice(item, player) else getPrice(item, player) * item.amount).format()}|${player.name}|${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}").appendLine() } } catch (exception: IOException) { main.logger.severe("Failed to write to sell log: ${exception.message}") } }
    fun getConfirmItem(): ItemStack = confirmItem; fun getPlayer(): Player = player; fun getSellItem(): ItemStack = sellItem; fun getMenu(): Inventory = menu; override fun getInventory(): Inventory = menu; fun getMain(): SellGUIMain = main; fun getMenuId(): String = menuConfig.getId(); fun getMenuConfig(): SellMenuConfig = menuConfig; fun isConfirmMode(): Boolean = confirmMode; fun isSold(): Boolean = sold; fun setSold(value: Boolean) { sold = value }; fun color(value: String?): String = ColorUtils.color(main.setPlaceholders(player, value.orEmpty())).orEmpty(); fun color(lore: List<String>?): List<String> = lore?.map(::color) ?: emptyList(); private fun Double.format() = "%.2f".format(this)
}
