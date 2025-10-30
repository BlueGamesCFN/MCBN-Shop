package de.mcbn.shops.shop.gui;

import de.mcbn.shops.Main;
import de.mcbn.shops.shop.Shop;
import de.mcbn.shops.shop.ShopCommands;
import de.mcbn.shops.shop.ShopManager;
import de.mcbn.shops.util.BlockPosKey;
import de.mcbn.shops.util.InventoryUtils;
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

public class ShopBuyGUI implements Listener {

    private final Main plugin;
    private final ShopManager shops;

    public ShopBuyGUI(Main plugin, ShopManager shops) {
        this.plugin = plugin;
        this.shops = shops;
    }

    private static final class Session {
        final UUID player;
        final BlockPosKey key;
        final Shop shop;
        Session(UUID player, Block block, Shop shop) {
            this.player = player;
            this.key = new BlockPosKey(block.getLocation());
            this.shop = shop;
        }
    }

    private static final class Holder implements InventoryHolder {
        final Session s;
        Holder(Session s) { this.s = s; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player p, Block block, Shop s) {
        Session sess = new Session(p.getUniqueId(), block, s);
        Inventory inv = Bukkit.createInventory(new Holder(sess), 27, ChatColor.DARK_AQUA + "Shop: " + prettyItem(s.template()));

        // Deko
        fill(inv, glass(Material.BLACK_STAINED_GLASS_PANE, " "));

        // Item-Vorschau
        inv.setItem(4, named(s.template().clone(), ChatColor.GOLD + prettyItem(s.template())));

        // Info-Items
        inv.setItem(10, info("Bundle", s.bundleAmount() + "x"));
        inv.setItem(11, info("Preis", s.price() + " " + s.currency().name()));
        inv.setItem(12, info("Vorrat", getStockText(block, s)));

        // Kauf-Buttons
        inv.setItem(20, named(new ItemStack(Material.LIME_DYE), ChatColor.GREEN + "Kaufen x1"));
        inv.setItem(22, named(new ItemStack(Material.SLIME_BALL), ChatColor.GREEN + "Kaufen x5"));
        inv.setItem(24, named(new ItemStack(Material.EMERALD_BLOCK), ChatColor.GREEN + "Max kaufen"));

        p.openInventory(inv);
    }

    private String getStockText(Block block, Shop s) {
        Optional<Inventory> invOpt = ShopManager.getContainerInventory(block);
        if (!invOpt.isPresent()) return ChatColor.RED + "0 Bundles";
        int items = InventoryUtils.countSimilar(invOpt.get(), s.template());
        return ChatColor.WHITE + "" + (items / s.bundleAmount()) + ChatColor.GRAY + " Bundles";
    }

    private static ItemStack info(String name, String value) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(ChatColor.YELLOW + name);
        im.setLore(Collections.singletonList(ChatColor.GRAY + value));
        it.setItemMeta(im);
        return it;
    }

    private static ItemStack named(ItemStack it, String name) {
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }

    private static ItemStack glass(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }

    private static void fill(Inventory inv, ItemStack it) {
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, it);
    }

    private static String prettyItem(ItemStack is) {
        if (is == null || is.getType() == Material.AIR) return "Unknown";

        ItemMeta meta = is.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        String n = is.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (n.isEmpty()) return "Unknown";

        return Character.toUpperCase(n.charAt(0)) + (n.length() > 1 ? n.substring(1) : "");
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (e.getRawSlot() >= e.getInventory().getSize()) return;

        Holder holder = (Holder) e.getInventory().getHolder();
        Session sess = holder.s;
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player)) return;
        Player p = (Player) he;
        if (!p.getUniqueId().equals(sess.player)) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        Block block = sess.key.toLocation().getBlock();
        Optional<Shop> sOpt = shops.get(block);
        if (!sOpt.isPresent()) { p.sendMessage("§cShop nicht gefunden."); p.closeInventory(); return; }
        Shop s = sOpt.get();

        ShopCommands cmds = new ShopCommands(plugin, shops, plugin.prompts());

        switch (clicked.getType()) {
            case LIME_DYE: // x1
                cmds.performPurchase(p, block, s, 1);
                break;
            case SLIME_BALL: // x5
                cmds.performPurchase(p, block, s, 5);
                break;
            case EMERALD_BLOCK: { // Max (so viel wie Geld & Vorrat erlauben)
                int pricePerBundle = s.price();
                int playerCurrency = 0;
                for (ItemStack is : p.getInventory().getContents())
                    if (is != null && is.getType() == s.currency()) playerCurrency += is.getAmount();

                Optional<Inventory> invOpt = ShopManager.getContainerInventory(block);
                int stockBundles = 0;
                if (invOpt.isPresent()) {
                    int items = InventoryUtils.countSimilar(invOpt.get(), s.template());
                    stockBundles = items / s.bundleAmount();
                }
                int afford = playerCurrency / Math.max(1, pricePerBundle);
                int bundles = Math.max(0, Math.min(stockBundles, afford));
                if (bundles <= 0) {
                    p.sendMessage("§cNicht genug Vorrat oder Währung.");
                } else {
                    cmds.performPurchase(p, block, s, bundles);
                }
                break;
            }
            default:
                break;
        }

        // GUI aktualisieren (Vorrat neu darstellen)
        Inventory inv = e.getInventory();
        inv.setItem(12, info("Vorrat", getStockText(block, s)));
    }
}
