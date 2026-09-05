package me.aov.sellgui.dynamicshop

import me.aov.sellgui.SellGUIMain
import org.bukkit.plugin.java.JavaPlugin

class SellGUIDynamicShop : JavaPlugin() {
    private var priceProvider: DynamicShopPriceProvider? = null

    override fun onEnable() {
        saveDefaultConfig()
        val sellGUI = SellGUIMain.getInstance()
        if (sellGUI == null || sellGUI.getSellGUIAPI() == null) {
            logger.severe("SellGUI API is not available. Disabling SellGUI-DynamicShop.")
            server.pluginManager.disablePlugin(this)
            return
        }
        priceProvider = DynamicShopPriceProvider(this)
        sellGUI.getSellGUIAPI().registerPriceProvider(priceProvider)
        logger.info("Registered DynamicShop price provider with SellGUI.")
    }

    override fun onDisable() {
        val sellGUI = SellGUIMain.getInstance()
        val provider = priceProvider
        if (sellGUI?.getSellGUIAPI() != null && provider != null) sellGUI.getSellGUIAPI().unregisterPriceProvider(provider)
    }
}
