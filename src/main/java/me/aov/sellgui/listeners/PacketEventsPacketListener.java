package me.aov.sellgui.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.config.ConfigManager;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PacketEventsPacketListener extends PacketListenerAbstract {

    private final SellGUIMain main;
    private final Map<Player, String> openGuiTitles = new ConcurrentHashMap<>();

    public PacketEventsPacketListener(SellGUIMain main) {
        super(PacketListenerPriority.HIGH);
        this.main = main;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            handleWindowItems(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            handleSetSlot(event);
        } else if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
            handleOpenWindow(event);
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            handleWindowClick(event);
        } else if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            handleCloseWindow(event);
        }
    }

    private void handleOpenWindow(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        WrapperPlayServerOpenWindow wrapper = new WrapperPlayServerOpenWindow(event);

        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer serializer = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        String plainTitle = serializer.serialize(wrapper.getTitle());

        String cleanTitle = ConfigManager.stripColorCodes(plainTitle);
        openGuiTitles.put(player, cleanTitle);
    }

    private void handleCloseWindow(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openGuiTitles.remove(player);
    }

    private void handleWindowClick(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
        int windowId = wrapper.getWindowId();

        // We need to update the inventory after a click to ensure worth lore is updated
        main.getServer().getScheduler().runTaskLater(main, player::updateInventory, 1L);

        int stateId = wrapper.getStateId().orElse(0);

        int button = wrapper.getButton();
        int slot = wrapper.getSlot();

        // If clicking in hotbar (0-8) while inventory is open (slots 9-44)
        if (button >= 0 && button <= 8 && slot >= 9 && slot <= 44) {
            main.getServer().getScheduler().runTaskLater(main, () -> {
                updateItemAtProtocolSlot(player, slot, stateId); // Update the clicked slot
                int hotbarProtocolSlot = button + 36; // Calculate hotbar slot
                updateItemAtProtocolSlot(player, hotbarProtocolSlot, stateId); // Update the hotbar slot
            }, 1L);
        } else {
            main.getServer().getScheduler().runTaskLater(main, () -> updateItemAtProtocolSlot(player, slot, stateId), 1L);
        }
    }

    private void updateItemAtProtocolSlot(Player player, int protocolSlot, int stateId) {
        org.bukkit.inventory.ItemStack bukkitItem = getItemFromProtocolSlot(player, protocolSlot);

        if (bukkitItem != null && bukkitItem.getType() != Material.AIR) {
            ItemStack packetEventsItem = SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
            boolean[] modified = {false};
            ItemStack processedItem = processItem(packetEventsItem, player, modified);

            if (modified[0]) {
                WrapperPlayServerSetSlot setSlotWrapper = new WrapperPlayServerSetSlot(0, stateId, protocolSlot, processedItem);
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, setSlotWrapper);
            }
        }
    }

    private org.bukkit.inventory.ItemStack getItemFromProtocolSlot(Player player, int protocolSlot) {
        // Protocol slots for player inventory:
        // 0-8: Hotbar
        // 9-35: Main inventory
        // 36-39: Armor slots
        // 40: Offhand
        // For simplicity, we'll focus on main inventory and hotbar for now.
        if (protocolSlot >= 9 && protocolSlot <= 35) { // Main inventory
            return player.getInventory().getItem(protocolSlot);
        } else if (protocolSlot >= 36 && protocolSlot <= 44) { // Hotbar (protocol 36-44 maps to bukkit 0-8)
            return player.getInventory().getItem(protocolSlot - 36);
        }
        return null;
    }

    private void handleWindowItems(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
        List<ItemStack> items = wrapper.getItems();
        boolean[] modified = {false};
        List<ItemStack> newItems = new ArrayList<>();

        for (ItemStack item : items) {
            newItems.add(processItem(item, player, modified));
        }

        if (modified[0]) {
            wrapper.setItems(newItems);
        }
    }

    private void handleSetSlot(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);

        boolean[] modified = {false};
        ItemStack processedItem = processItem(wrapper.getItem(), player, modified);

        if (modified[0]) {
            wrapper.setItem(processedItem);
        }
    }

    @SuppressWarnings("deprecation")
    private ItemStack processItem(ItemStack item, Player player, boolean[] modifiedFlag) {
        if (item == null || item.isEmpty()) {
            return item;
        }

        org.bukkit.inventory.ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(item);

        // Don't process special GUI items
        if (isGuiItem(bukkitItem)) {
            return item;
        }

        ItemMeta meta = bukkitItem.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String worthLineTemplate = main.getMessagesConfig().getString("sell.lore_worth", "&7Worth: &a$%price%");
        final String worthPrefix;
        if (worthLineTemplate.contains("%price%")) {
            String[] split = worthLineTemplate.split("%price%");
            worthPrefix = (split.length > 0) ? ColorUtils.stripColor(split[0]) : "";
        } else {
            worthPrefix = "";
        }

        // Step 1: Always try to remove any existing worth lore
        boolean removed = lore.removeIf(line -> !worthPrefix.isEmpty() && ColorUtils.stripColor(line).startsWith(worthPrefix));
        boolean added = false;

        // Step 2: Check if we should add a new worth lore
        String currentGuiTitle = openGuiTitles.get(player);
        boolean isBlacklisted = currentGuiTitle != null && main.getConfigManager().getWorthLoreBlacklistGuiTitles().contains(currentGuiTitle);

        if (!isBlacklisted) {
            if (isShulkerBox(bukkitItem) && bukkitItem.getItemMeta() instanceof BlockStateMeta) {
                BigDecimal itemPrice = getBaseItemPrice(bukkitItem, player);
                BigDecimal contentsPrice = getShulkerContentsPrice(bukkitItem, player);
                BigDecimal totalValue = itemPrice.add(contentsPrice);

                if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal finalTotalValue = applyPermissionBonuses(player, totalValue).multiply(BigDecimal.valueOf(bukkitItem.getAmount()));

                    String worthLine = worthLineTemplate.replace("%price%", main.getConfigManager().formatNumber(finalTotalValue));
                    lore.add(ColorUtils.color(worthLine));
                    added = true;
                }
            } else {
                double price = calculatePrice(bukkitItem, player) * bukkitItem.getAmount();
                if (price > 0) {
                    String worthLine = worthLineTemplate.replace("%price%", main.getConfigManager().formatNumber(price));
                    lore.add(ColorUtils.color(worthLine));
                    added = true;
                }
            }
        }

        // Step 3: If anything changed, update the item
        if (removed || added) {
            meta.setLore(lore);
            bukkitItem.setItemMeta(meta);
            if (modifiedFlag != null) {
                modifiedFlag[0] = true;
            }
            return SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
        }

        return item;
    }

    private boolean isGuiItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        NamespacedKey guiKey = new NamespacedKey(this.main, "sellgui");
        return meta.getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE);
    }

    private boolean isShulkerBox(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType() == Material.SHULKER_BOX ||
                item.getType() == Material.WHITE_SHULKER_BOX ||
                item.getType() == Material.ORANGE_SHULKER_BOX ||
                item.getType() == Material.MAGENTA_SHULKER_BOX ||
                item.getType() == Material.LIGHT_BLUE_SHULKER_BOX ||
                item.getType() == Material.YELLOW_SHULKER_BOX ||
                item.getType() == Material.LIME_SHULKER_BOX ||
                item.getType() == Material.PINK_SHULKER_BOX ||
                item.getType() == Material.GRAY_SHULKER_BOX ||
                item.getType() == Material.LIGHT_GRAY_SHULKER_BOX ||
                item.getType() == Material.CYAN_SHULKER_BOX ||
                item.getType() == Material.PURPLE_SHULKER_BOX ||
                item.getType() == Material.BLUE_SHULKER_BOX ||
                item.getType() == Material.BROWN_SHULKER_BOX ||
                item.getType() == Material.GREEN_SHULKER_BOX ||
                item.getType() == Material.RED_SHULKER_BOX ||
                item.getType() == Material.BLACK_SHULKER_BOX;
    }

    private BigDecimal getBaseItemPrice(org.bukkit.inventory.ItemStack itemStack, Player player) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return BigDecimal.ZERO;
        }

        BigDecimal itemPrice = BigDecimal.ZERO;
        org.bukkit.inventory.ItemStack itemToPrice = itemStack.clone();
        itemToPrice.setAmount(1);

        if (itemToPrice.hasItemMeta()) {
            ItemMeta meta = itemToPrice.getItemMeta();
            if (meta != null) {
                NamespacedKey key = new NamespacedKey(main, "current_price");
                if (meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
                    Double price = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
                    if (price != null) {
                        itemPrice = BigDecimal.valueOf(price);
                    }
                }
            }
        }

        if (itemPrice.compareTo(BigDecimal.ZERO) == 0) {
            PriceManager priceManager = new PriceManager(main);
            double price = priceManager.getItemPriceWithPlayer(itemToPrice, player);
            if (price > 0) {
                itemPrice = BigDecimal.valueOf(price);
            } else if (this.main.hasEssentials() && this.main.getConfig().getBoolean("use-essentials-price")) {
                BigDecimal essentialsPrice = this.main.getEssentialsHolder().getPrice(itemToPrice);
                if (essentialsPrice.compareTo(BigDecimal.ZERO) > 0) {
                    itemPrice = essentialsPrice;
                }
            } else {
                String key = itemToPrice.getType().name();
                if (isShulkerBox(itemToPrice)) {
                    key = "SHULKER_BOX";
                }
                itemPrice = BigDecimal.valueOf(this.main.getItemPricesConfig().getDouble(key, 0.0));
            }
            if (itemPrice.compareTo(BigDecimal.ZERO) == 0 && player != null) {
                Plugin shopGuiPlus = this.main.getServer().getPluginManager().getPlugin("ShopGuiPlus");
                if (shopGuiPlus != null && shopGuiPlus.isEnabled()) {
                    try {
                        double shopGuiPrice = net.brcdev.shopgui.ShopGuiPlusApi.getItemStackPriceSell(player, itemToPrice);
                        if (shopGuiPrice > 0) {
                            itemPrice = BigDecimal.valueOf(shopGuiPrice);
                        }
                    } catch (NoClassDefFoundError | Exception e) {
                        // Ignore if ShopGUIPlus API is not available or throws an error
                    }
                }
            }
        }

        if (main.getRandomPriceManager() != null && !main.getRandomPriceManager().canBeSold(itemToPrice)) {
            return BigDecimal.ZERO;
        }

        return itemPrice;
    }

    private BigDecimal getShulkerContentsPrice(org.bukkit.inventory.ItemStack itemStack, Player player) {
        BigDecimal contentsPrice = BigDecimal.ZERO;
        if (isShulkerBox(itemStack) && itemStack.getItemMeta() instanceof BlockStateMeta meta) {
            if (meta.getBlockState() instanceof ShulkerBox shulker) {
                for (org.bukkit.inventory.ItemStack contained : shulker.getInventory().getContents()) {
                    if (contained != null && !contained.getType().isAir()) {
                        contentsPrice = contentsPrice.add(getBaseItemPrice(contained, player).multiply(BigDecimal.valueOf(contained.getAmount())));
                    }
                }
            }
        }
        return contentsPrice;
    }

    private double calculatePrice(org.bukkit.inventory.ItemStack itemStack, Player player) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return 0.0;
        }

        BigDecimal itemPrice = getBaseItemPrice(itemStack, player);
        BigDecimal contentsPrice = getShulkerContentsPrice(itemStack, player);

        BigDecimal totalPrice = itemPrice.add(contentsPrice);

        if (totalPrice.compareTo(BigDecimal.ZERO) > 0) {
            totalPrice = applyPermissionBonuses(player, totalPrice);
        }

        return totalPrice.doubleValue();
    }

    private BigDecimal getBonusPercent(Player player) {
        BigDecimal bonusPercent = BigDecimal.ZERO;
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            if (pai.getPermission().startsWith("sellgui.bonus.") && pai.getValue()) {
                if (player.isOp() && pai.getAttachment() == null) {
                    continue; 
                }

                try {
                    String percentStr = pai.getPermission().substring("sellgui.bonus.".length());
                    bonusPercent = bonusPercent.add(new BigDecimal(percentStr));
                } catch (NumberFormatException e) {
                    // Ignore invalid bonus permissions
                }
            }
        }
        return bonusPercent;
    }

    private BigDecimal applyPermissionBonuses(Player player, BigDecimal price) {
        if (player == null || price.compareTo(BigDecimal.ZERO) <= 0) return price;

        BigDecimal bonusPercent = getBonusPercent(player);

        if (bonusPercent.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal multiplier = BigDecimal.ONE.add(bonusPercent.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            price = price.multiply(multiplier);
        }

        return price;
    }
}
