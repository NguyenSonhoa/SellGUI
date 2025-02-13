package me.aov.sellgui.listeners;

import me.aov.sellgui.SellGUI;
import me.aov.sellgui.SellGUIMain;
import me.aov.sellgui.commands.SellCommand;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SignListener implements Listener {
    SellGUIMain main;

    public SignListener(SellGUIMain main) {
        this.main = main;
    }

    @EventHandler
    public void createSign(SignChangeEvent event) {
        if (event.getLine(0) != null && event.getLine(0).toLowerCase().equals("[sellgui]") && (event.getPlayer().isOp() || event.getPlayer().hasPermission("sellgui.createsign"))) {
            for (int i = 0; i < 4; i++) {
                if (main.getLangConfig().getStringList("sign-lines").get(i) != null) {
                    event.setLine(i, ChatColor.translateAlternateColorCodes('&', main.getLangConfig().getStringList("sign-lines").get(i)));
                }
            }
        }
    }

    @EventHandler
    public void rightClickSign(PlayerInteractEvent event) {
        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && isSign(event.getClickedBlock().getType())) {
            Sign sign = (Sign) event.getClickedBlock().getState();
            if(isSellGUISign(sign) && event.getPlayer().hasPermission("sellgui.usesign")){
                SellCommand.getSellGUIs().add(new SellGUI(main, event.getPlayer()));
            }
        }
    }

    private boolean isSign(Material material) {
        if (material.equals(Material.SPRUCE_SIGN) || material.equals(Material.SPRUCE_WALL_SIGN) ||
                material.equals(Material.ACACIA_SIGN) || material.equals(Material.ACACIA_WALL_SIGN) ||
                material.equals(Material.BIRCH_SIGN) || material.equals(Material.BIRCH_WALL_SIGN) ||
                material.equals(Material.CRIMSON_SIGN) || material.equals(Material.CRIMSON_WALL_SIGN) ||
                material.equals(Material.DARK_OAK_SIGN) || material.equals(Material.DARK_OAK_WALL_SIGN) ||
                material.equals(Material.JUNGLE_SIGN) || material.equals(Material.JUNGLE_WALL_SIGN) ||
                material.equals(Material.OAK_SIGN) || material.equals(Material.OAK_WALL_SIGN) ||
                material.equals(Material.WARPED_SIGN) || material.equals(Material.WARPED_WALL_SIGN)) {
            return true;
        }
        return false;
    }

    private boolean isSellGUISign(Sign sign){
        if(sign.getLines() == null){
            return false;
        }
        for(int i = 0; i < 4; i++){
            try {
                if (!sign.getLine(i).equalsIgnoreCase(ChatColor.translateAlternateColorCodes('&', main.getLangConfig().getStringList("sign-lines").get(i)))) {
                    return false;
                }
            }catch (IndexOutOfBoundsException e){
                return false;
            }
        }
        return true;
    }


}
