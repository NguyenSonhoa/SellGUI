package me.aov.sellgui.utils;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemStackNormalizer {

    private static final String CURRENT_PRICE_KEY = "current_price";
    private static final String EVALUATED_KEY = "evaluated";
    private static final String WORTH_KEY = "worth";
    private static final Pattern PRICE_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final List<String> TRANSIENT_PLUGIN_KEYS = List.of(
            EVALUATED_KEY
    );

    private ItemStackNormalizer() {
    }

    public static boolean normalizePlayerInventory(SellGUIMain plugin, Player player) {
        if (plugin == null || player == null) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        boolean changed = false;

        for (ItemStack item : contents) {
            changed |= normalizeItem(plugin, item);
        }

        changed |= mergeSimilarStacks(contents);

        if (changed) {
            inventory.setStorageContents(contents);
            player.updateInventory();
        }

        return changed;
    }

    public static ItemStack normalizedCopy(SellGUIMain plugin, ItemStack item) {
        if (item == null) {
            return null;
        }

        ItemStack copy = item.clone();
        normalizeItem(plugin, copy);
        return copy;
    }

    public static boolean normalizeItem(SellGUIMain plugin, ItemStack item) {
        if (plugin == null || item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }

        if (ItemIdentifier.getItemType(item) == ItemIdentifier.ItemType.NEXO) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        boolean evaluatedItem = isEvaluatedItem(plugin, meta);
        boolean changed = evaluatedItem ? ensureEvaluatedKeys(plugin, meta) : removeTransientKeys(plugin, meta);
        if (!evaluatedItem) {
            changed |= removeEvaluationLore(plugin, meta);
        }

        if (changed) {
            applyMeta(item, meta);
        }

        return changed;
    }

    private static boolean removeTransientKeys(SellGUIMain plugin, ItemMeta meta) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        boolean changed = false;

        for (String keyName : TRANSIENT_PLUGIN_KEYS) {
            try {
                NamespacedKey key = new NamespacedKey(plugin, keyName);
                if (container.has(key)) {
                    container.remove(key);
                    changed = true;
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid legacy key names on newer servers.
            }
        }

        return changed;
    }

    private static boolean isEvaluatedItem(SellGUIMain plugin, ItemMeta meta) {
        PersistentDataContainer container = meta.getPersistentDataContainer();

        try {
            NamespacedKey currentPriceKey = new NamespacedKey(plugin, CURRENT_PRICE_KEY);
            Double currentPrice = container.get(currentPriceKey, PersistentDataType.DOUBLE);
            if (currentPrice != null && currentPrice > 0) {
                return true;
            }
        } catch (IllegalArgumentException ignored) {
        }

        try {
            if (container.has(new NamespacedKey(plugin, EVALUATED_KEY), PersistentDataType.BYTE)) {
                return true;
            }
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Double worth = container.get(new NamespacedKey(plugin, WORTH_KEY), PersistentDataType.DOUBLE);
            if (worth != null && worth > 0) {
                return true;
            }
        } catch (IllegalArgumentException ignored) {
        }

        return getEvaluationLorePrice(plugin, meta) != null;
    }

    private static boolean ensureEvaluatedKeys(SellGUIMain plugin, ItemMeta meta) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        boolean changed = false;

        try {
            NamespacedKey evaluatedKey = new NamespacedKey(plugin, EVALUATED_KEY);
            if (!container.has(evaluatedKey, PersistentDataType.BYTE)) {
                container.set(evaluatedKey, PersistentDataType.BYTE, (byte) 1);
                changed = true;
            }
        } catch (IllegalArgumentException ignored) {
        }

        try {
            NamespacedKey currentPriceKey = new NamespacedKey(plugin, CURRENT_PRICE_KEY);
            if (!container.has(currentPriceKey, PersistentDataType.DOUBLE)) {
                Double lorePrice = getEvaluationLorePrice(plugin, meta);
                if (lorePrice != null && lorePrice > 0) {
                    container.set(currentPriceKey, PersistentDataType.DOUBLE, lorePrice);
                    changed = true;
                }
            }
        } catch (IllegalArgumentException ignored) {
        }

        return changed;
    }

    private static boolean removeEvaluationLore(SellGUIMain plugin, ItemMeta meta) {
        if (!meta.hasLore()) {
            return false;
        }

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return false;
        }

        List<String> cleaned = new ArrayList<>(lore.size());
        boolean changed = false;
        String evaluationPrefix = getEvaluationLorePrefix(plugin);

        for (String line : lore) {
            if (isEvaluationLoreLine(line, evaluationPrefix)) {
                removePreviousBlankLine(cleaned);
                changed = true;
                continue;
            }

            cleaned.add(line);
        }

        if (changed) {
            meta.setLore(cleaned.isEmpty() ? null : cleaned);
        }

        return changed;
    }

    private static Double getEvaluationLorePrice(SellGUIMain plugin, ItemMeta meta) {
        if (!meta.hasLore()) {
            return null;
        }

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return null;
        }

        String evaluationPrefix = getEvaluationLorePrefix(plugin);
        for (String line : lore) {
            if (!isEvaluationLoreLine(line, evaluationPrefix)) {
                continue;
            }

            Matcher matcher = PRICE_PATTERN.matcher(stripColor(line));
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group());
                } catch (NumberFormatException ignored) {
                }
            }
            return 0.0;
        }

        return null;
    }

    private static String getEvaluationLorePrefix(SellGUIMain plugin) {
        String template = "&aEvaluated: &f$%price%";

        try {
            if (plugin.getConfigManager() != null && plugin.getConfigManager().getGUIConfig() != null) {
                template = plugin.getConfigManager().getGUIConfig()
                        .getString("price_evaluation_gui.evaluation_lore_format", template);
            }
        } catch (Exception ignored) {
        }

        String marker = "%price%";
        String prefix = template;
        int markerIndex = template.indexOf(marker);
        if (markerIndex >= 0) {
            prefix = template.substring(0, markerIndex);
        }

        return stripColor(ColorUtils.color(prefix)).trim();
    }

    private static boolean isEvaluationLoreLine(String line, String configuredPrefix) {
        String stripped = stripColor(line).trim();
        if (stripped.isEmpty()) {
            return false;
        }

        return (!configuredPrefix.isEmpty() && stripped.startsWith(configuredPrefix))
                || stripped.startsWith("Evaluated:");
    }

    private static void removePreviousBlankLine(List<String> lore) {
        if (lore.isEmpty()) {
            return;
        }

        String previous = stripColor(lore.get(lore.size() - 1)).trim();
        if (previous.isEmpty()) {
            lore.remove(lore.size() - 1);
        }
    }

    private static String stripColor(String text) {
        if (text == null) {
            return "";
        }

        String stripped = ChatColor.stripColor(text);
        return stripped == null ? "" : stripped;
    }

    private static void applyMeta(ItemStack item, ItemMeta meta) {
        if (isDefaultMeta(item, meta)) {
            try {
                item.setItemMeta(null);
                return;
            } catch (IllegalArgumentException | NullPointerException ignored) {
            }
        }

        item.setItemMeta(meta);
    }

    private static boolean isDefaultMeta(ItemStack item, ItemMeta meta) {
        try {
            ItemMeta defaultMeta = Bukkit.getItemFactory().getItemMeta(item.getType());
            return Bukkit.getItemFactory().equals(meta, defaultMeta);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean mergeSimilarStacks(ItemStack[] contents) {
        boolean changed = false;

        for (int sourceSlot = 0; sourceSlot < contents.length; sourceSlot++) {
            ItemStack source = contents[sourceSlot];
            if (source == null || source.getType() == Material.AIR || source.getAmount() <= 0) {
                continue;
            }

            int maxStackSize = source.getMaxStackSize();
            if (maxStackSize <= 1) {
                continue;
            }

            for (int targetSlot = 0; targetSlot < sourceSlot; targetSlot++) {
                ItemStack target = contents[targetSlot];
                if (target == null || target.getType() == Material.AIR || target.getAmount() >= target.getMaxStackSize()) {
                    continue;
                }

                if (!target.isSimilar(source)) {
                    continue;
                }

                int movable = Math.min(source.getAmount(), target.getMaxStackSize() - target.getAmount());
                if (movable <= 0) {
                    continue;
                }

                target.setAmount(target.getAmount() + movable);
                source.setAmount(source.getAmount() - movable);
                changed = true;

                if (source.getAmount() <= 0) {
                    contents[sourceSlot] = null;
                    break;
                }
            }
        }

        return changed;
    }
}
