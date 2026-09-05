package me.aov.sellgui.utils

import io.lumine.mythic.lib.api.item.NBTItem
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.lang.reflect.Method

object ItemIdentifier {
    private val hasMMOItemsPlugin = Bukkit.getPluginManager().getPlugin("MMOItems") != null
    private val hasNexoPlugin = Bukkit.getPluginManager().getPlugin("Nexo") != null
    private val nexoIdKey: NamespacedKey? = if (hasNexoPlugin) {
        try {
            val nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems")
            val getItemIdMethod = nexoItemsClass.getMethod("getITEM_ID")
            getItemIdMethod.invoke(null) as NamespacedKey
        } catch (_: Exception) {
            NamespacedKey("nexo", "id")
        }
    } else {
        null
    }

    enum class ItemType { VANILLA, MMOITEMS, NEXO, UNKNOWN }

    @JvmStatic
    fun getItemType(item: ItemStack?): ItemType {
        if (item == null || item.type == Material.AIR) return ItemType.UNKNOWN
        if (hasMMOItemsPlugin) {
            try {
                if (NBTItem.get(item).hasTag("MMOITEMS_ITEM_ID")) return ItemType.MMOITEMS
            } catch (_: Exception) {
            }
        }
        return if (hasNexoPlugin && isNexoItem(item)) ItemType.NEXO else ItemType.VANILLA
    }

    @JvmStatic
    fun getItemIdentifier(item: ItemStack?): String? {
        if (item == null || item.type == Material.AIR) return null
        val type = getItemType(item)
        try {
            when (type) {
                ItemType.MMOITEMS -> if (hasMMOItemsPlugin) {
                    val nbt = NBTItem.get(item)
                    val itemType = nbt.getString("MMOITEMS_ITEM_TYPE")
                    val itemId = nbt.getString("MMOITEMS_ITEM_ID")
                    if (!itemType.isNullOrEmpty() && !itemId.isNullOrEmpty()) {
                        return "MMOITEMS:${itemType.uppercase()}.${itemId.uppercase()}"
                    }
                }
                ItemType.NEXO -> if (hasNexoPlugin) {
                    val id = getNexoItemId(item)
                    if (!id.isNullOrEmpty()) return "NEXO:${id.uppercase()}"
                }
                else -> Unit
            }
        } catch (_: Exception) {
        }
        return if (type == ItemType.VANILLA) "VANILLA:${item.type.name}" else null
    }

    @JvmStatic
    fun getItemDisplayName(item: ItemStack?): String {
        if (item == null || item.type == Material.AIR) return "Unknown Item"
        if (hasMMOItemsPlugin) {
            try {
                val nbt = NBTItem.get(item)
                if (nbt.hasTag("display.Name")) {
                    val json = nbt.getString("display.Name")
                    return ChatColor.translateAlternateColorCodes(
                        '&',
                        net.md_5.bungee.api.ChatColor.of("#").toString() +
                            net.md_5.bungee.chat.ComponentSerializer.toString(
                                net.md_5.bungee.chat.ComponentSerializer.parse(json)[0]
                            )
                    )
                }
                if (nbt.hasTag("MMOITEMS_NAME")) {
                    return ChatColor.translateAlternateColorCodes('&', nbt.getString("MMOITEMS_NAME"))
                }
            } catch (_: Exception) {
            }
        }
        if (hasNexoPlugin) {
            try {
                val clazz = Class.forName("com.nexomc.nexo.api.NexoItems")
                val method = clazz.getMethod("getDisplayName", ItemStack::class.java)
                (method.invoke(null, item) as? String)?.takeIf(String::isNotEmpty)?.let { return it }
            } catch (_: Exception) {
            }
        }
        val meta = item.itemMeta
        if (meta != null) {
            if (meta.hasDisplayName()) return meta.displayName
            try {
                if (meta.hasItemName()) return meta.itemName
            } catch (_: Throwable) {
            }
        }
        return item.type.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
    }

