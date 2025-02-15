package me.aov.sellgui.commands;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;


public class SellAllCommand implements CommandExecutor {
    private SellGUIMain main;

    public SellAllCommand(SellGUIMain main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            sellItems(player.getInventory(), player);
        } else {
            player.sendMessage(color(main.getLangConfig().getString("sellall-confirm-message")));
        }
        return true;
    }

    public double getPrice(ItemStack itemStack, Player player) {
        double price = 0.0D;
        if(!main.getConfig().getBoolean("sell-all-command-sell-enchanted") && itemStack.getEnchantments().size() > 0){
            return price;
        }
        this.main.getItemPricesConfig().getStringList("flat-enchantment-bonus");
        ArrayList<String> flatBonus = new ArrayList<>(this.main.getItemPricesConfig().getStringList("flat-enchantment-bonus"));
        this.main.getItemPricesConfig().getStringList("multiplier-enchantment-bonus");
        ArrayList<String> multiplierBonus = new ArrayList<>(this.main.getItemPricesConfig().getStringList("multiplier-enchantment-bonus"));
        if (this.main.hasEssentials() && main.getConfig().getBoolean("use-essentials-price")) {
            if (main.getEssentialsHolder().getEssentials() != null) {
                return round(main.getEssentialsHolder().getPrice(itemStack).doubleValue(), 3);
            }
        }
        if (itemStack != null && !(itemStack.getType() == Material.AIR) &&
                this.main.getItemPricesConfig().contains(itemStack.getType().name()))
            price = this.main.getItemPricesConfig().getDouble(itemStack.getType().name());
        if (itemStack != null && itemStack.getItemMeta().hasEnchants()) {
            for (Enchantment enchantment : itemStack.getItemMeta().getEnchants().keySet()) {
                for (String s : flatBonus) {
                    String[] temp = s.split(":");
                    if (temp[0].equalsIgnoreCase(enchantment.getKey().getKey()) && temp[1]
                            .equalsIgnoreCase(itemStack.getEnchantmentLevel(enchantment) + ""))
                        price += Double.parseDouble(temp[2]);
                }
            }
            for (Enchantment enchantment : itemStack.getItemMeta().getEnchants().keySet()) {
                for (String s : multiplierBonus) {
                    String[] temp2 = s.split(":");
                    if (temp2[0].equalsIgnoreCase(enchantment.getKey().getKey()) && temp2[1]
                            .equalsIgnoreCase(itemStack.getEnchantmentLevel(enchantment) + ""))
                        price *= Double.parseDouble(temp2[2]);
                }
            }
        }
        for(PermissionAttachmentInfo pai : player.getEffectivePermissions()){
            if(pai.getPermission().contains("sellgui.bonus.")){
                if(price != 0){
                    price += Double.parseDouble(pai.getPermission().replaceAll("sellgui.bonus.", ""));
                }
            }else if(pai.getPermission().contains("sellgui.multiplier.")){
                price *= Double.parseDouble(pai.getPermission().replaceAll("sellgui.multiplier.",""));
            }
        }
        return round(price, 3);
    }

    public double getTotal(Inventory inventory, Player player) {
        double total = 0.0D;
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null)
                total += getPrice(itemStack, player) * itemStack.getAmount();
        }
        return total;
    }

    public void sellItems(Inventory inventory, Player player) {
        this.main.getEcon().depositPlayer((OfflinePlayer) player, getTotal(inventory, player));
        player.sendMessage(color(this.main.getLangConfig().getString("sold-message").replaceAll("%total%", round(getTotal(inventory, player),3) + "")));
        for (ItemStack itemStack : inventory) {
            if (itemStack != null && getPrice(itemStack, player) != 0.0D) {
                inventory.remove(itemStack);
            }
        }
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
