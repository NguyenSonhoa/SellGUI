package me.aov.sellgui.commands;

import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTCompound;
import io.lumine.mythic.lib.api.item.NBTItem;
import me.aov.sellgui.SellGUIMain;
import org.apache.commons.lang.WordUtils;
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
import me.aov.sellgui.utils.ItemIdentifier;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class SellAllCommand implements CommandExecutor {
    private SellGUIMain main;

    public SellAllCommand(SellGUIMain main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        String NoPerm = main.getMessagesConfig().getString("general.no_permission", "&c❌ You don't have permission to use this command!");
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cThis command can only be used by players!"));
            return true;
        }
        if (!player.hasPermission("sellgui.sellall")) {
            player.sendMessage(color(NoPerm));
            return true;
        }
        if (main.getEcon() == null) {
            player.sendMessage(color("&cEconomy system not available!"));
            return true;
        }
        double total = getTotal(player.getInventory(), player);
        int evaluationRequiredCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                if (main.getRandomPriceManager() != null &&
                        main.getRandomPriceManager().requiresEvaluation(item) &&
                        !main.getRandomPriceManager().isEvaluated(item)) {
                    evaluationRequiredCount += item.getAmount();
                }
            }
        }
        if (evaluationRequiredCount > 0) {
            String evaluationMessage = main.getMessagesConfig() != null ?
                    main.getMessagesConfig().getString("sellall.evaluation_required",
                            "&e⚠️ %count% items require evaluation before selling. Use /sellgui evaluate") :
                    "&e⚠️ %count% items require evaluation before selling. Use /sellgui evaluate";
            evaluationMessage = evaluationMessage.replace("%count%", String.valueOf(evaluationRequiredCount));
            player.sendMessage(color(evaluationMessage));
        }

        if (main.getConfig().getBoolean("general.debug", false)) {
            main.getLogger().info("SellAll debug - Player: " + player.getName() + ", Total: $" + total);
            main.getLogger().info("  Items needing evaluation: " + evaluationRequiredCount);
            int itemCount = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    double price = getPrice(item, player);
                    if (price > 0) {
                        itemCount++;
                        main.getLogger().info("  - " + item.getType() + " x" + item.getAmount() + " = $" + price);
                    }
                }
            }
            main.getLogger().info("  Total sellable items: " + itemCount);
        }

        if (total <= 0) {
            String NoItemMessage = main.getMessagesConfig().getString("sellall.no-items", "&c❌ No items to sell in your inventory!");
            player.sendMessage(color(NoItemMessage));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {

            sellItems(player.getInventory(), player);
        } else {

            player.sendMessage(color("&6&l=== SellAll Confirmation ==="));
            player.sendMessage("");
            String previewMore = main.getMessagesConfig().getString("sellall.preview-more", "&8... and more");
            String separator = main.getMessagesConfig().getString("sellall.separator", "&6&l=========================");
            String confirmProceed = main.getMessagesConfig().getString("sellall.confirm-proceed", "&a&l✓ &f/sellall confirm &7- Proceed with sale");
            String confirmCancel = main.getMessagesConfig().getString("sellall.confirm-cancel", "&c&l✗ &7Any other action - Cancel");
            String confirmMessage = "&e⚠️ You will receive &a$%total% &efor selling all items.";
            String itemToBeSold = main.getMessagesConfig().getString("sellall.item-to-be-sold", "&7Items to be sold:");
            if (main.getMessagesConfig() != null) {
                confirmMessage = main.getMessagesConfig().getString("sellall.confirm-message", confirmMessage);
            }
            confirmMessage = confirmMessage.replace("%total%", String.format("%.2f", total));
            player.sendMessage(color(confirmMessage));
            player.sendMessage("");

            if (main.getConfig().getBoolean("sellall-show-preview", true)) {
                player.sendMessage(color(itemToBeSold));
                player.sendMessage("");
                int previewCount = 0;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR && getPrice(item, player) > 0) {
                        if (previewCount < 5) {
                            String format = main.getMessagesConfig().getString("sellall.sell-all-format", "&8- &f%item_name% &7x%item_amount% &8= &e$%price%");
                            String itemName = ItemIdentifier.getItemDisplayName(item);
                            String calculationMethod = main.getConfig().getString("prices.calculation-method", "auto");
                            double itemPrice = getPrice(item, player);
                            double displayPrice = itemPrice;
                            if (!calculationMethod.equalsIgnoreCase("shopguiplus")) {
                                displayPrice *= item.getAmount();
                            }
                            String message = format.replace("%item_name%", itemName)
                                    .replace("%item_amount%", String.valueOf(item.getAmount()))
                                    .replace("%price%", String.format("%.2f", displayPrice));
                            player.sendMessage(color(message));
                            player.sendMessage("");
                            previewCount++;
                        }
                    }
                }
                if (previewCount >= 5) {
                    player.sendMessage(color(previewMore));
                    player.sendMessage("");
                }
                player.sendMessage(color(separator));
                player.sendMessage(color(confirmProceed));
                player.sendMessage(color(confirmCancel));
                player.sendMessage(color(separator));
            }
        }
        return true;
    }

    public double getPrice(ItemStack itemStack, Player player) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return 0.0;
        }

        if (main.getRandomPriceManager() != null && !main.getRandomPriceManager().canBeSold(itemStack)) {

            return 0.0;
        }

        double price = 0.0D;
        if(!main.getConfig().getBoolean("sell-all-command-sell-enchanted") && itemStack.getEnchantments().size() > 0){
            return price;
        }
        this.main.getItemPricesConfig().getStringList("flat-enchantment-bonus");
        ArrayList<String> flatBonus = new ArrayList<>(this.main.getItemPricesConfig().getStringList("flat-enchantment-bonus"));
        this.main.getItemPricesConfig().getStringList("multiplier-enchantment-bonus");
        ArrayList<String> multiplierBonus = new ArrayList<>(this.main.getItemPricesConfig().getStringList("multiplier-enchantment-bonus"));

        if (main.getPriceManager() != null) {
            try {
                price = main.getPriceManager().getItemPriceWithPlayer(itemStack, player);

                if (price > 0) {
                    return round(price, 3);
                }
            } catch (Exception e) {

            }
        }

        if (main.getNBTPriceManager() != null) {
            try {
                price = main.getNBTPriceManager().getPriceFromNBT(itemStack);
                if (price > 0) {
                    return round(price, 3);
                }
            } catch (Exception e) {

            }
        }

        if (main.getRandomPriceManager() != null) {
            try {
                price = main.getRandomPriceManager().getRandomPrice(itemStack);
                if (price > 0) {
                    return round(price, 3);
                }
            } catch (Exception e) {

            }
        }

        if (this.main.hasEssentials() && main.getConfig().getBoolean("use-essentials-price")) {
            if (main.getEssentialsHolder().getEssentials() != null) {
                return round(main.getEssentialsHolder().getPrice(itemStack).doubleValue(), 3);
            }
        }

        if (this.main.getItemPricesConfig().contains(itemStack.getType().name())) {
            price = this.main.getItemPricesConfig().getDouble(itemStack.getType().name());
        }
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
        String calculationMethod = main.getConfig().getString("prices.calculation-method", "auto");
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && itemStack.getType() != Material.AIR) {
                double itemPrice = getPrice(itemStack, player);
                if (itemPrice > 0) {
                    if (calculationMethod.equalsIgnoreCase("shopguiplus")) {
                        total += itemPrice;
                    } else {
                        total += itemPrice * itemStack.getAmount();
                    }
                }
            }
        }
        return round(total, 3);
    }

    public void sellItems(Inventory inventory, Player player) {
        double total = getTotal(inventory, player);
        int itemsSold = 0;

        if (total <= 0) {
            player.sendMessage(color("&cNo items to sell!"));
            return;
        }

        this.main.getEcon().depositPlayer((OfflinePlayer) player, total);

        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && getPrice(itemStack, player) > 0.0D) {

                if (this.main.getConfig().getBoolean("log-transactions")) {
                    logSellAll(itemStack, player);
                }
                itemsSold += itemStack.getAmount();
                inventory.remove(itemStack);
            }
        }

        String soldMessage = "&a✅ Sold %count% items for &e$%total%!";
        if (this.main.getMessagesConfig() != null) {
            soldMessage = this.main.getMessagesConfig().getString("sellall.sold-message", soldMessage);
        }
        soldMessage = soldMessage.replace("%total%", String.format("%.2f", total));
        soldMessage = soldMessage.replace("%count%", String.valueOf(itemsSold));
        player.sendMessage(color(soldMessage));

        if (main.getServer().getPluginManager().getPlugin("SellGUI") != null) {
            try {
                me.aov.sellgui.handlers.SoundHandler.playSuccess(player);
            } catch (Exception e) {

            }
        }
    }

    public void logSellAll(ItemStack itemStack, Player player) {
        if (itemStack != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.main.getLog(), true))) {
                Date now = new Date();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                ItemIdentifier.ItemType itemTypeEnum = ItemIdentifier.getItemType(itemStack);
                String itemType = itemTypeEnum.name();
                String itemId = ItemIdentifier.getItemIdentifier(itemStack);
                String displayName = ChatColor.stripColor(ItemIdentifier.getItemDisplayName(itemStack));
                double unitPrice = this.getPrice(itemStack, player);
                String calculationMethod = main.getConfig().getString("prices.calculation-method", "auto");
                double totalPrice;
                if (calculationMethod.equalsIgnoreCase("shopguiplus")) {
                    totalPrice = unitPrice;
                } else {
                    totalPrice = unitPrice * itemStack.getAmount();
                }
                String playerName = player.getName();

                String logEntry = String.format("[SELLALL] %s|%s|%s|%d|%.2f|%.2f|%s|%s",
                        itemType,
                        itemId,
                        displayName,
                        itemStack.getAmount(),
                        unitPrice,
                        totalPrice,
                        playerName,
                        format.format(now)
                );

                writer.append(logEntry + "\n");
                writer.flush();
            } catch (IOException e) {
                this.main.getLogger().severe("Failed to write to sell log: " + e.getMessage());
                e.printStackTrace();
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