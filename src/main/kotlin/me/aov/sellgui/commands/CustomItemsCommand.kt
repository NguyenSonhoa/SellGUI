package me.aov.sellgui.commands

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.utils.ColorUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class CustomItemsCommand(main: SellGUIMain) : CommandExecutor {
    init {
        Companion.main = main
        page = 0
        customItems = arrayListOf()
        prices = arrayListOf()
        importStuff()
        makeMenu()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("Players only")
            return true
        }
        if (args.isEmpty()) {
            player.openInventory(menu)
            page = 0
            display()
        } else {
            try {
                player.world.dropItem(player.location, getPriceItem(args[0].toDouble()))
            } catch (_: NullPointerException) {
                player.sendMessage("Error")
            } catch (_: NumberFormatException) {
                player.sendMessage("Try using a number.")
            }
        }
        return true
    }

    private fun importStuff() {
        val plugin = main
        val config = plugin.getCustomItemsConfig()
        if (config.getList("items") == null) config.set("items", customItems)
        if (config.getList("prices") == null) config.set("prices", prices)
        customItems.clear()
        prices.clear()
        customItems += config.getList("items").orEmpty().filterIsInstance<ItemStack>()
        prices += config.getList("prices").orEmpty().filterIsInstance<Number>().map(Number::toDouble)
    }

    private fun makeMenu() {
        menu = Bukkit.createInventory(null, 45, ColorUtils.color("&c&lCustom Items").orEmpty())
        filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE).also { item -> item.itemMeta?.let { meta -> meta.setDisplayName(" "); item.itemMeta = meta } }
        delete = ItemStack(Material.BARRIER).also { item -> item.itemMeta?.let { meta -> meta.setDisplayName(ColorUtils.color("&c&lDelete Item")); item.itemMeta = meta } }
        next = ItemStack(Material.GREEN_WOOL).also { item -> item.itemMeta?.let { meta -> meta.setDisplayName(ColorUtils.color("&2&lNext")); item.itemMeta = meta } }
        back = ItemStack(Material.RED_WOOL).also { item -> item.itemMeta?.let { meta -> meta.setDisplayName(ColorUtils.color("&c&lBack")); item.itemMeta = meta } }
        for (slot in 0..8) menu.setItem(slot, filler)
        for (slot in 18..26) menu.setItem(slot, filler)
        for (slot in 36..44) menu.setItem(slot, filler)
        menu.setItem(9, filler)
        menu.setItem(17, filler)
        menu.setItem(35, filler)
        menu.setItem(27, filler)
    }

    companion object {
        private lateinit var main: SellGUIMain
        private lateinit var menu: Inventory
        private lateinit var customItems: ArrayList<ItemStack>
        private lateinit var prices: ArrayList<Double>
        private var page = 0
        private lateinit var filler: ItemStack
        private lateinit var delete: ItemStack
        private lateinit var next: ItemStack
        private lateinit var back: ItemStack

        private fun display() {
            for (slot in 0..8) menu.setItem(slot, filler)
            for (slot in 10..16) {
                menu.clear(slot)
                menu.clear(slot + 18)
            }
            for (index in 7 * page until 7 + 7 * page) {
                val item = customItems.getOrNull(index) ?: continue
                val slot = getFreeSlot()
                menu.setItem(slot, item)
                menu.setItem(slot - 9, delete)
                menu.setItem(slot + 18, getPriceItem(getPrice(item)))
            }
            menu.setItem(36, if (page > 0) back else filler)
            menu.setItem(44, next)
        }

        private fun doesntHave(item: ItemStack?): Boolean = item != null && customItems.none { it.isSimilar(item) }

        private fun getPriceItem(price: Double): ItemStack = ItemStack(Material.PAPER).also { item ->
            item.itemMeta?.let { meta -> meta.setDisplayName("price: $price"); item.itemMeta = meta }
        }

        private fun getFreeSlot(): Int = (10..17).firstOrNull { menu.getItem(it) == null } ?: -1

        @JvmStatic
        fun addToList() {
            for (slot in 10..16) {
                val item = menu.getItem(slot)
                if (doesntHave(item)) {
                    customItems += item!!
                    val priceItem = menu.getItem(slot + 18)
                    prices += if (isPrice(priceItem)) getPricePrice(priceItem) else 0.0
                }
            }
        }

        @JvmStatic
        fun saveStuff() {
            main.getCustomItemsConfig().set("items", customItems)
            main.getCustomItemsConfig().set("prices", prices)
            main.saveCustom()
        }

        private fun isPrice(item: ItemStack?): Boolean = item?.takeIf { it.type == Material.PAPER }?.itemMeta?.displayName
            ?.let { it.contains("price: ") && getPricePrice(item) != -1.0 } ?: false

        @JvmStatic
        fun getPricePrice(item: ItemStack?): Double = item?.takeIf { it.type == Material.PAPER }?.itemMeta?.displayName
            ?.removePrefix("price: ")?.toDoubleOrNull() ?: -1.0

        @JvmStatic
        fun nextPage() { page++; addToList(); display() }

        @JvmStatic
        fun lastPage() { page--; addToList(); display() }

        @JvmStatic
        fun getPrice(stack: ItemStack?): Double = customItems.indexOfFirst { stack != null && it.isSimilar(stack) }
            .takeIf { it >= 0 }?.let(prices::get) ?: -1.0

        @JvmStatic
        fun removeItem(slot: Int) {
            val selected = menu.getItem(slot)
            val index = customItems.indexOfFirst { selected != null && selected.isSimilar(it) }
            if (index < 0) return
            val item = customItems.removeAt(index)
            menu.remove(item)
            menu.setItem(slot + 18, null)
            menu.setItem(slot - 9, filler)
            prices.removeAt(index)
            saveStuff()
            display()
        }

        @JvmStatic
        fun clickable(item: ItemStack?): Boolean = item != null && !(item.isSimilar(delete) || item.isSimilar(filler) || item.isSimilar(next) || item.isSimilar(back))

        @JvmStatic fun getMenu(): Inventory = menu
        @JvmStatic fun getFiller(): ItemStack = filler
        @JvmStatic fun getDelete(): ItemStack = delete
        @JvmStatic fun getCustomItems(): ArrayList<ItemStack> = customItems
        @JvmStatic fun getPrices(): ArrayList<Double> = prices
        @JvmStatic fun getPage(): Int = page
        @JvmStatic fun getNext(): ItemStack = next
        @JvmStatic fun getBack(): ItemStack = back
    }
}
