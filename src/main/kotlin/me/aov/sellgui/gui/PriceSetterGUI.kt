package me.aov.sellgui.gui

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.managers.PriceManager
import me.aov.sellgui.utils.ColorUtils
import me.aov.sellgui.utils.ItemIdentifier
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class PriceSetterGUI(private val main: SellGUIMain, val player: Player) : InventoryHolder {
    private val priceManager = PriceManager(main)
    private val inventory: Inventory = Bukkit.createInventory(
        this, 45,
        ColorUtils.color(main.setPlaceholders(player, main.configManager.guiConfig.getString("price_setter_gui.title", "&6&lPrice Setter").orEmpty())).orEmpty()
    )

    init {
        setupGUI()
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory = inventory

    private fun setupGUI() {
        val filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null)
        for (slot in 0 until inventory.size) inventory.setItem(slot, filler)
        inventory.setItem(ITEM_SLOT, null)
        inventory.setItem(PRICE_INPUT_SLOT, null)
        inventory.setItem(
            INFO_SLOT,
            createItem(Material.BOOK, color("&e&lHow to use:"), listOf("&71. Drag an item to the center slot", "&72. Use /sellguiprice <price> to set price", "&73. Click Save to confirm", "&7", "&eSupports: Vanilla, MMOItems, Nexo").map(::color))
        )
        setupControlButtons()
    }

    private fun setupControlButtons() {
        inventory.setItem(SAVE_BUTTON_SLOT, createItem(Material.GREEN_CONCRETE, color("&a&lSave Price"), listOf(color("&7Click to save the current price"), color("&7for the item in the center slot"))).also { addPersistentData(it, "price-setter-action", "save") })
        inventory.setItem(CANCEL_BUTTON_SLOT, createItem(Material.RED_CONCRETE, color("&c&lCancel"), listOf(color("&7Click to close without saving"))).also { addPersistentData(it, "price-setter-action", "cancel") })
        inventory.setItem(DELETE_BUTTON_SLOT, createItem(Material.BARRIER, color("&4&lDelete Price"), listOf(color("&7Click to remove the price"), color("&7for the item in the center slot"))).also { addPersistentData(it, "price-setter-action", "delete") })
        inventory.setItem(CHAT_INPUT_SLOT, createItem(Material.WRITABLE_BOOK, color("&b&lSet Price via Chat"), listOf(color("&7Click to close GUI and type price in chat"), color("&7GUI will reopen automatically after setting"), color("&7Type 'cancel' to cancel input"))).also { addPersistentData(it, "price-setter-action", "chat") })
    }

    fun updateItemInfo() {
        val item = inventory.getItem(ITEM_SLOT)
        if (item == null || item.type == Material.AIR) {
            inventory.setItem(PRICE_INPUT_SLOT, null)
            return
        }
        ItemIdentifier.debugItemNBT(item)
        val price = priceManager.getItemPrice(item)
        val type = ItemIdentifier.getItemType(item)
        val identifier = ItemIdentifier.getItemIdentifier(item) ?: "Unknown"
        val lore = listOf(
            color("&7Item: &f${ItemIdentifier.getItemDisplayName(item)}"),
            color("&7Type: &f${type.name}"),
            color("&7Identifier: &f$identifier"),
            color("&7"),
            color("&7Current Price: &e$${"%.2f".format(price)}"),
            color("&7"),
            color("&eUse: &f/sellguiprice <price>"),
            color("&eto set a new price")
        )
        inventory.setItem(PRICE_INPUT_SLOT, createItem(Material.GOLD_INGOT, color("&6&lPrice Information"), lore))
    }

    fun savePrice(price: Double): Boolean {
        val item = inventory.getItem(ITEM_SLOT)
        if (item == null || item.type == Material.AIR) {
            player.sendMessage(color("&cNo item found to set price for!"))
            return false
        }
        if (price < 0) {
            player.sendMessage(color("&cPrice cannot be negative!"))
            return false
        }
        if (!priceManager.setItemPrice(item, price)) {
            player.sendMessage(color("&cFailed to set price! Check console for errors."))
            return false
        }
        player.sendMessage(color("&aSuccessfully set price for &f${ItemIdentifier.getItemDisplayName(item)} &ato &e$${"%.2f".format(price)}"))
        updateItemInfo()
        return true
    }

    fun deletePrice(): Boolean {
        val item = inventory.getItem(ITEM_SLOT)
        if (item == null || item.type == Material.AIR) {
            player.sendMessage(color("&cNo item found to delete price for!"))
            return false
        }
        if (!priceManager.removeItemPrice(item)) {
            player.sendMessage(color("&cFailed to remove price! Check console for errors."))
            return false
        }
        player.sendMessage(color("&aSuccessfully removed price for &f${ItemIdentifier.getItemDisplayName(item)}"))
        updateItemInfo()
        return true
    }

    private fun createItem(material: Material, name: String, lore: List<String>?): ItemStack = ItemStack(material).also { item ->
        item.itemMeta?.let { meta ->
            meta.setDisplayName(name)
            meta.lore = lore
            item.itemMeta = meta
        }
    }

    private fun addPersistentData(item: ItemStack, key: String, value: String) {
        item.itemMeta?.let { meta ->
            meta.persistentDataContainer.set(NamespacedKey(main, key), PersistentDataType.STRING, value)
            item.itemMeta = meta
        }
    }

    private fun color(value: String): String = ColorUtils.color(value).orEmpty()

    companion object {
        private const val ITEM_SLOT = 13
        private const val PRICE_INPUT_SLOT = 22
        private const val SAVE_BUTTON_SLOT = 29
        private const val CANCEL_BUTTON_SLOT = 33
        private const val DELETE_BUTTON_SLOT = 31
        private const val CHAT_INPUT_SLOT = 40
        private const val INFO_SLOT = 4
        @JvmStatic fun getItemSlot(): Int = ITEM_SLOT
        @JvmStatic fun getPriceInputSlot(): Int = PRICE_INPUT_SLOT
    }
}
