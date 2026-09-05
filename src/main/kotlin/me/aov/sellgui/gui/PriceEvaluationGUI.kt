package me.aov.sellgui.gui

import me.aov.sellgui.SellGUIMain
import me.aov.sellgui.managers.NBTPriceManager
import me.aov.sellgui.utils.ColorUtils
import me.aov.sellgui.utils.ItemIdentifier
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import kotlin.math.abs
import kotlin.random.Random

class PriceEvaluationGUI(private val main: SellGUIMain, private val player: Player, private val nbtPriceManager: NBTPriceManager) : InventoryHolder {
    enum class EvaluationMode { NONE, FIXED, RANDOM }
    private var itemSlot = 22; private var animationSlot = 20; private var resultSlot = 20
    private var evaluateButton = 33; private var cancelButton = 48; private var instructionSlot = 4
    private var animationTask: BukkitTask? = null
    private var locked = false
    private var evaluationMode = EvaluationMode.NONE
    private var minPrice = 0.0
    private var maxPrice = 0.0
    private val random = Random.Default
    private val inventory: Inventory

    init {
        val config = main.configManager.guiConfig
        loadLayoutFromConfig(config)
        inventory = Bukkit.createInventory(this, config.getInt("price_evaluation_gui.size", 54), color(main.setPlaceholders(player, config.getString("price_evaluation_gui.title", "&6&lPrice Evaluation").orEmpty())))
        setupGUI()
    }

    private fun loadLayoutFromConfig(config: FileConfiguration) {
        val path = "price_evaluation_gui.positions."
        itemSlot = config.getInt("${path}item_slot", 22); evaluateButton = config.getInt("${path}evaluate_button", 33); cancelButton = config.getInt("${path}cancel_button", 48); instructionSlot = config.getInt("${path}instruction_slot", 4)
        animationSlot = config.getInt("${path}animation_slot", 20); resultSlot = config.getInt("${path}result_slot", 20)
    }

    private fun setupGUI() {
        val filler = createItemFromConfig("filler", Material.GRAY_STAINED_GLASS_PANE, " ", emptyList())
        val slots = main.configManager.guiConfig.getIntegerList("price_evaluation_gui.positions.filler")
        if (slots.isEmpty()) for (slot in 0 until inventory.size) inventory.setItem(slot, filler) else slots.filter { it in 0 until inventory.size }.forEach { inventory.setItem(it, filler) }
        inventory.setItem(itemSlot, null)
        inventory.setItem(instructionSlot, createItemFromConfig("instruction", Material.BOOK, "&e&l📋 How to Use", emptyList()))
        updateButtons()
    }

    fun updateButtons() {
        inventory.setItem(evaluateButton, createItemFromConfig("evaluate_button", Material.NETHER_STAR, "&6&l⚡ Evaluate", emptyList()))
        inventory.setItem(cancelButton, createItemFromConfig("cancel_button", Material.BARRIER, "&c&l❌ Cancel", emptyList()))
    }

    fun setFixedPrice(price: Double) {
        evaluationMode = EvaluationMode.FIXED; minPrice = price; maxPrice = price
        player.sendMessage(color(message("fixed_price_set", "&a✅ Fixed price set to &e$%price%").replace("%price%", price.format())))
        updateButtons()
    }

    fun setRandomPrice(min: Double, max: Double) {
        if (min >= max) { player.sendMessage(color(message("invalid_range", "&c❌ Invalid price range! Minimum must be less than maximum."))); return }
        evaluationMode = EvaluationMode.RANDOM; minPrice = min; maxPrice = max
        player.sendMessage(color(message("random_range_set", "&a✅ Random price range set to &e$%min% - $%max%").replace("%min%", min.format()).replace("%max%", max.format())))
        updateButtons()
    }

