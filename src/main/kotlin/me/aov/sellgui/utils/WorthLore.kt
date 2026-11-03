package me.aov.sellgui.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/** Renders and removes the display-only worth line independently of packet transport. */
class WorthLore(val template: String) {
    private val plainText = PlainTextComponentSerializer.plainText()
    private val legacy = LegacyComponentSerializer.legacySection()
    private val placeholders = Regex("%price%|%total%|%amount%")
    private val generatedLine = run {
        val plainTemplate = plainText.serialize(component(template))
        var offset = 0
        val pattern = buildString {
            placeholders.findAll(plainTemplate).forEach { match ->
                append(Regex.escape(plainTemplate.substring(offset, match.range.first)))
                append(when (match.value) {
                    "%amount%" -> "[0-9]+"
                    "%price%" -> "\\p{Nd}+[.,\u066B]\\p{Nd}{2}/u"
                    else -> "\\p{Nd}+[.,\u066B]\\p{Nd}{2}"
                })
                offset = match.range.last + 1
            }
            append(Regex.escape(plainTemplate.substring(offset)))
        }
        Regex(pattern)
    }

    fun remove(lore: List<Component>): List<Component> = lore.filterNot {
        generatedLine.matches(plainText.serialize(it))
    }

    fun update(lore: List<Component>, unitPrice: Double, amount: Int, show: Boolean): List<Component> {
        val cleaned = remove(lore)
        if (!show || unitPrice <= 0 || amount <= 0) return cleaned
        val line = template
            .replace("%price%", "%.2f".format(unitPrice) + "/u")
            .replace("%total%", "%.2f".format(unitPrice * amount))
            .replace("%amount%", amount.toString())
        return cleaned + component(line)
    }

    private fun component(line: String): Component =
        legacy.deserialize(ColorUtils.color(line).orEmpty()).decoration(TextDecoration.ITALIC, false)
}
