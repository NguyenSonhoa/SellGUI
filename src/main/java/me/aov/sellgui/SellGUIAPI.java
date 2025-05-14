package me.aov.sellgui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
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
            return price;
        }

        if (main.isMMOItemsEnabled()) {
            NBTItem nbtItem = NBTItem.get(itemStack);
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

        if (this.main.getConfig().getBoolean("sell-all-command-sell-enchanted", false) || itemStack.getEnchantments().isEmpty()) {
            List<String> flatBonus = new ArrayList<>();
            if (this.main.getItemPricesConfig() != null && this.main.getItemPricesConfig().isList("flat-enchantment-bonus")) {
                flatBonus.addAll(this.main.getItemPricesConfig().getStringList("flat-enchantment-bonus"));
            }

            List<String> multiplierBonus = new ArrayList<>();
            if (this.main.getItemPricesConfig() != null && this.main.getItemPricesConfig().isList("multiplier-enchantment-bonus")) {
                multiplierBonus.addAll(this.main.getItemPricesConfig().getStringList("multiplier-enchantment-bonus"));
            }

            boolean isEssentialsPriceSource = this.main.hasEssentials() &&
                    this.main.getConfig().getBoolean("use-essentials-price") &&
                    price > 0 &&
                    this.main.getEssentialsHolder() != null &&
                    this.main.getEssentialsHolder().getEssentials() != null &&
                    (this.main.getEssentialsHolder().getPrice(itemStack) != null && this.main.getEssentialsHolder().getPrice(itemStack).doubleValue() > 0);

            if (isEssentialsPriceSource && !this.main.getConfig().getBoolean("use-permission-bonuses-on-essentials", true)) {
                return round(price, this.main.getConfig().getInt("places-to-round", 2));
            } else {
                if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasEnchants()) {
                    for (Map.Entry<Enchantment, Integer> entry : itemStack.getEnchantments().entrySet()) {
                        Enchantment enchantment = entry.getKey();
                        int level = entry.getValue();
                        String enchantmentKey = enchantment.getKey().getKey();

                        for (String s : flatBonus) {
                            String[] temp = s.split(":");
                            if (temp.length == 3 && temp[0].equalsIgnoreCase(enchantmentKey) && temp[1].equalsIgnoreCase(String.valueOf(level))) {
                                try {
                                    price += Double.parseDouble(temp[2]);
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                        for (String s : multiplierBonus) {
                            String[] temp = s.split(":");
                            if (temp.length == 3 && temp[0].equalsIgnoreCase(enchantmentKey) && temp[1].equalsIgnoreCase(String.valueOf(level))) {
                                try {
                                    price *= Double.parseDouble(temp[2]);
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }

                if (player != null) {
                    for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
                        String perm = pai.getPermission();
                        if (pai.getValue()) {
                            if (perm.startsWith("sellgui.bonus.")) {
                                if (price != 0.0) {
                                    try {
                                        double bonusValue = Double.parseDouble(perm.substring("sellgui.bonus.".length()));
                                        price += bonusValue;
                                    } catch (NumberFormatException | IndexOutOfBoundsException ignored) {}
                                }
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
                }
                return round(price, this.main.getConfig().getInt("places-to-round", 2));
            }
        } else {
            return round(price, this.main.getConfig().getInt("places-to-round", 2));
        }
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