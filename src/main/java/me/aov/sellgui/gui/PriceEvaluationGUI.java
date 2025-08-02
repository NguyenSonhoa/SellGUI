package me.aov.sellgui.gui;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.managers.NBTPriceManager;
import me.aov.sellgui.utils.ItemIdentifier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class PriceEvaluationGUI implements InventoryHolder {

    public enum EvaluationMode {
        NONE,
        FIXED,
        RANDOM
    }

    private final SellGUIMain main;
    private final Player player;
    private final Inventory inventory;
    private final NBTPriceManager nbtPriceManager;
    private final Random random;

    private int ITEM_SLOT;
    private int ANIMATION_SLOT;
    private int RESULT_SLOT;
    private int EVALUATE_BUTTON;
    private int CANCEL_BUTTON;
    private int INSTRUCTION_SLOT;

    private BukkitTask animationTask;
    private boolean isLocked = false;
    private EvaluationMode evaluationMode = EvaluationMode.NONE;
    private double minPrice = 0;
    private double maxPrice = 0;

    public PriceEvaluationGUI(SellGUIMain main, Player player) {
        this.main = main;
        this.player = player;
        this.nbtPriceManager = new NBTPriceManager(main);
        this.random = new Random();

        FileConfiguration guiConfig = main.getConfigManager().getGUIConfig();
        String title = color(guiConfig.getString("price_evaluation_gui.title", "&6&lPrice Evaluation"));
        int size = guiConfig.getInt("price_evaluation_gui.size", 54);

        loadLayoutFromConfig(guiConfig);

        this.inventory = Bukkit.createInventory(this, size, title);

        setupGUI();
    }

    private void loadLayoutFromConfig(FileConfiguration guiConfig) {
        if (guiConfig != null) {
            String path = "price_evaluation_gui.positions.";
            ITEM_SLOT = guiConfig.getInt(path + "item_slot", 22);
            EVALUATE_BUTTON = guiConfig.getInt(path + "evaluate_button", 33);
            CANCEL_BUTTON = guiConfig.getInt(path + "cancel_button", 48);
            INSTRUCTION_SLOT = guiConfig.getInt(path + "instruction_slot", 4);

            ANIMATION_SLOT = guiConfig.getInt("price_evaluation_gui.items.animation_slot", 22);
            RESULT_SLOT = guiConfig.getInt("price_evaluation_gui.items.result_slot", 20);
        } else {

            ITEM_SLOT = 22;
            ANIMATION_SLOT = 22;
            RESULT_SLOT = 22;
            EVALUATE_BUTTON = 33;
            CANCEL_BUTTON = 48;
            INSTRUCTION_SLOT = 4;
        }
    }

    private void setupGUI() {

        ItemStack filler = createItemFromConfig("filler", Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>());
        List<Integer> fillerSlots = main.getConfigManager().getGUIConfig().getIntegerList("price_evaluation_gui.positions.filler");

        if (fillerSlots.isEmpty()) {
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        } else {
            for (int slot : fillerSlots) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, filler);
                }
            }
        }

        inventory.setItem(ITEM_SLOT, null);

        ItemStack instruction = createItemFromConfig("instruction", Material.BOOK, "&e&l📋 How to Use", new ArrayList<>());
        inventory.setItem(INSTRUCTION_SLOT, instruction);

        updateButtons();
    }

    public void updateButtons() {
        ItemStack evaluateButton = createItemFromConfig("evaluate_button", Material.NETHER_STAR, "&6&l⚡ Evaluate", new ArrayList<>());
        inventory.setItem(EVALUATE_BUTTON, evaluateButton);

        ItemStack cancelButton = createItemFromConfig("cancel_button", Material.BARRIER, "&c&l❌ Cancel", new ArrayList<>());
        inventory.setItem(CANCEL_BUTTON, cancelButton);
    }

    public void setFixedPrice(double price) {
        this.evaluationMode = EvaluationMode.FIXED;
        this.minPrice = price;
        this.maxPrice = price;
        player.sendMessage(color(getMessage("fixed_price_set", "&a✅ Fixed price set to &e$%price%").replace("%price%", String.format("%.2f", price))));
        updateButtons();
    }

    public void setRandomPrice(double min, double max) {
        if (min >= max) {
            player.sendMessage(color(getMessage("invalid_range", "&c❌ Invalid price range! Minimum must be less than maximum.")));
            return;
        }
        this.evaluationMode = EvaluationMode.RANDOM;
        this.minPrice = min;
        this.maxPrice = max;
        player.sendMessage(color(getMessage("random_range_set", "&a✅ Random price range set to &e$%min% - $%max%")
                .replace("%min%", String.format("%.2f", min))
                .replace("%max%", String.format("%.2f", max))));
        updateButtons();
    }

    public void startEvaluation() {
        if (isLocked) {
            player.sendMessage(color(getMessage("evaluation_in_progress", "&c❌ Evaluation already in progress!")));
            return;
        }

        ItemStack item = inventory.getItem(ITEM_SLOT);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(color(getMessage("no_item", "&c❌ No item to evaluate!")));
            return;
        }

        // Check for stacked items if disabled in config
        if (!main.getConfigManager().getConfig("config").getBoolean("general.allow-player-evaluation-stack", true) && item.getAmount() > 1) {
            player.sendMessage(color(getMessage("price_evaluation.stack_evaluation_disabled", "&c❌ You cannot evaluate stacked items.")));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            return;
        }

        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(main, "current_price"), PersistentDataType.DOUBLE)) {
            player.sendMessage(color(getMessage("item_already_evaluated", "&c⚠️ This item has already been evaluated!")));
            return;
        }

        String itemIdentifier = ItemIdentifier.getItemIdentifier(item);
        if (itemIdentifier == null) {
            player.sendMessage(color(getMessage("could_not_identify", "&c❌ Could not identify the item to evaluate.")));
            return;
        }

        FileConfiguration randomPricesConfig = main.getConfigManager().getRandomPricesConfig();

        if (randomPricesConfig.isConfigurationSection(itemIdentifier)) {
            double savedMin = randomPricesConfig.getDouble(itemIdentifier + ".min_price", 0);
            double savedMax = randomPricesConfig.getDouble(itemIdentifier + ".max_price", 0);

            if (savedMin > 0 && savedMax > savedMin) {
                this.minPrice = savedMin;
                this.maxPrice = savedMax;
                startRandomAnimation();
            } else {
                player.sendMessage(color(getMessage("invalid_range_in_config", "&c❌ The stored price range for this item is invalid. Please reset it.")));
            }
        } else {

            switch (evaluationMode) {
                case FIXED:
                    if (minPrice > 0) {
                        applyFixedPrice();
                    } else {
                        player.sendMessage(color(getMessage("no_price_set", "&c❌ Set a price first!")));
                    }
                    break;
                case RANDOM:
                    if (minPrice > 0 && maxPrice > minPrice) {
                        startRandomAnimation();
                    } else {
                        player.sendMessage(color(getMessage("no_price_set", "&c❌ Set a price range first!")));
                    }
                    break;
                default:
                    player.sendMessage(color(getMessage("no_price_configured", "&c❌ No price is configured for this item. Please set a price range for it first.")));
                    break;
            }
        }
    }

    private void startRandomAnimation() {
        isLocked = true;
        final ItemStack itemToEvaluate = inventory.getItem(ITEM_SLOT).clone();

        player.sendMessage(color(getMessage("evaluation_started", "&a⚡ Evaluation started!")));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

        final double finalPrice = calculateRandomPrice(minPrice, maxPrice);
        final boolean isMaxPrice = Math.abs(finalPrice - maxPrice) < 0.01;

        animationTask = new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = main.getConfigManager().getGUIConfig().getInt("price_evaluation_gui.animation.duration", 60);

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    completeRandomEvaluation(itemToEvaluate, finalPrice, isMaxPrice);
                    cancel();
                    return;
                }

                double animPrice = minPrice + (random.nextDouble() * (maxPrice - minPrice));
                updateAnimationDisplay(animPrice);

                if (ticks % 5 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f + (ticks * 0.02f));
                }
                ticks++;
            }
        }.runTaskTimer(main, 0L, main.getConfigManager().getGUIConfig().getInt("price_evaluation_gui.animation.update_interval", 5));
    }

    private void updateAnimationDisplay(double price) {

        ItemStack animItem = createItemFromConfig("animation", Material.GOLD_NUGGET, "&e&l🎲 Evaluating...", new ArrayList<>());

        ItemMeta meta = animItem.getItemMeta();
        if (meta != null) {
            String name = meta.hasDisplayName() ? meta.getDisplayName() : "";

            name = name.replace("%current%", String.format("%.2f", price));
            meta.setDisplayName(name);

            if (meta.hasLore()) {

                List<String> lore = meta.getLore().stream()
                        .map(line -> replacePricePlaceholders(line))
                        .map(line -> line.replace("%current%", String.format("%.2f", price)))
                        .collect(Collectors.toList());
                meta.setLore(lore);
            }
            animItem.setItemMeta(meta);
        }

        inventory.setItem(ANIMATION_SLOT, animItem);
    }

    private void completeRandomEvaluation(ItemStack originalItem, double finalPrice, boolean isMaxPrice) {
        isLocked = false;

        ItemStack pricedItem = addEvaluationInfo(originalItem.clone(), finalPrice);
        inventory.setItem(ITEM_SLOT, pricedItem);
        inventory.setItem(ANIMATION_SLOT, null);

        if (isMaxPrice) {
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
            player.sendMessage(color(getMessage("jackpot_message", "&6🎉 JACKPOT! You got the maximum price!")));
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        player.sendMessage(color(getMessage("evaluation_complete_chat", "&a✅ Price evaluation complete! Final price: &e$%price%").replace("%price%", String.format("%.2f", finalPrice))));

        ItemStack resultItem = createItemFromConfig("result", Material.EMERALD, "&a&l✅ Evaluation Complete!", new ArrayList<>());
        inventory.setItem(RESULT_SLOT, resultItem);

        this.evaluationMode = EvaluationMode.NONE;
        updateButtons();
    }

    private void applyFixedPrice() {
        isLocked = true;
        ItemStack item = inventory.getItem(ITEM_SLOT);
        if (item != null) {
            ItemStack pricedItem = addEvaluationInfo(item.clone(), minPrice);
            inventory.setItem(ITEM_SLOT, pricedItem);

            player.sendMessage(color(getMessage("evaluation_complete", "&a✅ Price set: &e$%price%").replace("%price%", String.format("%.2f", minPrice))));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            ItemStack resultItem = createItemFromConfig("result", Material.EMERALD, "&a&l✅ Evaluation Complete!", new ArrayList<>());
            inventory.setItem(RESULT_SLOT, resultItem);
        }
        isLocked = false;
        this.evaluationMode = EvaluationMode.NONE;
        updateButtons();
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String processedName = main.setPlaceholders(player, name);
            meta.setDisplayName(color(processedName));
            if (lore != null) {
                List<String> processedLore = main.setPlaceholders(player, lore);
                meta.setLore(processedLore.stream().map(this::color).collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItemFromConfig(String itemKey, Material defaultMaterial, String defaultName, List<String> defaultLore) {
        FileConfiguration guiConfig = main.getConfigManager().getGUIConfig();
        String path = "price_evaluation_gui.items." + itemKey;

        if (!guiConfig.contains(path)) {
            return createItem(defaultMaterial, defaultName, defaultLore);
        }

        Material material = Material.getMaterial(guiConfig.getString(path + ".material", defaultMaterial.name()).toUpperCase());
        String name = guiConfig.getString(path + ".name", defaultName);
        List<String> lore = guiConfig.getStringList(path + ".lore");
        if (lore.isEmpty()) {
            lore = defaultLore;
        }
        int customModelData = guiConfig.getInt(path + ".custom-model-data", -1);
        String nbtId = guiConfig.getString(path + ".nbt-id");

        name = replacePricePlaceholders(name);
        lore = lore.stream().map(this::replacePricePlaceholders).collect(Collectors.toList());

        ItemStack item = createItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (customModelData != -1) {
                meta.setCustomModelData(customModelData);
            }
            if (nbtId != null && !nbtId.isEmpty()) {
                NamespacedKey key = new NamespacedKey(main, "sellgui-nbt-id");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, nbtId);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String replacePricePlaceholders(String text) {
        if (text == null) return "";
        return text.replace("%min%", String.format("%.2f", minPrice))
                .replace("%max%", String.format("%.2f", maxPrice))
                .replace("%price%", String.format("%.2f", minPrice))
                .replace("%jackpot_chance%", String.format("%.1f", main.getConfigManager().getGUIConfig().getDouble("price_evaluation_gui.random_calculation.jackpot_chance", 20.0)));
    }

    private String getMessage(String key, String defaultMessage) {
        FileConfiguration msgConfig = main.getConfigManager().getMessagesConfig();
        String path = "price_evaluation." + key;
        return msgConfig.getString(path, defaultMessage);
    }

    private double calculateRandomPrice(double minPrice, double maxPrice) {
        return minPrice + (random.nextDouble() * (maxPrice - minPrice));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private ItemStack addEvaluationInfo(ItemStack item, double price) {
        if (item == null) return item;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            NamespacedKey worthKey = new NamespacedKey(main, "current_price");
            meta.getPersistentDataContainer().set(worthKey, PersistentDataType.DOUBLE, price);

            String evaluationLoreFormat = main.getConfigManager().getGUIConfig().getString("price_evaluation_gui.evaluation_lore_format", "&a✅ Evaluated: &f$%price%");
            String evaluationLore = evaluationLoreFormat.replace("%price%", String.format("%.2f", price));

            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            String evaluationLorePrefix = ChatColor.stripColor(main.getConfigManager().getGUIConfig().getString("price_evaluation_gui.evaluation_lore_format", "&a✅ Evaluated: &f$%price%")).replace("%price%", "").trim();

            lore.removeIf(line -> ChatColor.stripColor(line).startsWith(evaluationLorePrefix));
            lore.add("");
            lore.add(color(evaluationLore));

            meta.setLore(lore);
            item.setItemMeta(meta);
        } catch (Exception e) {
            main.getLogger().warning("Failed to add evaluation info to item: " + e.getMessage());
        }
        return item;
    }

    public void cleanup() {
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }
    }

    public boolean isLocked() {
        return isLocked;
    }

    public int getItemSlot() {
        return ITEM_SLOT;
    }

    public void returnItemToPlayer() {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        if (item != null && item.getType() != Material.AIR) {
            inventory.clear(ITEM_SLOT);
            player.getInventory().addItem(item).values().forEach(leftover ->
                    player.getWorld().dropItem(player.getLocation(), leftover)
            );
            player.sendMessage(color(getMessage("item_returned", "&eYour item has been returned to your inventory.")));
        }
    }
}