    @JvmStatic
    fun getItemStackFromIdentifier(identifier: String?): ItemStack? {
        if (identifier.isNullOrEmpty()) return null
        val type = getItemTypeFromString(identifier)
        val parts = identifier.split(":", limit = 2)
        if (parts.size < 2) return null
        val id = parts[1].uppercase()
        return when (type) {
            ItemType.VANILLA -> runCatching { ItemStack(Material.valueOf(id)) }.getOrNull()
            ItemType.MMOITEMS -> {
                if (!hasMMOItemsPlugin) return placeholder("MMOItems Not Loaded", id)
                try {
                    val clazz = Class.forName("net.Indyuce.mmoitems.MMOItems")
                    val plugin = clazz.getDeclaredField("plugin").get(null)
                    val mmo = id.split(".", limit = 2)
                    if (mmo.size != 2) return placeholder("Invalid MMOItems ID", id)
                    clazz.getMethod("getItem", String::class.java, String::class.java)
                        .invoke(plugin, mmo[0], mmo[1]) as? ItemStack ?: placeholder("MMOItem Not Found", id)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    placeholder("MMOItems Error", id)
                }
            }
            ItemType.NEXO -> {
                if (!hasNexoPlugin) return placeholder("Nexo Not Loaded", id)
                try {
                    val clazz = Class.forName("com.nexomc.nexo.api.NexoItems")
                    val builder = clazz.getMethod("itemFromId", String::class.java).invoke(null, id.lowercase())
                    if (builder == null) placeholder("Nexo Item Not Found", id)
                    else builder.javaClass.getMethod("build").invoke(builder) as ItemStack
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    placeholder("Nexo Error", id)
                }
            }
            else -> null
        }
    }

    private fun isNexoItem(item: ItemStack?): Boolean {
        val key = nexoIdKey ?: return false
        if (!hasNexoPlugin || item == null || !item.hasItemMeta()) return false
        return item.itemMeta.persistentDataContainer.has(key, PersistentDataType.STRING)
    }

    private fun getNexoItemId(item: ItemStack?): String? {
        val key = nexoIdKey ?: return null
        if (!hasNexoPlugin || item == null || !item.hasItemMeta()) return null
        return item.itemMeta.persistentDataContainer.get(key, PersistentDataType.STRING)
    }

    @JvmStatic
    fun getItemTypeFromString(id: String?): ItemType {
        if (id.isNullOrEmpty() || !id.contains(':')) return ItemType.UNKNOWN
        return runCatching { ItemType.valueOf(id.split(":", limit = 2)[0].uppercase()) }.getOrDefault(ItemType.UNKNOWN)
    }

    private fun placeholder(title: String, subtitle: String): ItemStack {
        return ItemStack(Material.PAPER).also { item ->
            item.itemMeta?.let { meta ->
                meta.setDisplayName("\u00a7c$title")
                meta.lore = listOf("\u00a77$subtitle")
                item.itemMeta = meta
            }
        }
    }

    @JvmStatic
    fun debugItemNBT(item: ItemStack?) {
        if (item == null || item.type == Material.AIR) {
            println("[SellGUI Debug] Item is null or AIR")
            return
        }
        println("\u00a76=== SellGUI Item Debug ===")
        println("Material: ${item.type}")
        println("Identifier: ${getItemIdentifier(item)}")
        println("Display Name: ${getItemDisplayName(item)}")
        println("Type: ${getItemType(item)}")
        if (hasMMOItemsPlugin) {
            try {
                val nbt = NBTItem.get(item)
                println("MMOITEMS_ITEM_TYPE: ${nbt.getString("MMOITEMS_ITEM_TYPE")}")
                println("MMOITEMS_ITEM_ID: ${nbt.getString("MMOITEMS_ITEM_ID")}")
            } catch (_: Exception) {
            }
        }
        if (hasNexoPlugin && isNexoItem(item)) println("Nexo ID: ${getNexoItemId(item)}")
        println("\u00a76=== End Debug ===\n")
    }
}
