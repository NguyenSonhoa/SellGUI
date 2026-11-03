package me.aov.sellgui

import net.milkbowl.vault.economy.Economy
import me.aov.sellgui.listeners.PacketEventsPacketListener
import me.aov.sellgui.utils.WorthLore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.ServicePriority
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.util.UnsafeValuesMock
import java.lang.reflect.Proxy

class SellGUIPluginTest {
    private lateinit var server: ServerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock(object : ServerMock() {
            private val unsafeValues = object : UnsafeValuesMock() {
                // Price-file initialization enumerates legacy aliases; legacy conversion itself
                // is outside these tests and missing in MockBukkit 4.116.3.
                override fun fromLegacy(material: Material): Material =
                    Material.getMaterial(material.name.removePrefix("LEGACY_")) ?: Material.AIR
                override fun fromLegacy(material: org.bukkit.material.MaterialData, itemPriority: Boolean): Material =
                    fromLegacy(material.itemType)
            }
            override fun getUnsafe(): UnsafeValuesMock = unsafeValues
        })
        val vault = MockBukkit.createMockPlugin("Vault")
        MockBukkit.createMockPlugin("NBTAPI")
        val economy = Proxy.newProxyInstance(Economy::class.java.classLoader, arrayOf(Economy::class.java)) { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> true
                Int::class.javaPrimitiveType -> 2
                Double::class.javaPrimitiveType -> 0.0
                String::class.java -> "TestEconomy"
                else -> null
            }
        } as Economy
        server.servicesManager.register(Economy::class.java, economy, vault, ServicePriority.Normal)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
        SellGUIMain.instance = null
        SellGUIMain.econ = null
    }

    @Test
    fun `plugin enables with its real descriptor and disables cleanly`() {
        val plugin = MockBukkit.load(SellGUIMain::class.java)
        assertTrue(plugin.isEnabled)
        assertNotNull(plugin.getCommand("sellgui")?.executor)
        assertNotNull(plugin.getEcon())
        assertTrue(plugin.getMessagesConfig().contains("sell.lore_worth"))
        server.pluginManager.disablePlugin(plugin)
        assertFalse(plugin.isEnabled)
    }

    @Test
    fun `evaluated stack total uses its unit worth and leaves the server item untouched`() {
        val plugin = MockBukkit.load(SellGUIMain::class.java)
        val player = server.addPlayer()
        val item = ItemStack(Material.STONE, 64)
        val originalLore = listOf(Component.text("Original lore"))
        item.itemMeta = item.itemMeta.also {
            it.lore(originalLore)
            it.persistentDataContainer.set(NamespacedKey(plugin, "current_price"), PersistentDataType.DOUBLE, 1.25)
        }
        val original = item.clone()
        val priceMethod = PacketEventsPacketListener::class.java.getDeclaredMethod("calculatePrice", ItemStack::class.java, Player::class.java)
        priceMethod.isAccessible = true
        val price = priceMethod.invoke(PacketEventsPacketListener(plugin), item, player) as Double
        val displayed = WorthLore("Worth: $%total%").update(originalLore, price, item.amount, true)
        assertEquals(listOf("Original lore", "Worth: $80.00"), displayed.map(PlainTextComponentSerializer.plainText()::serialize))
        assertEquals(original, item)
        assertEquals(originalLore, item.itemMeta.lore())
    }

}