    fun startEvaluation() {
        if (locked) { player.sendMessage(color(message("evaluation_in_progress", "&c❌ Evaluation already in progress!"))); return }
        val item = inventory.getItem(itemSlot)
        if (item == null || item.type == Material.AIR) { player.sendMessage(color(message("no_item", "&c❌ No item to evaluate!"))); return }
        if (!main.configManager.getConfig("config").getBoolean("general.allow-player-evaluation-stack", true) && item.amount > 1) {
            player.sendMessage(color(message("price_evaluation.stack_evaluation_disabled", "&c❌ You cannot evaluate stacked items."))); player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 1f, 1f); return
        }
        if (item.itemMeta?.persistentDataContainer?.has(NamespacedKey(main, "current_price"), PersistentDataType.DOUBLE) == true) { player.sendMessage(color(message("item_already_evaluated", "&c⚠️ This item has already been evaluated!"))); return }
        val identifier = ItemIdentifier.getItemIdentifier(item) ?: run { player.sendMessage(color(message("could_not_identify", "&c❌ Could not identify the item to evaluate."))); return }
        val saved = main.configManager.randomPricesConfig
        if (saved.isConfigurationSection(identifier)) {
            val minimum = saved.getDouble("$identifier.min_price", 0.0); val maximum = saved.getDouble("$identifier.max_price", 0.0)
            if (minimum > 0 && maximum > minimum) { minPrice = minimum; maxPrice = maximum; startRandomAnimation() } else player.sendMessage(color(message("invalid_range_in_config", "&c❌ The stored price range for this item is invalid. Please reset it.")))
        } else when (evaluationMode) {
            EvaluationMode.FIXED -> if (minPrice > 0) applyFixedPrice() else player.sendMessage(color(message("no_price_set", "&c❌ Set a price first!")))
            EvaluationMode.RANDOM -> if (minPrice > 0 && maxPrice > minPrice) startRandomAnimation() else player.sendMessage(color(message("no_price_set", "&c❌ Set a price range first!")))
            EvaluationMode.NONE -> player.sendMessage(color(message("no_price_configured", "&c❌ No price is configured for this item. Please set a price range for it first.")))
        }
    }

    private fun startRandomAnimation() {
        val item = inventory.getItem(itemSlot)?.clone() ?: return
        locked = true
        player.sendMessage(color(message("evaluation_started", "&a⚡ Evaluation started!"))); player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
        val finalPrice = calculateRandomPrice(minPrice, maxPrice); val isMaxPrice = abs(finalPrice - maxPrice) < 0.01
        val maxTicks = main.configManager.guiConfig.getInt("price_evaluation_gui.animation.duration", 60)
        val interval = main.configManager.guiConfig.getInt("price_evaluation_gui.animation.update_interval", 5).toLong()
        animationTask = object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks >= maxTicks) { completeRandomEvaluation(item, finalPrice, isMaxPrice); cancel(); return }
                updateAnimationDisplay(minPrice + random.nextDouble() * (maxPrice - minPrice))
                if (ticks % 5 == 0) player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, .5f, 1f + ticks * .02f)
                ticks++
            }
        }.runTaskTimer(main, 0L, interval)
    }

    private fun updateAnimationDisplay(price: Double) {
        val item = createItemFromConfig("animation", Material.GOLD_NUGGET, "&e&l🎲 Evaluating...", emptyList())
        item.itemMeta?.let { meta ->
            meta.setDisplayName((if (meta.hasDisplayName()) meta.displayName else "").replace("%current%", price.format()))
            meta.lore = meta.lore?.map { replacePricePlaceholders(it).replace("%current%", price.format()) }
            item.itemMeta = meta
        }
        inventory.setItem(animationSlot, item)
    }

    private fun completeRandomEvaluation(original: ItemStack, price: Double, isMaxPrice: Boolean) {
        locked = false
        inventory.setItem(itemSlot, addEvaluationInfo(original.clone(), price)); inventory.setItem(animationSlot, null)
        if (isMaxPrice) { player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f); player.sendMessage(color(message("jackpot_message", "&6🎉 JACKPOT! You got the maximum price!"))) } else player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        player.sendMessage(color(message("evaluation_complete_chat", "&a✅ Price evaluation complete! Final price: &e$%price%").replace("%price%", price.format())))
        showTemporaryResult(ItemIdentifier.getItemDisplayName(original)); evaluationMode = EvaluationMode.NONE; updateButtons()
    }

    private fun applyFixedPrice() {
        locked = true
        inventory.getItem(itemSlot)?.let { item ->
            inventory.setItem(itemSlot, addEvaluationInfo(item.clone(), minPrice))
            player.sendMessage(color(message("evaluation_complete", "&a✅ Price set: &e$%price%").replace("%price%", minPrice.format())))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
            showTemporaryResult(ItemIdentifier.getItemDisplayName(item))
        }
        locked = false; evaluationMode = EvaluationMode.NONE; updateButtons()
    }

    private fun showTemporaryResult(displayName: String) {
        val config = main.configManager.guiConfig
        val name = config.getString("price_evaluation_gui.items.result.name", "&a&l✅ Evaluation Complete!").orEmpty().replace("%evaluated_item_name%", displayName)
        val lore = config.getStringList("price_evaluation_gui.items.result.lore").map { it.replace("%evaluated_item_name%", displayName) }
        val result = createItem(Material.EMERALD, name, lore, -1)
        inventory.setItem(resultSlot, result)
        object : BukkitRunnable() { override fun run() { inventory.getItem(resultSlot)?.takeIf { it.isSimilar(result) }?.let { inventory.setItem(resultSlot, createItemFromConfig("filler", Material.GRAY_STAINED_GLASS_PANE, " ", emptyList())) } } }.runTaskLater(main, 100L)
    }

    private fun createItem(material: Material, name: String, lore: List<String>?, customModelData: Int): ItemStack = ItemStack(material).also { item ->
        item.itemMeta?.let { meta ->
            meta.setDisplayName(color(main.setPlaceholders(player, name)))
            meta.lore = lore?.let { main.setPlaceholders(player, it).map(::color) }
            if (customModelData != -1) meta.setCustomModelData(customModelData)
            item.itemMeta = meta
        }
    }

    private fun createItemFromConfig(key: String, defaultMaterial: Material, defaultName: String, defaultLore: List<String>): ItemStack {
        val config = main.configManager.guiConfig; val path = "price_evaluation_gui.items.$key"
        if (!config.contains(path)) return createItem(defaultMaterial, defaultName, defaultLore, -1)
        val material = Material.getMaterial(config.getString("$path.material", defaultMaterial.name).orEmpty().uppercase()) ?: defaultMaterial
        val name = replacePricePlaceholders(config.getString("$path.name", defaultName).orEmpty())
        val lore = config.getStringList("$path.lore").ifEmpty { defaultLore }.map(::replacePricePlaceholders)
        return createItem(material, name, lore, config.getInt("$path.custom-model-data", -1)).also { item ->
            config.getString("$path.nbt-id")?.takeIf(String::isNotEmpty)?.let { id -> item.itemMeta?.let { meta -> meta.persistentDataContainer.set(NamespacedKey(main, "sellgui-nbt-id"), PersistentDataType.STRING, id); item.itemMeta = meta } }
        }
    }

    private fun replacePricePlaceholders(text: String?): String = text.orEmpty().replace("%min%", minPrice.format()).replace("%max%", maxPrice.format()).replace("%price%", minPrice.format()).replace("%jackpot_chance%", main.configManager.guiConfig.getDouble("price_evaluation_gui.random_calculation.jackpot_chance", 20.0).format(1))
    private fun message(key: String, default: String): String = main.configManager.messagesConfig.getString("price_evaluation.$key", default) ?: default

    private fun calculateRandomPrice(min: Double, max: Double): Double {
        val config = main.configManager.guiConfig; val path = "price_evaluation_gui.random_calculation."
        if (random.nextDouble() * 100 < config.getDouble("${path}jackpot_chance", 0.0)) return max
        if (!config.getString("${path}distribution", "uniform").equals("weighted", true)) return min + random.nextDouble() * (max - min)
        val low = config.getDouble("${path}weighted.low_range_weight", 33.3); val mid = config.getDouble("${path}weighted.mid_range_weight", 33.3); val high = config.getDouble("${path}weighted.high_range_weight", 33.3); val total = low + mid + high
        if (total <= 0) return min + random.nextDouble() * (max - min)
        val range = max - min
        val roll = random.nextDouble() * total
        return when {
            roll < low -> min + random.nextDouble() * range * .33
            roll < low + mid -> min + range * .33 + random.nextDouble() * range * .34
            else -> min + range * .67 + random.nextDouble() * range * .33
        }
    }

    override fun getInventory(): Inventory = inventory
    private fun addEvaluationInfo(item: ItemStack, price: Double): ItemStack {
        try {
            item.itemMeta?.let { meta ->
                meta.persistentDataContainer.set(NamespacedKey(main, "current_price"), PersistentDataType.DOUBLE, price); meta.persistentDataContainer.set(NamespacedKey(main, "evaluated"), PersistentDataType.BYTE, 1.toByte())
                val format = main.configManager.guiConfig.getString("price_evaluation_gui.evaluation_lore_format", "&a✅ Evaluated: &f$%price%").orEmpty()
                val prefix = (ChatColor.stripColor(format) ?: "").replace("%price%", "").trim()
                val lore = (meta.lore ?: emptyList()).filterNot { (ChatColor.stripColor(it) ?: "").startsWith(prefix) }.toMutableList()
                lore += ""; lore += color(format.replace("%price%", price.format())); meta.lore = lore; item.itemMeta = meta
            }
        } catch (exception: Exception) { main.logger.warning("Failed to add evaluation info to item: ${exception.message}") }
        return item
    }

    fun cleanup() { animationTask?.takeUnless { it.isCancelled }?.cancel() }
    fun isLocked(): Boolean = locked
    fun getItemSlot(): Int = itemSlot
    fun returnItemToPlayer() {
        inventory.getItem(itemSlot)?.takeIf { it.type != Material.AIR }?.let { item ->
            inventory.clear(itemSlot); player.inventory.addItem(item).values.forEach { player.world.dropItem(player.location, it) }; player.sendMessage(color(message("item_returned", "&eYour item has been returned to your inventory.")))
        }
    }
    private fun color(value: String): String = ColorUtils.color(value).orEmpty()
    private fun Double.format(decimals: Int = 2): String = java.lang.String.format("%.${decimals}f", this)
}
