package me.aov.sellgui.handlers;

import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.managers.PriceManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class SellGUIPlaceholderExpansion extends PlaceholderExpansion {

    private final SellGUIMain main;

    public SellGUIPlaceholderExpansion(SellGUIMain main) {
        this.main = main;
    }

    @Override
    public String getIdentifier() {
        return "sellgui";
    }

    @Override
    public String getAuthor() {
        return "SaneNuyan";
    }

    @Override
    public String getVersion() {
        return "2.5.11"; // Keep the version consistent
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return "";

        if (identifier.equals("pricehand")) {
            ItemStack item = player.getInventory().getItemInMainHand();
            return String.valueOf(getPrice(item, player));
        }

        if (identifier.equals("pricehandfull")) {
            ItemStack item = player.getInventory().getItemInMainHand();
            String itemName = item.getType().name();
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                itemName = item.getItemMeta().getDisplayName();
            }
            return itemName + " - " + getPrice(item, player);
        }
        return null;
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
