package me.aov.sellgui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import javax.annotation.Nullable;
import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

public class SellGUIAPI {
    private SellGUIMain main;

    public SellGUIAPI(SellGUIMain main) {
        this.main = main;
    }

    public double getPrice(ItemStack itemStack, @Nullable Player player) {
        double price = 0.0;
        if (itemStack != null && itemStack.getType() != Material.AIR) {
            NBTItem nbtItem = NBTItem.get(itemStack);
            if (nbtItem.hasType()) {  //
                String itemId = nbtItem.getString("MMOITEMS_ITEM_ID");
                if (this.main.getMMOItemsPriceEditor().getItemPrices().containsKey(itemId)) {
                    return this.main.getMMOItemsPriceEditor().getItemPrices().get(itemId);
                }
            }
        }
        if (!this.main.getConfig().getBoolean("sell-all-command-sell-enchanted") && itemStack.getEnchantments().size() > 0) {
            return price;
        } else {
            ArrayList<String> flatBonus = new ArrayList();
            if (this.main.getItemPricesConfig().getStringList("flat-enchantment-bonus") != null) {
                Iterator var6 = this.main.getItemPricesConfig().getStringList("flat-enchantment-bonus").iterator();

                while(var6.hasNext()) {
                    String s = (String)var6.next();
                    flatBonus.add(s);
                }
            }

            ArrayList<String> multiplierBonus = new ArrayList();
            Iterator var13;
            if (this.main.getItemPricesConfig().getStringList("multiplier-enchantment-bonus") != null) {
                var13 = this.main.getItemPricesConfig().getStringList("multiplier-enchantment-bonus").iterator();

                while(var13.hasNext()) {
                    String s = (String)var13.next();
                    multiplierBonus.add(s);
                }
            }

            Iterator var9;
            if (this.main.hasEssentials() && this.main.getConfig().getBoolean("use-essentials-price") && this.main.getEssentialsHolder().getEssentials() != null) {
                if (!this.main.getConfig().getBoolean("use-permission-bonuses-on-essentials")) {
                    return round(this.main.getEssentialsHolder().getPrice(itemStack).doubleValue(), this.main.getConfig().getInt("places-to-round"));
                } else {
                    double temp = round(this.main.getEssentialsHolder().getPrice(itemStack).doubleValue(), this.main.getConfig().getInt("places-to-round"));
                    if (player != null) {
                        var9 = player.getEffectivePermissions().iterator();

                        while(true) {
                            while(var9.hasNext()) {
                                PermissionAttachmentInfo pai = (PermissionAttachmentInfo)var9.next();
                                if (pai.getPermission().contains("sellgui.bonus.") && pai.getValue()) {
                                    if (temp != 0.0) {
                                        temp += Double.parseDouble(pai.getPermission().replaceAll("sellgui.bonus.", ""));
                                    }
                                } else if (pai.getPermission().contains("sellgui.multiplier.") && pai.getValue()) {
                                    temp *= Double.parseDouble(pai.getPermission().replaceAll("sellgui.multiplier.", ""));
                                }
                            }

                            return this.main.getConfig().getBoolean("round-places") ? round(temp, this.main.getConfig().getInt("places-to-round")) : temp;
                        }
                    } else {
                        return this.main.getConfig().getBoolean("round-places") ? round(temp, this.main.getConfig().getInt("places-to-round")) : temp;
                    }
                }
            } else {
                if (itemStack != null && itemStack.getType() != Material.AIR && this.main.getItemPricesConfig().contains(itemStack.getType().name())) {
                    price = this.main.getItemPricesConfig().getDouble(itemStack.getType().name());
                }

                if (itemStack != null && itemStack.getItemMeta().hasEnchants()) {
                    var13 = itemStack.getItemMeta().getEnchants().keySet().iterator();

                    String s;
                    String[] temp2;
                    Enchantment enchantment;
                    String var10000;
                    int var10001;
                    while(var13.hasNext()) {
                        enchantment = (Enchantment)var13.next();
                        var9 = flatBonus.iterator();

                        while(var9.hasNext()) {
                            s = (String)var9.next();
                            temp2 = s.split(":");
                            if (temp2[0].equalsIgnoreCase(enchantment.getKey().getKey())) {
                                var10000 = temp2[1];
                                var10001 = itemStack.getEnchantmentLevel(enchantment);
                                if (var10000.equalsIgnoreCase("" + var10001)) {
                                    price += Double.parseDouble(temp2[2]);
                                }
                            }
                        }
                    }

                    var13 = itemStack.getItemMeta().getEnchants().keySet().iterator();

                    while(var13.hasNext()) {
                        enchantment = (Enchantment)var13.next();
                        var9 = multiplierBonus.iterator();

                        while(var9.hasNext()) {
                            s = (String)var9.next();
                            temp2 = s.split(":");
                            if (temp2[0].equalsIgnoreCase(enchantment.getKey().getKey())) {
                                var10000 = temp2[1];
                                var10001 = itemStack.getEnchantmentLevel(enchantment);
                                if (var10000.equalsIgnoreCase("" + var10001)) {
                                    price *= Double.parseDouble(temp2[2]);
                                }
                            }
                        }
                    }
                }

                if (player != null) {
                    var13 = player.getEffectivePermissions().iterator();

                    while(var13.hasNext()) {
                        PermissionAttachmentInfo pai = (PermissionAttachmentInfo)var13.next();
                        if (pai.getPermission().contains("sellgui.bonus.")) {
                            if (price != 0.0) {
                                price += Double.parseDouble(pai.getPermission().replaceAll("sellgui.bonus.", ""));
                            }
                        } else if (pai.getPermission().contains("sellgui.multiplier.")) {
                            price *= Double.parseDouble(pai.getPermission().replaceAll("sellgui.multiplier.", ""));
                        }
                    }
                }

                return round(price, 3);
            }
        }
    }

    public double getPurePrice(ItemStack itemStack) {
        double price = 0.0;
        if (itemStack != null && itemStack.getType() != Material.AIR && this.main.getItemPricesConfig().contains(itemStack.getType().name())) {
            price = this.main.getItemPricesConfig().getDouble(itemStack.getType().name());
        }

        return price;
    }

    public void openSellGUI(Player player) {
        this.main.getSellCommand().getSellGUIS().add(new SellGUI(this.main, player));
    }

    private static double round(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        } else {
            BigDecimal bd = new BigDecimal("" + value);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }
    }
}
