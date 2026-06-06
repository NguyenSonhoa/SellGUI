package me.aov.sellgui.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface SellGUIPriceProvider {
    String getName();

    default int getPriority() {
        return 0;
    }

    default boolean isAvailable() {
        return true;
    }

    double getSellPrice(Player player, ItemStack itemStack);

    default void onItemsSold(Player player, List<SoldItem> soldItems, double totalPrice) {
    }
}
