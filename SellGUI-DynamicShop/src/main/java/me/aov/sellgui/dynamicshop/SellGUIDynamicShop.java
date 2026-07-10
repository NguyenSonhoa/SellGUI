package me.aov.sellgui.dynamicshop;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.plugin.java.JavaPlugin;

public final class SellGUIDynamicShop extends JavaPlugin {
    private DynamicShopPriceProvider priceProvider;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        SellGUIMain sellGUI = SellGUIMain.getInstance();
        if (sellGUI == null || sellGUI.getSellGUIAPI() == null) {
            getLogger().severe("SellGUI API is not available. Disabling SellGUI-DynamicShop.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        priceProvider = new DynamicShopPriceProvider(this);
        sellGUI.getSellGUIAPI().registerPriceProvider(priceProvider);
        getLogger().info("Registered DynamicShop price provider with SellGUI.");
    }

    @Override
    public void onDisable() {
        SellGUIMain sellGUI = SellGUIMain.getInstance();
        if (sellGUI != null && sellGUI.getSellGUIAPI() != null && priceProvider != null) {
            sellGUI.getSellGUIAPI().unregisterPriceProvider(priceProvider);
        }
    }
}
