package me.aov.sellgui.listeners

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.component.ComponentTypes
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore
import com.github.retrooper.packetevents.protocol.item.ItemStack
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.config.ConfigManager
import me.aov.sellgui.utils.ItemIdentifier
import me.aov.sellgui.utils.WorthLore
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.persistence.PersistentDataType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

class PacketEventsPacketListener(private val main: SellGUIMain) : PacketListenerAbstract(PacketListenerPriority.HIGH) {
    private val openGuiTitles = ConcurrentHashMap<Player, String>()
    @Volatile private var cachedWorthLore: WorthLore? = null
    override fun onPacketSend(event: PacketSendEvent) = when (event.packetType) {
        PacketType.Play.Server.WINDOW_ITEMS -> handleWindowItems(event)
        PacketType.Play.Server.SET_SLOT -> handleSetSlot(event)
        PacketType.Play.Server.OPEN_WINDOW -> handleOpenWindow(event)
        else -> Unit
    }
    override fun onPacketReceive(event: PacketReceiveEvent) {
        when (event.packetType) {
            PacketType.Play.Client.CLICK_WINDOW -> handleWindowClick(event)
            PacketType.Play.Client.CLOSE_WINDOW -> (event.getPlayer() as? Player)?.let { openGuiTitles.remove(it) }
        }
    }

    private fun handleOpenWindow(event: PacketSendEvent) {
        val player = event.getPlayer() as? Player ?: return
        val title = PlainTextComponentSerializer.plainText().serialize(WrapperPlayServerOpenWindow(event).title)
        openGuiTitles[player] = ConfigManager.stripColorCodes(title)
    }

    private fun handleWindowClick(event: PacketReceiveEvent) {
        val player = event.getPlayer() as? Player ?: return
        val wrapper = WrapperPlayClientClickWindow(event)
        wrapper.slots.ifPresent { slots -> slots.replaceAll { _, item -> removeWorthLore(item) } }
        wrapper.carriedItemStack = removeWorthLore(wrapper.carriedItemStack)
        main.server.scheduler.runTaskLater(main, player::updateInventory, 1L)
        val state = wrapper.stateId.orElse(0); val button = wrapper.button; val slot = wrapper.slot
        main.server.scheduler.runTaskLater(main, Runnable {
            updateItemAtProtocolSlot(player, slot, state)
            if (button in 0..8 && slot in 9..44) updateItemAtProtocolSlot(player, button + 36, state)
        }, 1L)
    }

