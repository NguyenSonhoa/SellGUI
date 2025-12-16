package me.aov.sellgui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import javax.annotation.Nullable;

import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTCompound;
import io.lumine.mythic.lib.api.item.NBTItem;
import me.aov.sellgui.commands.SellCommand;
import me.aov.sellgui.commands.SellCommand;
import me.aov.sellgui.managers.PriceManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;

public class SellGUIAPI {
    private SellGUIMain main;

    public SellGUIAPI(SellGUIMain sellGUIMain) {
        this.main = sellGUIMain;
    }

    public double getPrice(ItemStack itemStack, @Nullable Player player) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return 0.0;
        }

        double price = 0.0;

        if (itemStack.hasItemMeta()) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                NamespacedKey key = new NamespacedKey(main, "current_price");
                if (meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
                    price = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
                }
            }
        }

        if (price == 0) {
            PriceManager priceManager = new PriceManager(main);
            price = priceManager.getItemPrice(itemStack);

            if (price == 0) {
                if (this.main.hasEssentials() && this.main.getConfig().getBoolean("use-essentials-price")) {
                    if (this.main.getEssentialsHolder() != null && this.main.getEssentialsHolder().getEssentials() != null) {
                        BigDecimal essentialsPriceBd = this.main.getEssentialsHolder().getPrice(itemStack);
                        if (essentialsPriceBd != null) {
                            double essentialsPrice = round(essentialsPriceBd.doubleValue(),
                                    this.main.getConfig().getInt("places-to-round", 2));
                            if (essentialsPrice > 0) {
                                price = essentialsPrice;
                            }
                        }
                    }
                }
                if (price == 0 && this.main.getItemPricesConfig() != null && this.main.getItemPricesConfig().contains(itemStack.getType().name())) {
                    price = this.main.getItemPricesConfig().getDouble(itemStack.getType().name());
                }
            }
        }

        if (price > 0) {
            return applyPlayerBonuses(price, player);
        }

        return round(price, this.main.getConfig().getInt("places-to-round", 2));
    }

    private double applyPlayerBonuses(double price, @Nullable Player player) {
        if (player == null || price <= 0) {
            return round(price, this.main.getConfig().getInt("places-to-round", 2));
        }

        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            String perm = pai.getPermission();
            if (pai.getValue()) {
                if (perm.startsWith("sellgui.bonus.")) {
                    try {
                        double bonusValue = Double.parseDouble(perm.substring("sellgui.bonus.".length()));
                        price += bonusValue;
                    } catch (NumberFormatException | IndexOutOfBoundsException ignored) {}
                } else if (perm.startsWith("sellgui.multiplier.")) {
                    try {
                        double multiplierValue = Double.parseDouble(perm.substring("sellgui.multiplier.".length()));
                        if (multiplierValue > 0) {
                            price *= multiplierValue;
                        }
                    } catch (NumberFormatException | IndexOutOfBoundsException ignored) {}
                }
            }
        }

        return round(price, this.main.getConfig().getInt("places-to-round", 2));
    }

    public double getPurePrice(ItemStack itemStack) {
        double price = 0.0;
        if (itemStack != null && itemStack.getType() != Material.AIR && this.main.getItemPricesConfig() != null &&
                this.main.getItemPricesConfig().contains(itemStack.getType().name())) {
            price = this.main.getItemPricesConfig().getDouble(itemStack.getType().name());
        }
        return price;
    }

    public void openSellGUI(Player player) {
        SellCommand.getSellGUIs().add(new SellGUI(this.main, player, this.main.getItemNBTManager()));
    }

    private static double round(double value, int places) {
        if (places < 0) {
            return value;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        try {
            BigDecimal bd = BigDecimal.valueOf(value);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
            return bd.doubleValue();
        } catch (NumberFormatException e) {
            return value;
        }
    }
}