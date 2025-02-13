package me.aov.sellgui.commands;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

public class CustomItemsCommand implements CommandExecutor {
    private static SellGUIMain main;
    private static Inventory menu;
    private static ArrayList<ItemStack> customItems;
    private static ArrayList<Double> prices;
    private static int page;
    private static ItemStack filler;
    private static ItemStack delete;
    private static ItemStack next;
    private static ItemStack back;



    public CustomItemsCommand(SellGUIMain main) {
        this.main = main;
        page = 0;
        customItems = new ArrayList<>();
        prices = new ArrayList<>();
        importStuff();
        makeMenu();

    }


    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
       if(!(commandSender instanceof Player)){
           commandSender.sendMessage("Players only");
           return true;
       }else{
           Player p = (((Player) commandSender).getPlayer());
           if(strings.length == 0){
               p.openInventory(menu);
               page = 0;
               display();
           }else{
               try{
                   p.getWorld().dropItem(p.getLocation(), getPriceItem(Double.parseDouble(strings[0])));
               }catch(NullPointerException e){
                   p.sendMessage("Error");
               }catch (NumberFormatException e){
                   p.sendMessage("Try using a number.");
               }
           }
       }
        return true;
    }

    private void importStuff() {
        if (main.getCustomItemsConfig().getList("items") == null) {
            main.getCustomItemsConfig().set("items", this.customItems);
        }
        if (main.getCustomItemsConfig().getList("prices") == null) {
            main.getCustomItemsConfig().set("prices", this.prices);
        }
        customItems.clear();
        prices.clear();
        for (ItemStack itemStack : (ArrayList<ItemStack>) main.getCustomItemsConfig().getList("items")) {
            customItems.add(itemStack);
        }
        for (Double dubs : (ArrayList<Double>) main.getCustomItemsConfig().getList("prices")) {
            prices.add(dubs);
        }
    }

    private void makeMenu() {
        menu = Bukkit.createInventory(null, 45, color("&c&lCustom Items"));
        filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        delete = new ItemStack(Material.BARRIER);
        next = new ItemStack(Material.GREEN_WOOL);
        back = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);
        meta = delete.getItemMeta();
        meta.setDisplayName(color("&c&lDelete Item"));
        delete.setItemMeta(meta);
        meta = next.getItemMeta();
        meta.setDisplayName(color("&2&lNext"));
        next.setItemMeta(meta);
        meta = back.getItemMeta();
        meta.setDisplayName(color("&c&lBack"));
        back.setItemMeta(meta);

        for (int i = 0; i < 9; i++) {
            menu.setItem(i, filler);
        }
        for (int i = 18; i < 27; i++) {
            menu.setItem(i, filler);
        }
        for (int i = 36; i < 45; i++) {
            menu.setItem(i, filler);
        }
        menu.setItem(9, filler);
        menu.setItem(17, filler);
        menu.setItem(35, filler);
        menu.setItem(27, filler);
    }

    private static void display() {
        for (int i = 0; i < 9; i++) {
            menu.setItem(i, filler);
        }
        for (int i = 10; i < 17; i++) {
            menu.clear(i);
            menu.clear(i+18);
        }
        for (int i = 0 + (7 * page); i < 7 + (7 * page); i++) {
            try {
                int slot = getFreeSlot();
                menu.setItem(slot, customItems.get(i));
                menu.setItem(slot - 9, delete);
                menu.setItem(slot + 18, getPriceItem(getPrice(customItems.get(i))));
            } catch (IndexOutOfBoundsException e) {
                continue;
            }
        }
        if(page > 0){
            menu.setItem(36, back);
        }else{
            menu.setItem(36, filler);
        }
        menu.setItem(44, next);
    }

    private static boolean doesntHave(ItemStack itemStack) {
        for (ItemStack s : customItems) {
            if (s.isSimilar(itemStack)) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack getPriceItem(double dub) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName("price: " + dub);
        paper.setItemMeta(meta);
        return paper;
    }

    private static int getFreeSlot() {
        for (int i = 10; i < 18; i++) {
            if (menu.getItem(i) == null) {
                return i;
            }
        }
        return -1;
    }

    public static void addToList() {
        for (int i = 10; i < 17; i++) {
            if (menu.getItem(i) != null && doesntHave(menu.getItem(i))) {
                customItems.add(menu.getItem(i));
                if (menu.getItem(i + 18) != null && isPrice(menu.getItem(i + 18))) {
                    prices.add(getPricePrice(menu.getItem(i + 18)));
                } else {
                    prices.add(0D);
                }
            }
        }
    }


    public static void saveStuff() {
        main.getCustomItemsConfig().set("items", customItems);
        main.getCustomItemsConfig().set("prices", prices);
        main.saveCustom();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static boolean isPrice(ItemStack itemStack) {
        if (itemStack != null && itemStack.getItemMeta().getDisplayName().contains("price: ") && getPricePrice(itemStack) != -1) {
            return true;
        }
        return false;
    }

    public static double getPricePrice(ItemStack itemStack) {
        if (itemStack != null && itemStack.getType().equals(Material.PAPER)) {
            return Double.parseDouble(itemStack.getItemMeta().getDisplayName().replaceAll("price: ", ""));
        }
        return -1;
    }
    public static void nextPage(){
        page++;
        addToList();
        display();
    }
    public static void lastPage(){
        page--;
        addToList();
        display();
    }

    public static double getPrice(ItemStack stack) {
        int index = 0;
        for (ItemStack itemStack : customItems) {
            if (stack != null && itemStack.isSimilar(stack)) {
                return prices.get(index);
            } else {
                index++;
            }
        }
        return -1;
    }

    public static void removeItem(int slot) {
        int index = 0;
        ItemStack temp = null;
        for (ItemStack itemStack : customItems) {
            if (menu.getItem(slot) != null && menu.getItem(slot).isSimilar(itemStack)) {
                temp = itemStack;
            } else {

                index++;
            }
            if(temp != null){
                continue;
            }
        }
        menu.remove(temp);
        customItems.remove(temp);
        menu.setItem(slot+18, null);
        menu.setItem(slot-9, filler);
        prices.remove(index);
        saveStuff();
        display();
    }
    public static boolean clickable(ItemStack itemStack){
        return !(itemStack.isSimilar(delete) || itemStack.isSimilar(filler) || itemStack.isSimilar(next) || itemStack.isSimilar(back));
    }


    public static Inventory getMenu() {
        return menu;
    }

    public static ItemStack getFiller() {
        return filler;
    }

    public static ItemStack getDelete() {
        return delete;
    }

    public static ArrayList<ItemStack> getCustomItems() {
        return customItems;
    }

    public static ArrayList<Double> getPrices() {
        return prices;
    }

    public static int getPage() {
        return page;
    }

    public static ItemStack getNext() {
        return next;
    }

    public static ItemStack getBack() {
        return back;
    }
}
