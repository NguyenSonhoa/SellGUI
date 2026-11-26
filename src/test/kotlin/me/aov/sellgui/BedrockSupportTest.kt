package me.aov.sellgui

import me.aov.sellgui.commands.SellCommand
import me.aov.sellgui.hooks.BedrockBridge
import me.aov.sellgui.managers.BedrockSupport
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.HandlerList
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.util.UUID

class BedrockSupportTest : SellGUITestFixture() {
    private class TestBedrockBridge : BedrockBridge {
        val players = hashSetOf<UUID>()
        val responses = arrayListOf<(Boolean) -> Unit>()
        val contents = arrayListOf<String>()
        var sendSucceeds = true
        override fun isBedrockPlayer(playerId: UUID): Boolean = playerId in players
        override fun sendConfirmation(playerId: UUID, title: String, content: String, sell: String, cancel: String, response: (Boolean) -> Unit): Boolean {
            contents += content
            responses += response
            return sendSucceeds
        }
    }

    @Test
    fun `Bedrock closes chest and confirms sale exactly once on the server thread`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val player = bedrockPlayer(bridge)
        player.addAttachment(plugin, "sellgui.bonus.10", true)
        val gui = stageItems(plugin, player)
        player.closeInventory()
        assertNull(SellCommand.getSellGUI(player))
        assertEquals(0, player.inventory.all(Material.STONE).size)
        assertTrue(bridge.responses.isEmpty())
        server.scheduler.performTicks(2)
        assertEquals(1, bridge.responses.size)
        assertTrue(bridge.contents.single().contains("22.00"))
        val callback = bridge.responses.single()
        val responseThread = Thread { callback(true); callback(true) }
        responseThread.start()
        responseThread.join()
        assertTrue(deposits.isEmpty())
        server.scheduler.performOneTick()
        assertEquals(listOf(22.0), deposits)
        assertEquals(0, count(player, Material.STONE))
        assertNull(gui.getMenu().getItem(10))
        assertEquals(1L, server.pluginManager.firedEvents.filter { it is me.aov.sellgui.api.PlayerSellItemsEvent }.count())
    }

    @Test
    fun `cancel and stale confirmation return held items only once`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val player = bedrockPlayer(bridge)
        val gui = stageItems(plugin, player)
        player.closeInventory()
        server.scheduler.performTicks(2)
        val callback = bridge.responses.single()
        callback(false)
        callback(true)
        server.scheduler.performOneTick()
        assertEquals(8, count(player, Material.STONE))
        assertNull(gui.getMenu().getItem(10))
        assertTrue(deposits.isEmpty())
    }

    @Test
    fun `confirmation timeout returns items and ignores late acceptance`() {
        val (plugin, bridge) = loadBedrockPlugin()
        plugin.config.set("bedrock.confirmation-timeout-seconds", 1)
        val player = bedrockPlayer(bridge)
        stageItems(plugin, player)
        player.closeInventory()
        server.scheduler.performTicks(20)
        assertEquals(8, count(player, Material.STONE))
        bridge.responses.single()(true)
        server.scheduler.performOneTick()
        assertEquals(8, count(player, Material.STONE))
        assertTrue(deposits.isEmpty())
    }

    @Test
    fun `disconnect returns pending items before player data is saved`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val player = bedrockPlayer(bridge)
        stageItems(plugin, player)
        player.closeInventory()
        server.scheduler.performTicks(2)
        assertTrue(player.disconnect())
        assertEquals(8, count(player, Material.STONE))
        bridge.responses.single()(true)
        server.scheduler.performOneTick()
        assertTrue(deposits.isEmpty())
    }

    @Test
    fun `disconnect while chest is open returns items without sending a form`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val player = bedrockPlayer(bridge)
        stageItems(plugin, player)
        player.disconnect()
        server.scheduler.performTicks(3)
        assertEquals(8, count(player, Material.STONE))
        assertTrue(bridge.responses.isEmpty())
        assertTrue(deposits.isEmpty())
    }

    @Test
    fun `new sell menu and another inventory invalidate old confirmations`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val player = bedrockPlayer(bridge)
        stageItems(plugin, player)
        player.closeInventory()
        server.scheduler.performTicks(2)
        assertTrue(server.dispatchCommand(player, "sell"))
        assertEquals(8, count(player, Material.STONE))
        bridge.responses.first()(true)
        server.scheduler.performOneTick()
        assertTrue(deposits.isEmpty())
        val gui = SellCommand.getSellGUI(player)!!
        player.inventory.clear()
        gui.getMenu().setItem(10, pricedItem(plugin))
        player.closeInventory()
        server.scheduler.performTicks(2)
        player.openInventory(server.createInventory(null, 9))
        assertEquals(8, count(player, Material.STONE))
        bridge.responses.last()(true)
        server.scheduler.performOneTick()
        assertTrue(deposits.isEmpty())
    }

    @Test
    fun `plugin disable returns active and pending chest contents`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val pendingPlayer = bedrockPlayer(bridge)
        val activePlayer = bedrockPlayer(bridge)
        stageItems(plugin, pendingPlayer)
        pendingPlayer.closeInventory()
        server.scheduler.performTicks(2)
        stageItems(plugin, activePlayer)
        server.pluginManager.disablePlugin(plugin)
        assertEquals(8, count(pendingPlayer, Material.STONE))
        assertEquals(8, count(activePlayer, Material.STONE))
        assertTrue(SellCommand.getSellGUIs().isEmpty())
        bridge.responses.single()(true)
        assertTrue(deposits.isEmpty())
    }

    @Test
    fun `failed form delivery returns all held items`() {
        val (plugin, bridge) = loadBedrockPlugin()
        bridge.sendSucceeds = false
        val player = bedrockPlayer(bridge)
        stageItems(plugin, player)
        player.closeInventory()
        server.scheduler.performTicks(2)
        assertEquals(8, count(player, Material.STONE))
        assertTrue(deposits.isEmpty())
    }

    @Test
    fun `changed price requires another confirmation`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val player = bedrockPlayer(bridge)
        val gui = stageItems(plugin, player)
        player.closeInventory()
        server.scheduler.performTicks(2)
        gui.getMenu().setItem(10, pricedItem(plugin, price = 3.0))
        bridge.responses.single()(true)
        server.scheduler.performTicks(3)
        assertTrue(deposits.isEmpty())
        assertEquals(2, bridge.responses.size)
        assertTrue(bridge.contents.last().contains("24.00"))
        bridge.responses.last()(true)
        server.scheduler.performOneTick()
        assertEquals(listOf(24.0), deposits)
    }

    @Test
    fun `revoked use permission cancels an already opened form`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val player = bedrockPlayer(bridge)
        stageItems(plugin, player)
        player.closeInventory()
        server.scheduler.performTicks(2)
        player.addAttachment(plugin, "sellgui.use", false)
        bridge.responses.single()(true)
        server.scheduler.performOneTick()
        assertEquals(8, count(player, Material.STONE))
        assertTrue(deposits.isEmpty())
    }

    @Test
    fun `menu filter and evaluation checks prevent Bedrock sales`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val player = bedrockPlayer(bridge)
        plugin.configManager.guiConfig.set("sell_menus.default.item-filter.denied-items", listOf("STONE"))
        stageItems(plugin, player)
        player.closeInventory()
        server.scheduler.performTicks(2)
        assertEquals(8, count(player, Material.STONE))
        assertTrue(bridge.responses.isEmpty())
        plugin.configManager.guiConfig.set("sell_menus.default.item-filter.denied-items", emptyList<String>())
        plugin.getRandomPriceManager().setRandomPriceRange(ItemStack(Material.DIRT), 1.0, 2.0, player.name)
        val gui = stageItems(plugin, player)
        gui.getMenu().setItem(11, ItemStack(Material.DIRT))
        player.closeInventory()
        server.scheduler.performTicks(2)
        assertEquals(16, count(player, Material.STONE))
        assertEquals(1, count(player, Material.DIRT))
        assertTrue(bridge.responses.isEmpty())
        assertTrue(deposits.isEmpty())
    }

    @Test
    fun `Java and disabled Bedrock support return items on normal close`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val javaPlayer = server.addPlayer(".JavaName")
        stageItems(plugin, javaPlayer)
        javaPlayer.closeInventory()
        assertEquals(8, count(javaPlayer, Material.STONE))
        val player = bedrockPlayer(bridge)
        plugin.config.set("bedrock.enabled", false)
        stageItems(plugin, player)
        player.closeInventory()
        server.scheduler.performTicks(3)
        assertEquals(8, count(player, Material.STONE))
        assertTrue(bridge.responses.isEmpty())
    }

    @Test
    fun `collect to cursor triggers sell controls but cannot collect ordinary items`() {
        val player = server.addPlayer()
        val gui = stageItems(plugin, player)
        val ordinary = click(player, 10, InventoryAction.COLLECT_TO_CURSOR)
        assertTrue(ordinary.isCancelled)
        val sell = click(player, 49, InventoryAction.COLLECT_TO_CURSOR)
        assertTrue(sell.isCancelled)
        assertTrue(gui.isConfirmMode())
        val confirm = click(player, 49, InventoryAction.COLLECT_TO_CURSOR)
        assertTrue(confirm.isCancelled)
        assertEquals(listOf(20.0), deposits)
        assertNull(gui.getMenu().getItem(10))
    }

    @Test
    fun `payment failure preserves Bedrock items`() {
        val (plugin, bridge) = loadBedrockPlugin()
        rejectDeposits = true
        val player = bedrockPlayer(bridge)
        stageItems(plugin, player)
        player.closeInventory()
        server.scheduler.performTicks(2)
        bridge.responses.single()(true)
        server.scheduler.performOneTick()
        assertEquals(8, count(player, Material.STONE))
        assertTrue(deposits.isEmpty())
    }

    @Test
    fun `cancelling with full inventory drops only the overflow`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val player = bedrockPlayer(bridge)
        stageItems(plugin, player)
        player.closeInventory()
        server.scheduler.performTicks(2)
        for (slot in 0 until player.inventory.size) player.inventory.setItem(slot, ItemStack(Material.DIRT, 64))
        bridge.responses.single()(false)
        server.scheduler.performOneTick()
        assertEquals(0, count(player, Material.STONE))
        val dropped = player.world.getEntitiesByClass(org.bukkit.entity.Item::class.java).filter { it.itemStack.type == Material.STONE }
        assertEquals(8, dropped.sumOf { it.itemStack.amount })
        assertTrue(deposits.isEmpty())
    }

    @Test
    fun `named Bedrock menu uses its filters and checks its permission again`() {
        val (plugin, bridge) = loadBedrockPlugin()
        val player = bedrockPlayer(bridge)
        assertTrue(server.dispatchCommand(player, "sell fishing"))
        val gui = SellCommand.getSellGUI(player)!!
        val fish = pricedItem(plugin).also { it.type = Material.COD }
        gui.getMenu().setItem(10, fish)
        gui.getMenu().setItem(11, pricedItem(plugin))
        player.closeInventory()
        server.scheduler.performTicks(2)
        assertEquals(8, count(player, Material.STONE))
        assertEquals(1, bridge.responses.size)
        assertTrue(bridge.contents.single().contains("20.00"))
        player.addAttachment(plugin, "sellgui.menu.fishing", false)
        bridge.responses.single()(true)
        server.scheduler.performOneTick()
        assertEquals(8, count(player, Material.COD))
        assertTrue(deposits.isEmpty())
    }

    private fun loadBedrockPlugin(): Pair<SellGUIMain, TestBedrockBridge> {
        val bridge = TestBedrockBridge()
        HandlerList.unregisterAll(plugin.bedrockSupport)
        val support = BedrockSupport(plugin, listOf(bridge))
        SellGUIMain::class.java.getDeclaredField("bedrockSupport").also { it.isAccessible = true }.set(plugin, support)
        server.pluginManager.registerEvents(support, plugin)
        return plugin to bridge
    }

    private fun bedrockPlayer(bridge: TestBedrockBridge): PlayerMock = server.addPlayer().also { bridge.players += it.uniqueId }

    private fun stageItems(plugin: SellGUIMain, player: PlayerMock): SellGUI {
        assertTrue(server.dispatchCommand(player, "sell"))
        return SellCommand.getSellGUI(player)!!.also { gui ->
            gui.getMenu().setItem(10, pricedItem(plugin))
            gui.updateSellItemTotal()
        }
    }

    private fun pricedItem(plugin: SellGUIMain, price: Double = 2.5): ItemStack = ItemStack(Material.STONE, 8).also { item ->
        item.itemMeta = item.itemMeta.also { meta ->
            meta.persistentDataContainer.set(NamespacedKey(plugin, "current_price"), PersistentDataType.DOUBLE, price)
        }
    }

    private fun count(player: PlayerMock, material: Material): Int = player.inventory.all(material).values.sumOf { it.amount }

    private fun click(player: PlayerMock, slot: Int, action: InventoryAction): InventoryClickEvent =
        InventoryClickEvent(player.openInventory, InventoryType.SlotType.CONTAINER, slot, ClickType.DOUBLE_CLICK, action)
            .also(server.pluginManager::callEvent)

}
