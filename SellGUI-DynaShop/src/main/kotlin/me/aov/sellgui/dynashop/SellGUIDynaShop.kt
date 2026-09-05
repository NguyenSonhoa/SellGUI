package me.aov.sellgui.dynashop

import me.aov.sellgui.SellGUIMain
import org.bukkit.plugin.java.JavaPlugin

class SellGUIDynaShop : JavaPlugin() {
    private var priceProvider: DynaShopPriceProvider? = null

    override fun onEnable() {
        val sellGUI = SellGUIMain.getInstance()
        if (sellGUI == null || sellGUI.getSellGUIAPI() == null) {
            logger.severe("SellGUI API is not available. Disabling SellGUI-DynaShop.")
            server.pluginManager.disablePlugin(this)
            return
        }
        priceProvider = DynaShopPriceProvider(this)
        priceProvider?.let { sellGUI.getSellGUIAPI().registerPriceProvider(it) }
        logger.info("Registered ShopGUIPlus-DynaShop dynamic price provider with SellGUI.")
    }

    override fun onDisable() {
        val sellGUI = SellGUIMain.getInstance()
        val provider = priceProvider
        if (sellGUI?.getSellGUIAPI() != null && provider != null) sellGUI.getSellGUIAPI().unregisterPriceProvider(provider)
    }
}
