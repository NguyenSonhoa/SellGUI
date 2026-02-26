package me.aov.sellgui.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.aov.sellgui.SellGUIMain;
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

public class PacketEventsPacketListener extends PacketListenerAbstract {

    private final SellGUIMain main;

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
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            handleWindowClick(event);
        }
    }

    private void handleWindowClick(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
        int windowId = wrapper.getWindowId();

        if (windowId != 0) return;

        int stateId = wrapper.getStateId().orElse(0);

        int button = wrapper.getButton();
        int slot = wrapper.getSlot();

        if (button >= 0 && button <= 8 && slot >= 9 && slot <= 44) {

            main.getServer().getScheduler().runTaskLater(main, () -> {

                updateItemAtProtocolSlot(player, slot, stateId);
                int hotbarProtocolSlot = button + 36;
                updateItemAtProtocolSlot(player, hotbarProtocolSlot, stateId);
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

        if (protocolSlot >= 9 && protocolSlot <= 35) {
            return player.getInventory().getItem(protocolSlot);
        } else if (protocolSlot >= 36 && protocolSlot <= 44) {
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

        if (isGuiItem(bukkitItem)) {
            return item;
        }

        double price = calculatePrice(bukkitItem, player) * bukkitItem.getAmount();
        ItemMeta meta = bukkitItem.getItemMeta();

        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            String worthLineTemplate = main.getMessagesConfig().getString("sell.lore_worth", "&7Worth: &a$%price%");
            final String worthPrefix;
            if (worthLineTemplate.contains("%price%")) {
                String[] split = worthLineTemplate.split("%price%");
                if (split.length > 0) {
                    worthPrefix = ColorUtils.stripColor(split[0]);
                } else {
                    worthPrefix = "";
                }
            } else {
                worthPrefix = "";
            }

            boolean removed = lore.removeIf(line -> !worthPrefix.isEmpty() && ColorUtils.stripColor(line).startsWith(worthPrefix));

            boolean wasModified = false;

            if (price > 0) {
                String worthLine = worthLineTemplate.replace("%price%", String.format("%.2f", price));
                lore.add(ColorUtils.color(worthLine));
                wasModified = true;
            }

            if (wasModified || removed) {
                meta.setLore(lore);
                bukkitItem.setItemMeta(meta);
                if (modifiedFlag != null) {
                    modifiedFlag[0] = true;
                }
                return SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
            }
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

    private double calculatePrice(org.bukkit.inventory.ItemStack itemStack, Player player) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return 0.0;
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
            } else if (this.main.getItemPricesConfig().contains(itemToPrice.getType().name())) {
                itemPrice = BigDecimal.valueOf(this.main.getItemPricesConfig().getDouble(itemToPrice.getType().name()));
            } else if (player != null) {
                Plugin shopGuiPlus = this.main.getServer().getPluginManager().getPlugin("ShopGuiPlus");
                if (shopGuiPlus != null && shopGuiPlus.isEnabled()) {
                    try {
                        double shopGuiPrice = net.brcdev.shopgui.ShopGuiPlusApi.getItemStackPriceSell(player, itemToPrice);
                        if (shopGuiPrice > 0) {
                            itemPrice = BigDecimal.valueOf(shopGuiPrice);
                        }
                    } catch (NoClassDefFoundError | Exception e) {

                    }
                }
            }
        }

        if (main.getRandomPriceManager() != null && !main.getRandomPriceManager().canBeSold(itemToPrice)) {
            return 0.0;
        }

        BigDecimal contentsPrice = BigDecimal.ZERO;
        if (itemStack.getType().name().endsWith("_SHULKER_BOX") && itemStack.getItemMeta() instanceof BlockStateMeta meta) {
            if (meta.getBlockState() instanceof ShulkerBox shulker) {
                for (org.bukkit.inventory.ItemStack contained : shulker.getInventory().getContents()) {
                    if (contained != null && !contained.getType().isAir()) {
                        contentsPrice = contentsPrice.add(BigDecimal.valueOf(calculatePrice(contained, player)).multiply(BigDecimal.valueOf(contained.getAmount())));
                    }
                }
            }
        }

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
                try {
                    String percentStr = pai.getPermission().substring("sellgui.bonus.".length());
                    bonusPercent = bonusPercent.add(new BigDecimal(percentStr));
                } catch (NumberFormatException e) {

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

