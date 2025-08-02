package me.aov.sellgui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import javax.annotation.Nullable;

import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTCompound;
import io.lumine.mythic.lib.api.item.NBTItem;
import me.aov.sellgui.commands.SellCommand;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

public class SellGUIAPI extends PlaceholderExpansion {
    private SellGUIMain main;

    public SellGUIAPI(SellGUIMain sellGUIMain) {
        this.main = sellGUIMain;
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
        return "2.2";
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    public void registerExpansion() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.register();
        }
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
        double price = 0.0;

        if (itemStack == null || itemStack.getType() == Material.AIR) {
            if (this.main.shouldRoundPrices()) {
                price = Math.round(price);
            }
            return price;
        }
        try {
            NBTItem nbtItem = new NBTItem(itemStack) {
                @Override
                public Object get(String s) {
                    return null;
                }

                @Override
                public String getString(String s) {
                    return "";
                }

                @Override
                public boolean hasTag(String s) {
                    return false;
                }

                @Override
                public boolean getBoolean(String s) {
                    return false;
                }

                @Override
                public double getDouble(String s) {
                    return 0;
                }

                @Override
                public int getInteger(String s) {
                    return 0;
                }

                @Override
                public NBTCompound getNBTCompound(String s) {
                    return null;
                }

                @Override
                public NBTItem addTag(List<ItemTag> list) {
                    return null;
                }

                @Override
                public NBTItem removeTag(String... strings) {
                    return null;
                }

                @Override
                public Set<String> getTags() {
                    return Set.of();
                }

                @Override
                public ItemStack toItem() {
                    return null;
                }

                @Override
                public int getTypeId(String s) {
                    return 0;
                }
            };
            if (nbtItem.hasTag("sellgui:price")) {
                price = nbtItem.getDouble("sellgui:price");
                if (price > 0) {
                    return applyPlayerBonuses(price, player);
                }
            }

            if (this.main.hasNexo) {
                if (nbtItem.hasTag("nexo:id")) {
                    String nexoId = nbtItem.getString("nexo:id");
                    if (nexoId != null && !nexoId.isEmpty()) {
                        Map<String, Double> nexoPrices = this.main.getLoadedNexoPrices();
                        if (nexoPrices != null && nexoPrices.containsKey(nexoId)) {
                            price = nexoPrices.get(nexoId);
                        }
                    }
                }
            }

            if (price == 0 && main.isMMOItemsEnabled()) {
                if (nbtItem.hasTag("MMOITEMS_ITEM_ID")) {
                    String mmoItemType = nbtItem.getType();
                    String mmoItemId = nbtItem.getString("MMOITEMS_ITEM_ID");

                    if (mmoItemType != null && !mmoItemType.isEmpty() && mmoItemId != null && !mmoItemId.isEmpty()) {
                        String fullItemId = mmoItemType.toUpperCase() + "." + mmoItemId.toUpperCase();

                        Map<String, Double> mmoPrices = this.main.getLoadedMMOItemPrices();
                        if (mmoPrices != null && mmoPrices.containsKey(fullItemId)) {
                            price = mmoPrices.get(fullItemId);
                        }
                    }
                }
            }
        } catch (Exception e) {

            this.main.getLogger().warning("Failed to read NBT data: " + e.getMessage());
        }

        if (price == 0 && this.main.hasEssentials() && this.main.getConfig().getBoolean("use-essentials-price")) {
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
        if (price > 0) {
            return applyPlayerBonuses(price, player);
        }

        return 0.0;
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
        SellCommand.getSellGUIs().add(new SellGUI(this.main, player));
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