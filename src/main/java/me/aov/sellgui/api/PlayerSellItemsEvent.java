package me.aov.sellgui.api;

import me.aov.sellgui.SellGUI;
import me.aov.sellgui.api.SellGUIEvents;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * This event is called after a player has sold items through the SellGUI.
 */
public class PlayerSellItemsEvent extends SellGUIEvents {

    private final List<ItemStack> soldItems;
    private final double totalPrice;

    public PlayerSellItemsEvent(Player player, SellGUI sellGUI, List<ItemStack> soldItems, double totalPrice) {
        super(player, sellGUI);
        this.soldItems = soldItems;
        this.totalPrice = totalPrice;
    }

    /**
     * Gets the list of ItemStacks that were sold.
     *
     * @return A list of the sold items.
     */
    public List<ItemStack> getSoldItems() {
        return soldItems;
    }

    /**
     * Gets the total price the player received for all sold items.
     *
     * @return The total price.
     */
    public double getTotalPrice() {
        return totalPrice;
    }
}
