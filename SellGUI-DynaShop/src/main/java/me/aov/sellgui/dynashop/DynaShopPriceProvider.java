package me.aov.sellgui.dynashop;

import me.aov.sellgui.api.SellGUIPriceProvider;
import me.aov.sellgui.api.SoldItem;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.ShopManager.ShopAction;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

final class DynaShopPriceProvider implements SellGUIPriceProvider {
    private static final String DYNASHOP_PLUGIN = "ShopGUIPlus-DynaShop";

    private final SellGUIDynaShop plugin;

    DynaShopPriceProvider(SellGUIDynaShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return DYNASHOP_PLUGIN;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean isAvailable() {
        return getDynaShopPlugin() != null && Bukkit.getPluginManager().isPluginEnabled("ShopGUIPlus");
    }

    @Override
    public double getSellPrice(Player player, ItemStack itemStack) {
        ShopItem shopItem = findShopItem(player, itemStack);
        if (shopItem == null || !isDynaShopItem(shopItem)) {
            return 0.0;
        }

        int amount = Math.max(1, itemStack.getAmount());
        if (!canSell(shopItem, amount)) {
            return 0.0;
        }

        Object dynamicPrice = loadDynamicPrice(player, shopItem, itemStack);
        if (dynamicPrice == null) {
            return 0.0;
        }

        try {
            double unitPrice;
            if (isProgressivePricing()) {
                double decay = ((Number) dynamicPrice.getClass().getMethod("getDecaySell").invoke(dynamicPrice)).doubleValue();
                unitPrice = ((Number) dynamicPrice.getClass()
                        .getMethod("calculateProgressiveAveragePrice", int.class, double.class, boolean.class)
                        .invoke(dynamicPrice, amount, decay, false)).doubleValue();
            } else {
                unitPrice = ((Number) dynamicPrice.getClass().getMethod("getSellPrice").invoke(dynamicPrice)).doubleValue();
            }
            return unitPrice > 0 && !Double.isNaN(unitPrice) && !Double.isInfinite(unitPrice) ? unitPrice : 0.0;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to read DynaShop sell price: " + ex.getMessage());
            return 0.0;
        }
    }

    @Override
    public void onItemsSold(Player player, List<SoldItem> soldItems, double totalPrice) {
        for (SoldItem soldItem : soldItems) {
            ItemStack itemStack = soldItem.getItemStack();
            if (itemStack == null) {
                continue;
            }

            ShopItem shopItem = findShopItem(player, itemStack);
            if (shopItem == null || !isDynaShopItem(shopItem)) {
                continue;
            }

            int amount = Math.max(1, soldItem.getAmount());
            if (getSellPrice(player, itemStack) <= 0) {
                continue;
            }

            processSell(player, shopItem, itemStack, amount);
        }
    }

    private ShopItem findShopItem(Player player, ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        try {
            ShopItem shopItem = player != null
                    ? ShopGuiPlusApi.getItemStackShopItem(player, itemStack)
                    : ShopGuiPlusApi.getItemStackShopItem(itemStack);
            if (shopItem != null) {
                return shopItem;
            }
        } catch (Throwable ignored) {
        }

        try {
            return ShopGuiPlusApi.getItemStackShopItem(itemStack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isDynaShopItem(ShopItem shopItem) {
        Object shopConfigManager = getShopConfigManager();
        if (shopConfigManager == null) {
            return false;
        }

        String shopId = shopItem.getShop().getId();
        String itemId = shopItem.getId();
        return hasTypeDynaShop(shopConfigManager, shopId, itemId)
                || invokeBoolean(shopConfigManager, "hasDynamicSection", shopId, itemId)
                || invokeBoolean(shopConfigManager, "hasStockSection", shopId, itemId)
                || invokeBoolean(shopConfigManager, "hasRecipeSection", shopId, itemId);
    }

    private boolean hasTypeDynaShop(Object shopConfigManager, String shopId, String itemId) {
        try {
            Method method = shopConfigManager.getClass().getMethod("getItemValue",
                    String.class, String.class, String.class, Class.class);
            Object result = method.invoke(shopConfigManager, shopId, itemId, "typeDynaShop", String.class);
            return result instanceof Optional<?> optional && optional.isPresent();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to check DynaShop item type: " + ex.getMessage());
            return false;
        }
    }

    private boolean canSell(ShopItem shopItem, int amount) {
        String sellType = getDynaShopType(shopItem, "sell");
        if (!"STOCK".equals(sellType) && !"STATIC_STOCK".equals(sellType)) {
            return true;
        }

        Object dynaShopPlugin = getDynaShopPlugin();
        if (dynaShopPlugin == null) {
            return false;
        }

        try {
            Object priceStock = dynaShopPlugin.getClass().getMethod("getPriceStock").invoke(dynaShopPlugin);
            Object result = priceStock.getClass()
                    .getMethod("canSell", String.class, String.class, int.class)
                    .invoke(priceStock, shopItem.getShop().getId(), shopItem.getId(), amount);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to check DynaShop stock sell limit: " + ex.getMessage());
            return true;
        }
    }

    private String getDynaShopType(ShopItem shopItem, String action) {
        Object shopConfigManager = getShopConfigManager();
        if (shopConfigManager == null) {
            return "UNKNOWN";
        }

        String shopId = shopItem.getShop().getId();
        String itemId = shopItem.getId();
        try {
            Object type = shopConfigManager.getClass()
                    .getMethod("getRealTypeDynaShop", String.class, String.class, String.class)
                    .invoke(shopConfigManager, shopId, itemId, action);
            if (type != null) {
                return type.toString();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        try {
            Object type = shopConfigManager.getClass()
                    .getMethod("getTypeDynaShop", String.class, String.class, String.class)
                    .invoke(shopConfigManager, shopId, itemId, action);
            if (type != null && !"NONE".equals(type.toString()) && !"UNKNOWN".equals(type.toString())) {
                return type.toString();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        try {
            Object type = shopConfigManager.getClass()
                    .getMethod("getTypeDynaShop", String.class, String.class)
                    .invoke(shopConfigManager, shopId, itemId);
            return type == null ? "UNKNOWN" : type.toString();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return "UNKNOWN";
        }
    }

    private Object loadDynamicPrice(Player player, ShopItem shopItem, ItemStack itemStack) {
        Object listener = getDynaShopListener();
        if (listener == null) {
            return null;
        }

        try {
            Method method = listener.getClass().getMethod("getOrLoadPrice", Player.class, String.class,
                    String.class, ItemStack.class, java.util.Set.class, java.util.Map.class);
            return method.invoke(listener, player, shopItem.getShop().getId(), shopItem.getId(),
                    itemStack, new HashSet<String>(), new HashMap<String, Object>());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to load DynaShop price: " + ex.getMessage());
            return null;
        }
    }

    private void processSell(Player player, ShopItem shopItem, ItemStack itemStack, int amount) {
        Object listener = getDynaShopListener();
        if (listener == null) {
            return;
        }

        String shopId = shopItem.getShop().getId();
        String itemId = shopItem.getId();
        ItemStack soldStack = itemStack.clone();
        soldStack.setAmount(amount);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> invokeProcessTransaction(listener, shopId, itemId, soldStack, amount));
        invokeUpdateStorage(listener, player, shopId, itemId, amount);
    }

    private void invokeProcessTransaction(Object listener, String shopId, String itemId, ItemStack itemStack, int amount) {
        try {
            Method method = listener.getClass().getDeclaredMethod("processTransactionAsync",
                    String.class, String.class, ItemStack.class, int.class, ShopAction.class);
            method.setAccessible(true);
            method.invoke(listener, shopId, itemId, itemStack, amount, ShopAction.SELL);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Failed to update DynaShop market transaction for "
                    + shopId + ":" + itemId + ": " + ex.getMessage());
        }
    }

    private void invokeUpdateStorage(Object listener, Player player, String shopId, String itemId, int amount) {
        try {
            Method method = listener.getClass().getDeclaredMethod("updateStorageData",
                    Player.class, String.class, String.class, boolean.class, int.class);
            method.setAccessible(true);
            method.invoke(listener, player, shopId, itemId, false, amount);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to flush DynaShop storage update: " + ex.getMessage());
        }
    }

    private boolean isProgressivePricing() {
        Object dynaShopPlugin = getDynaShopPlugin();
        if (dynaShopPlugin == null) {
            return false;
        }

        try {
            Object config = dynaShopPlugin.getClass().getMethod("getConfigMain").invoke(dynaShopPlugin);
            Object mode = config.getClass().getMethod("getString", String.class, String.class)
                    .invoke(config, "pricing.calculation-mode", "simple");
            return "progressive".equalsIgnoreCase(String.valueOf(mode));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private boolean invokeBoolean(Object target, String methodName, String shopId, String itemId) {
        try {
            Object result = target.getClass()
                    .getMethod(methodName, String.class, String.class)
                    .invoke(target, shopId, itemId);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private Object getDynaShopListener() {
        Object dynaShopPlugin = getDynaShopPlugin();
        if (dynaShopPlugin == null) {
            return null;
        }

        try {
            return dynaShopPlugin.getClass().getMethod("getDynaShopListener").invoke(dynaShopPlugin);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to access DynaShop listener: " + ex.getMessage());
            return null;
        }
    }

    private Object getShopConfigManager() {
        Object dynaShopPlugin = getDynaShopPlugin();
        if (dynaShopPlugin == null) {
            return null;
        }

        try {
            return dynaShopPlugin.getClass().getMethod("getShopConfigManager").invoke(dynaShopPlugin);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            debug("Failed to access DynaShop shop config manager: " + ex.getMessage());
            return null;
        }
    }

    private Plugin getDynaShopPlugin() {
        Plugin dynaShop = Bukkit.getPluginManager().getPlugin(DYNASHOP_PLUGIN);
        return dynaShop != null && dynaShop.isEnabled() ? dynaShop : null;
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(message);
        }
    }
}
