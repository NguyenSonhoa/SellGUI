package me.aov.sellgui

import me.aov.sellgui.api.PlayerSellItemsEvent
import me.aov.sellgui.api.SellGUIEvents
import me.aov.sellgui.api.SellGUIItemsSoldEvent
import me.aov.sellgui.api.SellGUIPriceProvider
import me.aov.sellgui.api.SoldItem
import me.aov.sellgui.commands.SellCommand
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource

class SaleRegressionTest : SellGUITestFixture() {
    private val provider = RecordingPriceProvider()
    private val observer = SaleObserver()

    @BeforeEach
    fun registerIntegrations() {
        plugin.config.set("prices.calculation-method", "addon")
        plugin.getSellGUIAPI().registerPriceProvider(provider)
        server.pluginManager.registerEvents(observer, plugin)
    }

    @Test
    fun `sold event dispatch uses its static handler list without recursion`() {
        val player = server.addPlayer()
        val event = SellGUIItemsSoldEvent(player, 20.0)
        assertSame(SellGUIItemsSoldEvent.getHandlerList(), event.handlers)
        server.pluginManager.callEvent(event)
        assertEquals(listOf(20.0), observer.totals)
    }

    @Test
    fun `GUI sale reaches both event listeners and the dynamic price provider once`() {
        val player = server.addPlayer()
        assertTrue(server.dispatchCommand(player, "sellgui"))
        val gui = requireNotNull(SellCommand.getSellGUI(player))
        gui.getMenu().setItem(10, ItemStack(Material.STONE, 8))
        fun click() = server.pluginManager.callEvent(InventoryClickEvent(
            player.openInventory, InventoryType.SlotType.CONTAINER, 49,
            ClickType.LEFT, InventoryAction.PICKUP_ALL,
        ))
        click()
        assertTrue(gui.isConfirmMode())
        click()
        assertEquals(listOf(20.0), deposits)
        assertNull(gui.getMenu().getItem(10))
        assertEquals(listOf(20.0), observer.totals)
        assertEquals(listOf(20.0), observer.guiTotals)
        assertEquals(1, provider.sales.size)
        assertEquals(8, provider.sales.single().single().getAmount())
        assertEquals(20.0, provider.sales.single().single().getTotalPrice())
        assertSame(SellGUIEvents.getHandlerList(), observer.guiEvents.single().handlers)
    }

