package me.aov.sellgui.dynashop;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.plugin.java.JavaPlugin;

public final class SellGUIDynaShop extends JavaPlugin {
    private DynaShopPriceProvider priceProvider;

    @Override
    public void onEnable() {
        SellGUIMain sellGUI = SellGUIMain.getInstance();
        if (sellGUI == null || sellGUI.getSellGUIAPI() == null) {
            getLogger().severe("SellGUI API is not available. Disabling SellGUI-DynaShop.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        priceProvider = new DynaShopPriceProvider(this);
        sellGUI.getSellGUIAPI().registerPriceProvider(priceProvider);
        getLogger().info("Registered ShopGUIPlus-DynaShop dynamic price provider with SellGUI.");
    }

    @Override
    public void onDisable() {
        SellGUIMain sellGUI = SellGUIMain.getInstance();
        if (sellGUI != null && sellGUI.getSellGUIAPI() != null && priceProvider != null) {
            sellGUI.getSellGUIAPI().unregisterPriceProvider(priceProvider);
        }
    }
}
