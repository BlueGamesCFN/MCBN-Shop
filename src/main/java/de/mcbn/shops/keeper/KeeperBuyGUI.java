package de.mcbn.shops.keeper;

import de.mcbn.shops.Main;
import de.mcbn.shops.shop.Shop;
import de.mcbn.shops.shop.ShopCommands;
import de.mcbn.shops.shop.ShopManager;
import de.mcbn.shops.util.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class KeeperBuyGUI implements Listener {

    private final Main plugin;
    private final ShopManager shops;
    private final de.mcbn.shops.chat.ChatPromptService prompts;

    public KeeperBuyGUI(Main plugin, ShopManager shops, de.mcbn.shops.chat.ChatPromptService prompts) {
        this.plugin = plugin;
        this.shops = shops;
        this.prompts = prompts;
    }

    public static void open(Player p, Shop s) {
        Inventory inv = Bukkit.createInventory(new BuyHolder(s), 27, ChatColor.DARK_GREEN + "Kaufmenü");
        ItemStack display = s.template().clone();
        inv.setItem(10, display);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        int stockBundles = 0;
        java.util.Optional<org.bukkit.inventory.Inventory> invOpt = ShopManager.getContainerInventory(s.pos().toLocation().getBlock());
        if (invOpt.isPresent()) {
            int stockItems = InventoryUtils.countSimilar(invOpt.get(), s.template());
            stockBundles = stockItems / s.bundleAmount();
        }
        meta.setDisplayName(ChatColor.YELLOW + "Info");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Bundle: " + ChatColor.WHITE + s.bundleAmount(),
                ChatColor.GRAY + "Preis: " + ChatColor.AQUA + s.price() + "x " + s.currency().name(),
                ChatColor.GRAY + "Bestand: " + ChatColor.WHITE + stockBundles + " Bundles",
                ChatColor.DARK_GRAY + "Auswahl: 0 Bundles"
        ));
        info.setItemMeta(meta);
        inv.setItem(12, info);

        inv.setItem(19, button(Material.LIME_CONCRETE, "+1"));
        inv.setItem(20, button(Material.LIME_CONCRETE, "+5"));
        inv.setItem(21, button(Material.LIME_CONCRETE, "+10"));
        inv.setItem(22, button(Material.YELLOW_CONCRETE, "Max"));
        inv.setItem(25, button(Material.EMERALD_BLOCK, "Bestätigen"));
        inv.setItem(26, button(Material.BARRIER, "Abbrechen"));

        p.openInventory(inv);
    }

    private static ItemStack button(Material m, String name) {
        ItemStack is = new ItemStack(m);
        ItemMeta meta = is.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + name);
        is.setItemMeta(meta);
        return is;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BuyHolder)) return;
        event.setCancelled(true);
        Player p = (Player) event.getWhoClicked();
        BuyHolder holder = (BuyHolder) event.getInventory().getHolder();
        Shop s = holder.shop;
        ItemStack info = event.getInventory().getItem(12);
        if (info == null || !info.hasItemMeta()) return;
        ItemMeta im = info.getItemMeta();
        List<String> lore = im.getLore();
        if (lore == null || lore.size() < 4) return;

        Optional<org.bukkit.inventory.Inventory> invOpt = ShopManager.getContainerInventory(s.pos().toLocation().getBlock());
        int stockBundles = 0;
        if (invOpt.isPresent()) {
            int stockItems = InventoryUtils.countSimilar(invOpt.get(), s.template());
            stockBundles = stockItems / s.bundleAmount();
        }

        String clickedName = event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta() ? ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName()) : "";
        int selected = 0;
        try {
            String line = ChatColor.stripColor(lore.get(3));
            selected = Integer.parseInt(line.replace("Auswahl:", "").replace("Bundles", "").trim());
        } catch (Exception ignored) {}

        if (clickedName.equalsIgnoreCase("+1")) selected += 1;
        else if (clickedName.equalsIgnoreCase("+5")) selected += 5;
        else if (clickedName.equalsIgnoreCase("+10")) selected += 10;
        else if (clickedName.equalsIgnoreCase("Max")) selected = stockBundles;
        else if (clickedName.equalsIgnoreCase("Abbrechen")) { p.closeInventory(); return; }
        else if (clickedName.equalsIgnoreCase("Bestätigen")) {
            if (selected <= 0) { p.sendMessage(Main.get().messages().prefixed("prompt-buy-qty")); return; }
            Block block = s.pos().toLocation().getBlock();
            new ShopCommands(Main.get(), Main.get().shops(), Main.get().prompts()).performPurchase(p, block, s, selected);
            p.closeInventory();
            return;
        }

        if (selected > stockBundles) selected = stockBundles;
        if (selected < 0) selected = 0;

        lore.set(3, ChatColor.DARK_GRAY + "Auswahl: " + selected + " Bundles");
        im.setLore(lore);
        info.setItemMeta(im);
        event.getInventory().setItem(12, info);
    }

    public static class BuyHolder implements InventoryHolder {
        final Shop shop;
        public BuyHolder(Shop s) { this.shop = s; }
        @Override public Inventory getInventory() { return null; }
    }
}