    @Test
    fun `autosell records the completed payout and notifies listeners and providers`() {
        val player = server.addPlayer()
        val item = ItemStack(Material.STONE, 8)
        item.itemMeta = item.itemMeta.also { it.setDisplayName("&aOre|Crate\nNot a second log row") }
        player.inventory.setItem(0, item)
        enableAutosell(player)
        plugin.autosellManager.processAutosell(player)
        server.scheduler.performOneTick()
        assertEquals(listOf(20.0), deposits)
        assertNull(player.inventory.getItem(0))
        val row = logRows().single()
        val fields = row.split('|')
        assertEquals(8, fields.size)
        assertTrue(fields[0].startsWith("[AUTOSELL] VANILLA"))
        assertEquals("VANILLA:STONE", fields[1])
        assertEquals("8", fields[3])
        assertEquals("2.50", fields[4])
        assertEquals("20.00", fields[5])
        assertEquals(player.name, fields[6])
        assertTrue(fields[7].matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
        assertEquals(listOf(20.0), observer.totals)
        assertEquals(1, provider.sales.size)
        assertEquals(20.0, provider.sales.single().single().getTotalPrice())
    }

    @Test
    fun `successive autosell batches create separate log lines`() {
        val player = server.addPlayer()
        enableAutosell(player)
        for (amount in listOf(2, 3)) {
            player.inventory.setItem(0, ItemStack(Material.STONE, amount))
            plugin.autosellManager.processAutosell(player)
            server.scheduler.performOneTick()
        }
        assertEquals(listOf(5.0, 7.5), deposits)
        assertEquals(listOf("5.00", "7.50"), logRows().map { it.split('|')[5] })
    }

    @ParameterizedTest
    @ValueSource(strings = ["logging.enabled", "log-transactions"])
    fun `either logging switch can disable autosell transaction records`(path: String) {
        val player = server.addPlayer()
        plugin.config.set(path, false)
        player.inventory.setItem(0, ItemStack(Material.STONE, 4))
        enableAutosell(player)
        plugin.autosellManager.processAutosell(player)
        server.scheduler.performOneTick()
        assertEquals(listOf(10.0), deposits)
        assertTrue(logRows().isEmpty())
        assertEquals(1, provider.sales.size)
    }

    @Test
    fun `disabled or exclusive items are not sold or logged`() {
        val player = server.addPlayer()
        player.inventory.setItem(0, ItemStack(Material.STONE, 4))
        plugin.autosellManager.processAutosell(player)
        server.scheduler.performOneTick()
        assertTrue(deposits.isEmpty())
        assertEquals(4, player.inventory.getItem(0)?.amount)
        plugin.autosellManager.setGlobalAutosellEnabled(player.uniqueId, true)
        plugin.autosellManager.processAutosell(player)
        server.scheduler.performOneTick()
        assertTrue(deposits.isEmpty())
        assertEquals(4, player.inventory.getItem(0)?.amount)
        enableAutosell(player)
        plugin.configManager.guiConfig.set("sell_menus.fishing.item-filter.allowed-items", listOf("STONE"))
        plugin.configManager.guiConfig.set("sell_menus.fishing.item-filter.exclusive", true)
        plugin.autosellManager.processAutosell(player)
        server.scheduler.performOneTick()
        assertTrue(deposits.isEmpty())
        assertEquals(4, player.inventory.getItem(0)?.amount)
        assertTrue(logRows().isEmpty())
        assertTrue(provider.sales.isEmpty())
    }

    @Test
    fun `rejected autosell payout preserves inventory and produces no successful log or callback`() {
        val player = server.addPlayer()
        player.inventory.setItem(0, ItemStack(Material.STONE, 4))
        enableAutosell(player)
        rejectDeposits = true
        plugin.autosellManager.processAutosell(player)
        server.scheduler.performOneTick()
        assertEquals(4, player.inventory.getItem(0)?.amount)
        assertTrue(deposits.isEmpty())
        assertTrue(logRows().isEmpty())
        assertTrue(observer.totals.isEmpty())
        assertTrue(provider.sales.isEmpty())
    }

    @Test
    fun `sellall logs separate completed transactions and delivers the sale event`() {
        val player = server.addPlayer()
        for (amount in listOf(2, 3)) {
            player.inventory.setItem(0, ItemStack(Material.STONE, amount))
            assertTrue(server.dispatchCommand(player, "sellall confirm"))
        }
        assertEquals(listOf(5.0, 7.5), deposits)
        assertEquals(listOf(5.0, 7.5), observer.totals)
        assertEquals(listOf("5.00", "7.50"), logRows().map { it.split('|')[5] })
        assertEquals(2, provider.sales.size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["gui", "sellall"])
    fun `manual sale can retry after a rejected payout without losing items or paying twice`(mode: String) {
        val player = server.addPlayer()
        val gui = if (mode == "gui") openGui(player) else null
        val inventory = gui?.getMenu() ?: player.inventory
        val slot = if (gui == null) 0 else 10
        inventory.setItem(slot, ItemStack(Material.STONE, 4))
        fun sell() {
            if (gui != null) gui.sellItems(inventory) else server.dispatchCommand(player, "sellall confirm")
        }
        rejectDeposits = true
        sell()
        assertEquals(4, inventory.getItem(slot)?.amount)
        assertTrue(deposits.isEmpty())
        assertTrue(logRows().isEmpty())
        assertTrue(observer.totals.isEmpty())
        assertTrue(provider.sales.isEmpty())
        rejectDeposits = false
        sell()
        sell()
        assertEquals(listOf(10.0), deposits)
        assertNull(inventory.getItem(slot))
        assertEquals(1, logRows().size)
        assertEquals(listOf(10.0), observer.totals)
        assertEquals(1, provider.sales.size)
    }

    @ParameterizedTest
    @CsvSource("gui,logging.enabled", "gui,log-transactions", "sellall,logging.enabled", "sellall,log-transactions")
    fun `manual sale respects both transaction logging switches`(mode: String, path: String) {
        val player = server.addPlayer()
        plugin.config.set(path, false)
        if (mode == "gui") {
            val gui = openGui(player)
            gui.getMenu().setItem(10, ItemStack(Material.STONE, 4))
            gui.sellItems(gui.getMenu())
        } else {
            player.inventory.setItem(0, ItemStack(Material.STONE, 4))
            server.dispatchCommand(player, "sellall confirm")
        }
        assertEquals(listOf(10.0), deposits)
        assertTrue(logRows().isEmpty())
        assertEquals(1, provider.sales.size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["gui", "sellall", "autosell"])
    fun `logs and addon callbacks use exactly the same dynamic quote as the payout`(mode: String) {
        val player = server.addPlayer()
        provider.priceIncrement = 1.0
        val gui = if (mode == "gui") openGui(player) else null
        val inventory = gui?.getMenu() ?: player.inventory
        inventory.setItem(if (gui == null) 0 else 10, ItemStack(Material.STONE, 4))
        // Keep two distinct stacks when the join-time inventory normalizer runs.
        val secondStack = ItemStack(Material.STONE, 3)
        secondStack.itemMeta = secondStack.itemMeta.also { it.setDisplayName("Second batch") }
        inventory.setItem(if (gui == null) 1 else 11, secondStack)
        when (mode) {
            "gui" -> gui!!.sellItems(inventory)
            "sellall" -> server.dispatchCommand(player, "sellall confirm")
            else -> {
                enableAutosell(player)
                plugin.autosellManager.processAutosell(player)
                server.scheduler.performOneTick()
            }
        }
        val paid = deposits.single()
        val rows = logRows().map { it.split('|') }
        assertEquals(2, rows.size)
        assertEquals(paid, rows.sumOf { it[5].toDouble() })
        assertEquals(paid, provider.sales.single().sumOf { it.getTotalPrice() })
        assertEquals(listOf(paid), observer.totals)
        rows.forEach { assertEquals(it[3].toInt() * it[4].toDouble(), it[5].toDouble()) }
    }

    @Test
    fun `disabling autosell cancels a sale already queued for the next tick`() {
        val player = server.addPlayer()
        player.inventory.setItem(0, ItemStack(Material.STONE, 4))
        enableAutosell(player)
        plugin.autosellManager.processAutosell(player)
        plugin.autosellManager.setGlobalAutosellEnabled(player.uniqueId, false)
        server.scheduler.performOneTick()
        assertEquals(4, player.inventory.getItem(0)?.amount)
        assertTrue(deposits.isEmpty())
        assertTrue(logRows().isEmpty())
        assertTrue(provider.sales.isEmpty())
    }

    private fun openGui(player: Player): SellGUI {
        assertTrue(server.dispatchCommand(player, "sellgui"))
        return requireNotNull(SellCommand.getSellGUI(player))
    }

    private fun logRows() = plugin.getLog().readLines().filter { it.startsWith("[") }

    private fun enableAutosell(player: Player) {
        plugin.autosellManager.setGlobalAutosellEnabled(player.uniqueId, true)
        plugin.autosellManager.setAutosellEnabled(player.uniqueId, "VANILLA:STONE", true)
    }

    class SaleObserver : Listener {
        val totals = arrayListOf<Double>()
        val guiTotals = arrayListOf<Double>()
        val guiEvents = arrayListOf<PlayerSellItemsEvent>()
        @EventHandler fun onSold(event: SellGUIItemsSoldEvent) { totals += event.getTotalSoldValue() }
        @EventHandler fun onGuiSold(event: PlayerSellItemsEvent) {
            guiTotals += event.getTotalPrice()
            guiEvents += event
        }
    }

    class RecordingPriceProvider : SellGUIPriceProvider {
        val sales = arrayListOf<List<SoldItem>>()
        var priceIncrement = 0.0
        private var nextPrice = 2.5
        override fun getName() = "TestDynamicPriceProvider"
        override fun getSellPrice(player: Player, itemStack: ItemStack): Double {
            if (itemStack.type != Material.STONE) return 0.0
            return nextPrice.also { nextPrice += priceIncrement }
        }
        override fun onItemsSold(player: Player, soldItems: List<SoldItem>, totalPrice: Double) { sales += soldItems }
    }
}
