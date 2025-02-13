//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package me.aov.sellgui;

import com.earth2me.essentials.Essentials;
import java.math.BigDecimal;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public class EssentialsHolder {
    private Essentials essentials = (Essentials)Bukkit.getPluginManager().getPlugin("Essentials");

    public EssentialsHolder() {
        if (this.essentials == null) {
            System.out.println("WEEWOOO");
        }

    }

    public Essentials getEssentials() {
        return this.essentials;
    }

    public void setEssentials(Essentials essentials) {
        this.essentials = essentials;
    }

    public BigDecimal getPrice(ItemStack itemStack) {
        return this.essentials.getWorth().getPrice(this.essentials, itemStack) != null ? this.essentials.getWorth().getPrice(this.essentials, itemStack) : new BigDecimal(0);
    }
}
