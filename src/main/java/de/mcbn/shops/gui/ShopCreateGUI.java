package de.mcbn.shops.shop.gui;

import de.mcbn.shops.Main;
import de.mcbn.shops.shop.ShopManager;
import de.mcbn.shops.util.BlockPosKey;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShopCreateGUI implements Listener {

    private final Main plugin;
    private final ShopManager shops;

    public ShopCreateGUI(Main plugin, ShopManager shops) {
        this.plugin = plugin;
        this.shops = shops;
    }

    /** Session-Daten pro Spieler */
    private static final class Session {
        final UUID player;
        final BlockPosKey blockKey;
        final ItemStack template;
        int bundle = 16;
        int price = 1;
        Session(UUID player, Block block, ItemStack template) {
            this.player = player;
            this.blockKey = new BlockPosKey(block.getLocation());
            this.template = template;
        }
    }

    private static final class Holder implements InventoryHolder {
        final Session session;
        Holder(Session s) { this.session = s; }
        @Override public Inventory getInventory() { return null; }
    }

    /** Öffnet das Create-GUI, wenn im Ziel-Container ein Item als Template vorhanden ist. */
    public void open(Player p, Block target, ItemStack template) {
        Session s = new Session(p.getUniqueId(), target, template.clone());
        s.template.setAmount(1);

        Inventory inv = Bukkit.createInventory(new Holder(s), 27, ChatColor.DARK_GREEN + "Shop erstellen");

        // Deko
        fill(inv, glass(Material.GRAY_STAINED_GLASS_PANE, " "));

        // Template in die Mitte
        inv.setItem(4, named(template.clone(), ChatColor.GOLD + "Verkaufs-Item"));

        // Bundle-Menü
        inv.setItem(10, named(icon(Material.REDSTONE, "-1 Bundle"), ChatColor.RED + "Bundle -1"));
        inv.setItem(11, bundleItem(s.bundle));
        inv.setItem(12, named(icon(Material.EMERALD, "+1 Bundle"), ChatColor.GREEN + "Bundle +1"));

        // Preis-Menü (Währung aus Config)
        String currency = plugin.getConfig().getString("currency-material", "DIAMOND");
        inv.setItem(19, named(icon(Material.REDSTONE, "-1 Preis"), ChatColor.RED + "Preis -1"));
        inv.setItem(20, priceItem(s.price, currency));
        inv.setItem(21, named(icon(Material.EMERALD, "+1 Preis"), ChatColor.GREEN + "Preis +1"));

        // Aktionen
        inv.setItem(24, named(icon(Material.LIME_WOOL, "§a§lErstellen"), ChatColor.GREEN + "" + ChatColor.BOLD + "Erstellen"));
        inv.setItem(25, named(icon(Material.RED_WOOL, "§cAbbrechen"), ChatColor.RED + "Abbrechen"));

        p.openInventory(inv);
    }

    private static ItemStack icon(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }

    private static ItemStack named(ItemStack it, String name) {
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }

    private static ItemStack bundleItem(int bundle) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(ChatColor.YELLOW + "Bundle: " + ChatColor.WHITE + bundle);
        im.setLore(Collections.singletonList(ChatColor.GRAY + "Menge pro Kauf"));
        it.setItemMeta(im);
        return it;
    }

    private static ItemStack priceItem(int price, String currency) {
        ItemStack it = new ItemStack(Material.DIAMOND);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(ChatColor.AQUA + "Preis: " + ChatColor.WHITE + price + " " + currency);
        im.setLore(Collections.singletonList(ChatColor.GRAY + "Preis pro Bundle"));
        it.setItemMeta(im);
        return it;
    }

    private static void fill(Inventory inv, ItemStack it) {
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, it);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (e.getRawSlot() >= e.getInventory().getSize()) return;

        Holder holder = (Holder) e.getInventory().getHolder();
        Session s = holder.session;
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player)) return;
        Player p = (Player) he;
        if (!p.getUniqueId().equals(s.player)) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        String currency = plugin.getConfig().getString("currency-material", "DIAMOND");

        switch (clicked.getType()) {
            case REDSTONE: {
                String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                if (name.contains("Bundle")) s.bundle = Math.max(1, s.bundle - 1);
                if (name.contains("Preis")) s.price = Math.max(1, s.price - 1);
                e.getInventory().setItem(11, bundleItem(s.bundle));
                e.getInventory().setItem(20, priceItem(s.price, currency));
                break;
            }
            case EMERALD: {
                String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                if (name.contains("Bundle")) s.bundle = Math.min(3456, s.bundle + 1); // 54*64 Hardcap
                if (name.contains("Preis")) s.price = Math.min(64000, s.price + 1);
                e.getInventory().setItem(11, bundleItem(s.bundle));
                e.getInventory().setItem(20, priceItem(s.price, currency));
                break;
            }
            case LIME_WOOL: {
                // Erstellen
                Block target = s.blockKey.toLocation().getBlock();
                Optional<org.bukkit.inventory.Inventory> invOpt = ShopManager.getContainerInventory(target);
                if (!invOpt.isPresent()) { p.sendMessage("§cKein Behälter gefunden."); p.closeInventory(); return; }
                if (plugin.shops().isShop(target)) { p.sendMessage("§cHier existiert bereits ein Shop."); p.closeInventory(); return; }

                plugin.shops().createShop(p.getUniqueId(), target, s.template, s.bundle, s.price);
                p.sendMessage("§aShop erstellt: §f" + s.bundle + "x " + prettyItem(s.template) + " §7für §b" + s.price);
                p.closeInventory();
                break;
            }
            case RED_WOOL:
                p.closeInventory();
                break;
            default:
                break;
        }
    }

    private static String prettyItem(ItemStack is) {
        if (is == null) return "Unbekannt";
        if (is.getItemMeta() != null && is.getItemMeta().hasDisplayName()) return is.getItemMeta().getDisplayName();
        String n = is.getType().name().toLowerCase(Locale.ROOT).replace('_',' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    // 🔧 FEHLENDE METHODE HINZUGEFÜGT:
    private static ItemStack glass(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }
}
