package me.aov.sellgui.dynamicshop;

import me.aov.sellgui.api.SellGUIPriceProvider;
import me.aov.sellgui.api.SoldItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;

final class DynamicShopPriceProvider implements SellGUIPriceProvider {
    private static final String DYNAMIC_SHOP_PLUGIN = "DynamicShop";
    private static final String SHOP_DATA_MANAGER = "org.minecraftsmp.dynamicshop.managers.ShopDataManager";
    private static final String TRANSACTION = "org.minecraftsmp.dynamicshop.transactions.Transaction";
    private static final String TRANSACTION_TYPE = "org.minecraftsmp.dynamicshop.transactions.Transaction$TransactionType";

    private final SellGUIDynamicShop plugin;

    DynamicShopPriceProvider(SellGUIDynamicShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return DYNAMIC_SHOP_PLUGIN;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean isAvailable() {
        return getDynamicShopPlugin() != null && getShopDataManagerClass() != null;
    }

    @Override
    public double getSellPrice(Player player, ItemStack itemStack) {
        if (!isSellable(itemStack)) {
            return 0.0;
        }

        int amount = Math.max(1, itemStack.getAmount());
        Material material = itemStack.getType();
        if (!canSell(material, amount)) {
            return 0.0;
        }

        try {
            Object total = getShopDataManagerClass()
                    .getMethod("getTotalSellValue", Material.class, int.class)
                    .invoke(null, material, amount);
            double totalPrice = ((Number) total).doubleValue();
            return totalPrice > 0 && !Double.isNaN(totalPrice) && !Double.isInfinite(totalPrice)
                    ? totalPrice / amount
                    : 0.0;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to read DynamicShop sell price: " + ex.getMessage());
            return 0.0;
        }
    }

    @Override
    public void onItemsSold(Player player, List<SoldItem> soldItems, double totalPrice) {
        Class<?> managerClass = getShopDataManagerClass();
        if (managerClass == null) {
            return;
        }

        for (SoldItem soldItem : soldItems) {
            ItemStack itemStack = soldItem.getItemStack();
            if (!isSellable(itemStack)) {
                continue;
            }

            Material material = itemStack.getType();
            int amount = Math.max(1, soldItem.getAmount());
            if (!canSell(material, amount)) {
                continue;
            }

            double payout = readTotalSellValue(material, amount);
            updateStock(managerClass, material, amount);
            logTransaction(player, material, amount, payout);
        }
    }

    private boolean isSellable(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR || isDamaged(itemStack)) {
            return false;
        }

        Material material = itemStack.getType();
        Class<?> managerClass = getShopDataManagerClass();
        if (managerClass == null) {
            return false;
        }

        try {
            double basePrice = ((Number) managerClass.getMethod("getBasePrice", Material.class).invoke(null, material)).doubleValue();
            if (basePrice < 0) {
                return false;
            }

            Object disabled = managerClass.getMethod("isSellDisabled", Material.class).invoke(null, material);
            return !Boolean.TRUE.equals(disabled) && matchesTemplate(managerClass, itemStack, material);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to check DynamicShop item: " + ex.getMessage());
            return false;
        }
    }

    private boolean canSell(Material material, int amount) {
        Class<?> managerClass = getShopDataManagerClass();
        if (managerClass == null) {
            return false;
        }

        try {
            Object result = managerClass.getMethod("canSell", Material.class, int.class)
                    .invoke(null, material, amount);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to check DynamicShop stock limit: " + ex.getMessage());
            return false;
        }
    }

    private boolean matchesTemplate(Class<?> managerClass, ItemStack itemStack, Material material) throws ReflectiveOperationException {
        Object templateObject = managerClass.getMethod("getTemplate", Material.class).invoke(null, material);
        ItemStack template = templateObject instanceof ItemStack ? ((ItemStack) templateObject).clone() : new ItemStack(material, 1);

        ItemStack oneItem = itemStack.clone();
        oneItem.setAmount(1);
        template.setAmount(1);
        return oneItem.isSimilar(template);
    }

    private double readTotalSellValue(Material material, int amount) {
        Class<?> managerClass = getShopDataManagerClass();
        if (managerClass == null) {
            return 0.0;
        }

        try {
            Object total = managerClass.getMethod("getTotalSellValue", Material.class, int.class)
                    .invoke(null, material, amount);
            return ((Number) total).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to read DynamicShop transaction price: " + ex.getMessage());
            return 0.0;
        }
    }

    private void updateStock(Class<?> managerClass, Material material, int amount) {
        try {
            managerClass.getMethod("updateStock", Material.class, double.class).invoke(null, material, (double) amount);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Failed to update DynamicShop stock for " + material + ": " + ex.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void logTransaction(Player player, Material material, int amount, double payout) {
        Plugin dynamicShop = getDynamicShopPlugin();
        if (dynamicShop == null) {
            return;
        }

        try {
            Object logger = dynamicShop.getClass().getMethod("getTransactionLogger").invoke(dynamicShop);
            if (logger == null) {
                return;
            }

            Class<?> transactionClass = Class.forName(TRANSACTION);
            Class<? extends Enum> transactionTypeClass = Class.forName(TRANSACTION_TYPE).asSubclass(Enum.class);
            Object sellType = Enum.valueOf(transactionTypeClass, "SELL");
            String category = readCategory(material);
            Object transaction = transactionClass
                    .getMethod("now", String.class, transactionTypeClass, String.class, int.class, double.class, String.class, String.class)
                    .invoke(null, player.getName(), sellType, material.name(), amount, payout, category, "");

            logger.getClass().getMethod("log", transactionClass).invoke(logger, transaction);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to log DynamicShop transaction: " + ex.getMessage());
        }
    }

    private String readCategory(Material material) {
        Class<?> managerClass = getShopDataManagerClass();
        if (managerClass == null) {
            return "UNKNOWN";
        }

        try {
            Object category = managerClass.getMethod("detectCategory", Material.class).invoke(null, material);
            return category == null ? "UNKNOWN" : category.toString();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return "UNKNOWN";
        }
    }

    private boolean isDamaged(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        return meta instanceof Damageable damageable && damageable.hasDamage() && damageable.getDamage() > 0;
    }

    private Class<?> getShopDataManagerClass() {
        try {
            return Class.forName(SHOP_DATA_MANAGER);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private Plugin getDynamicShopPlugin() {
        Plugin dynamicShop = Bukkit.getPluginManager().getPlugin(DYNAMIC_SHOP_PLUGIN);
        return dynamicShop != null && dynamicShop.isEnabled() ? dynamicShop : null;
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(message);
        }
    }
}
