package me.aov.sellgui

import me.aov.sellgui.listeners.PacketEventsPacketListener
import me.aov.sellgui.utils.WorthLore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SellGUIPluginTest : SellGUITestFixture() {
    @Test
    fun `plugin enables with its real descriptor and disables cleanly`() {
        assertTrue(plugin.isEnabled)
        assertNotNull(plugin.getCommand("sellgui")?.executor)
        assertNotNull(plugin.getEcon())
        assertTrue(plugin.getMessagesConfig().contains("sell.lore_worth"))
        server.pluginManager.disablePlugin(plugin)
        assertFalse(plugin.isEnabled)
    }

    @Test
    fun `evaluated stack total uses its unit worth and leaves the server item untouched`() {
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
