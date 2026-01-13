package me.aov.sellgui.listeners;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.managers.PriceManager;
import me.aov.sellgui.utils.ColorUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigDecimal;
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

    private void handleWindowItems(PacketSendEvent event) {
        Player player = (Player) event.getPlayer();
        if (player == null) return;

        WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
        List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = wrapper.getItems();
        boolean modified = false;
        List<com.github.retrooper.packetevents.protocol.item.ItemStack> newItems = new ArrayList<>();

        for (com.github.retrooper.packetevents.protocol.item.ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                newItems.add(item);
                continue;
            }
            
            ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(item);
            
            if (isGuiItem(bukkitItem)) {
                newItems.add(item);
                continue;
            }

            double price = calculatePrice(bukkitItem, player) * bukkitItem.getAmount();

            if (price > 0) {
                ItemMeta meta = bukkitItem.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                    String worthLine = main.getMessagesConfig().getString("sell.lore_worth", "&7Worth: &a$%price%");
                    worthLine = worthLine.replace("%price%", String.format("%.2f", price));
                    lore.add(ColorUtils.color(worthLine));
                    meta.setLore(lore);
                    bukkitItem.setItemMeta(meta);
                    
                    newItems.add(SpigotConversionUtil.fromBukkitItemStack(bukkitItem));
                    modified = true;
                } else {
                    newItems.add(item);
                }
            } else {
                newItems.add(item);
            }
        }

        if (modified) {
            wrapper.setItems(newItems);
            event.markForReEncode(true);
        }
    }

    private void handleSetSlot(PacketSendEvent event) {
        Player player = (Player) event.getPlayer();
        if (player == null) return;

        WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
        com.github.retrooper.packetevents.protocol.item.ItemStack item = wrapper.getItem();

        if (item == null || item.isEmpty()) return;

        ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(item);
        
        if (isGuiItem(bukkitItem)) return;

        double price = calculatePrice(bukkitItem, player) * bukkitItem.getAmount();

        if (price > 0) {
            ItemMeta meta = bukkitItem.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                String worthLine = main.getMessagesConfig().getString("sell.lore_worth", "&7Worth: &a$%price%");
                worthLine = worthLine.replace("%price%", String.format("%.2f", price));
                lore.add(ColorUtils.color(worthLine));
                meta.setLore(lore);
                bukkitItem.setItemMeta(meta);
                
                wrapper.setItem(SpigotConversionUtil.fromBukkitItemStack(bukkitItem));
                event.markForReEncode(true);
            }
        }
    }

    private boolean isGuiItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey guiKey = new NamespacedKey(this.main, "sellgui");
        return meta.getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE);
    }

    private double calculatePrice(ItemStack itemStack, Player player) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return 0.0;
        }

        BigDecimal itemPrice = BigDecimal.ZERO;
        ItemStack itemToPrice = itemStack.clone();
        itemToPrice.setAmount(1);

        // Check for pre-calculated price in NBT
        if (itemToPrice.hasItemMeta()) {
            ItemMeta meta = itemToPrice.getItemMeta();
            if (meta != null) {
                NamespacedKey key = new NamespacedKey(main, "current_price");
                if (meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
                    itemPrice = BigDecimal.valueOf(meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE));
                }
            }
        }

        // If no NBT price, calculate it
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
            } else if (player != null && this.main.getServer().getPluginManager().getPlugin("ShopGuiPlus") != null && this.main.getServer().getPluginManager().getPlugin("ShopGuiPlus").isEnabled()) {
                try {
                    double shopGuiPrice = net.brcdev.shopgui.ShopGuiPlusApi.getItemStackPriceSell(player, itemToPrice);
                    if (shopGuiPrice > 0) {
                        itemPrice = BigDecimal.valueOf(shopGuiPrice);
                    }
                } catch (NoClassDefFoundError e) {
                    // ShopGuiPlusApi class not found
                } catch (Exception e) {
                    // Error getting price
                }
            }
        }

        // Handle RandomPriceManager
        if (main.getRandomPriceManager() != null && !main.getRandomPriceManager().canBeSold(itemToPrice)) {
            return 0.0;
        }

        // Calculate shulker box contents price
        BigDecimal contentsPrice = BigDecimal.ZERO;
        if (itemStack.getType().name().endsWith("_SHULKER_BOX") && itemStack.hasItemMeta() && itemStack.getItemMeta() instanceof BlockStateMeta) {
            BlockStateMeta meta = (BlockStateMeta) itemStack.getItemMeta();
            if (meta.getBlockState() instanceof ShulkerBox) {
                ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
                for (ItemStack contained : shulker.getInventory().getContents()) {
                    if (contained != null && !contained.getType().isAir()) {
                        contentsPrice = contentsPrice.add(BigDecimal.valueOf(calculatePrice(contained, player)).multiply(BigDecimal.valueOf(contained.getAmount())));
                    }
                }
            }
        }

        BigDecimal totalPrice = itemPrice.add(contentsPrice);

        // Apply permission bonuses
        if (totalPrice.compareTo(BigDecimal.ZERO) > 0) {
            totalPrice = applyPermissionBonuses(player, totalPrice);
        }

        return totalPrice.doubleValue();
    }

    private BigDecimal applyPermissionBonuses(Player player, BigDecimal price) {
        if (player == null || price.compareTo(BigDecimal.ZERO) <= 0) return price;

        BigDecimal bonusPercent = BigDecimal.ZERO;

        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            if (pai.getPermission().startsWith("sellgui.bonus.") && pai.getValue()) {
                try {
                    String percentStr = pai.getPermission().substring("sellgui.bonus.".length());
                    bonusPercent = bonusPercent.add(new BigDecimal(percentStr));
                } catch (NumberFormatException e) {
                    // Invalid format
                }
            }
        }

        if (bonusPercent.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal multiplier = BigDecimal.ONE.add(bonusPercent.divide(new BigDecimal("100")));
            price = price.multiply(multiplier);
        }

        return price;
    }
}