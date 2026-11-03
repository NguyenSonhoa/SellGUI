package me.aov.sellgui.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Locale

class WorthLoreTest {
    private fun text(lines: List<Component>) = lines.map(PlainTextComponentSerializer.plainText()::serialize)

    @Test
    fun `total is the price of the complete stack`() {
        val lore = WorthLore("&7Worth: &a$%total%")
        assertEquals(listOf("Worth: $80.00"), text(lore.update(emptyList(), 1.25, 64, true)))
        assertEquals(listOf("Worth: $1.25"), text(lore.update(emptyList(), 1.25, 1, true)))
    }

    @Test
    fun `unit price remains compatible and can appear beside total and amount`() {
        val lore = WorthLore("&7%amount%x: &a$%price% &7(total $%total%)")
        assertEquals(listOf("64x: $1.25/u (total $80.00)"), text(lore.update(emptyList(), 1.25, 64, true)))
    }

    @Test
    fun `existing unit price templates keep one worth line when refreshed`() {
        val lore = WorthLore("&7Worth: &a$%price%")
        val original = listOf(Component.text("Worth: priceless heirloom"))
        val displayed = lore.update(original, 1.25, 64, true)
        assertEquals(listOf("Worth: priceless heirloom", "Worth: $1.25/u"), text(displayed))
        assertEquals(displayed, lore.update(displayed, 1.25, 32, true))
        assertEquals(original, lore.remove(displayed))
    }

    @Test
    fun `splitting and merging a stack replaces its old total and preserves original lore`() {
        val lore = WorthLore("&#FCD34DWorth: $%total%")
        val original = listOf(Component.text("Worth: priceless heirloom"), Component.text("Original item lore"))
        val fullStack = lore.update(original, 1.25, 64, true)
        val splitStack = lore.update(fullStack, 1.25, 16, true)
        val mergedStack = lore.update(splitStack, 1.25, 64, true)
        assertEquals(listOf("Worth: priceless heirloom", "Original item lore", "Worth: $20.00"), text(splitStack))
        assertEquals(fullStack, mergedStack)
        assertEquals(2, original.size)
    }

    @Test
    fun `incoming item has only its generated worth line removed`() {
        val lore = WorthLore("&8* &fWorth: &#FCD34D$%total%")
        val original = listOf(Component.text("Custom lore"))
        assertEquals(original, lore.remove(lore.update(original, 2.5, 32, true)))
    }

    @Test
    fun `placeholder at the beginning is still cleaned without matching unrelated lore`() {
        val lore = WorthLore("%total% coins for %amount% items")
        val original = listOf(Component.text("50 coins for a quest"))
        val displayed = lore.update(original, 2.5, 20, true)
        assertEquals(listOf("50 coins for a quest", "50.00 coins for 20 items"), text(displayed))
        assertEquals(original, lore.remove(displayed))
    }

    @Test
    fun `hiding worth or having no sell price removes any previous generated line`() {
        val lore = WorthLore("Worth: $%total%")
        val displayed = lore.update(emptyList(), 2.0, 8, true)
        assertTrue(lore.update(displayed, 2.0, 8, false).isEmpty())
        assertTrue(lore.update(displayed, 0.0, 8, true).isEmpty())
        assertTrue(lore.update(displayed, 2.0, 0, true).isEmpty())
    }

    @Test
    fun `decimal comma totals are also removed on refresh`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val lore = WorthLore("Worth: $%total%")
            val displayed = lore.update(emptyList(), 1.25, 2, true)
            assertEquals(listOf("Worth: $2,50"), text(displayed))
            assertEquals(listOf("Worth: $5,00"), text(lore.update(displayed, 1.25, 4, true)))
            assertTrue(lore.remove(displayed).isEmpty())
        } finally {
            Locale.setDefault(previous)
        }
    }
}