    private fun updateItemAtProtocolSlot(player: Player, slot: Int, state: Int) {
        val item = getItemFromProtocolSlot(player, slot) ?: return
        if (item.type == Material.AIR || ItemIdentifier.getItemType(item) == ItemIdentifier.ItemType.NEXO) return
        val modified = booleanArrayOf(false); val processed = processItem(SpigotConversionUtil.fromBukkitItemStack(item), player, modified)
        if (modified[0]) PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerSetSlot(0, state, slot, processed))
    }

    private fun getItemFromProtocolSlot(player: Player, slot: Int): org.bukkit.inventory.ItemStack? = when (slot) { in 9..35 -> player.inventory.getItem(slot); in 36..44 -> player.inventory.getItem(slot - 36); else -> null }
    private fun handleWindowItems(event: PacketSendEvent) {
        val player = event.getPlayer() as? Player ?: return; val wrapper = WrapperPlayServerWindowItems(event); val modified = booleanArrayOf(false)
        val items = wrapper.items.map { processItem(it, player, modified) }
        if (modified[0]) wrapper.items = items
    }
    private fun handleSetSlot(event: PacketSendEvent) {
        val player = event.getPlayer() as? Player ?: return; val wrapper = WrapperPlayServerSetSlot(event); val modified = booleanArrayOf(false)
        val item = processItem(wrapper.item, player, modified); if (modified[0]) wrapper.item = item
    }

    private fun processItem(item: ItemStack?, player: Player, modified: BooleanArray): ItemStack? {
        if (item == null || item.isEmpty) return item
        val bukkit = SpigotConversionUtil.toBukkitItemStack(item)
        if (isGuiItem(bukkit)) return item
        val originalLore = item.getComponent(ComponentTypes.LORE).map { it.lines }.orElse(emptyList())
        val show = shouldShowWorthLore(player)
        val lore = worthLore().update(originalLore, if (show) calculatePrice(bukkit, player) else 0.0, bukkit.amount, show)
        if (lore == originalLore) return item
        return item.copy().also { updated -> if (lore.isEmpty()) updated.unsetComponent(ComponentTypes.LORE) else updated.setComponent(ComponentTypes.LORE, ItemLore(lore)); modified[0] = true }
    }

    private fun removeWorthLore(item: ItemStack?): ItemStack? {
        if (item == null || item.isEmpty) return item
        val originalLore = item.getComponent(ComponentTypes.LORE).map { it.lines }.orElse(emptyList())
        val lore = worthLore().remove(originalLore)
        if (lore == originalLore) return item
        return item.copy().also { if (lore.isEmpty()) it.unsetComponent(ComponentTypes.LORE) else it.setComponent(ComponentTypes.LORE, ItemLore(lore)) }
    }

    private fun worthLore(): WorthLore {
        val template = main.getMessagesConfig().getString("sell.lore_worth", "&7Worth: &a$%price%").orEmpty()
        return cachedWorthLore?.takeIf { it.template == template }
            ?: WorthLore(template).also { cachedWorthLore = it }
    }
    private fun shouldShowWorthLore(player: Player): Boolean = if (main.configManager.isWorthLoreWhitelistGuiEnabled()) main.configManager.getWorthLoreWhitelistGuiTitles().contains(openGuiTitles[player] ?: "Inventory") else !main.configManager.getWorthLoreBlacklistGuiTitles().contains(openGuiTitles[player] ?: "Inventory")
    private fun isGuiItem(item: org.bukkit.inventory.ItemStack?): Boolean = item?.takeIf { it.hasItemMeta() }?.itemMeta?.persistentDataContainer?.has(NamespacedKey(main, "sellgui"), PersistentDataType.BYTE) == true

    private fun baseItemPrice(item: org.bukkit.inventory.ItemStack?, player: Player): BigDecimal {
        if (item == null || item.type == Material.AIR) return BigDecimal.ZERO
        val single = item.clone().also { it.amount = 1 }
        var price = single.itemMeta?.persistentDataContainer?.get(NamespacedKey(main, "current_price"), PersistentDataType.DOUBLE)?.let(BigDecimal::valueOf) ?: BigDecimal.ZERO
        if (price.compareTo(BigDecimal.ZERO) == 0) main.getPriceManager()?.getItemPriceWithPlayer(single, player)?.takeIf { it > 0 }?.let { price = BigDecimal.valueOf(it) }
        if (main.getRandomPriceManager()?.canBeSold(single) == false) return BigDecimal.ZERO
        return price
    }
    private fun shulkerContentsPrice(item: org.bukkit.inventory.ItemStack?, player: Player): BigDecimal {
        val box = (item?.itemMeta as? BlockStateMeta)?.blockState as? ShulkerBox ?: return BigDecimal.ZERO
        return box.inventory.contents.filterNotNull().filter { !it.type.isAir }.fold(BigDecimal.ZERO) { total, contained -> total.add(baseItemPrice(contained, player).multiply(BigDecimal.valueOf(contained.amount.toLong()))) }
    }
    private fun calculatePrice(item: org.bukkit.inventory.ItemStack?, player: Player): Double {
        if (item == null || item.type == Material.AIR) return 0.0
        var price = baseItemPrice(item, player).add(shulkerContentsPrice(item, player))
        if (price > BigDecimal.ZERO) price = applyPermissionBonuses(player, price)
        return price.toDouble()
    }
    private fun applyPermissionBonuses(player: Player, price: BigDecimal): BigDecimal {
        if (price <= BigDecimal.ZERO) return price
        val bonus = player.effectivePermissions.filter { it.value && it.permission.startsWith("sellgui.bonus.") }.fold(BigDecimal.ZERO) { total, info -> total + (info.permission.removePrefix("sellgui.bonus.").toBigDecimalOrNull() ?: BigDecimal.ZERO) }
        return if (bonus > BigDecimal.ZERO) price * (BigDecimal.ONE + bonus.divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)) else price
    }

}
