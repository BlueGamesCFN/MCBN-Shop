package de.mcbn.shops.keeper.gui;

import de.mcbn.shops.Main;
import de.mcbn.shops.keeper.KeeperManager;
import de.mcbn.shops.keeper.ShopKeeper;
import de.mcbn.shops.shop.Shop;
import de.mcbn.shops.shop.ShopManager;
import de.mcbn.shops.util.BlockPosKey;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class KeeperMenuGUI implements Listener {

    private final Main plugin;
    private final KeeperManager keepers;
    private final ShopManager shops;

    public KeeperMenuGUI(Main plugin, KeeperManager keepers, ShopManager shops) {
        this.plugin = plugin;
        this.keepers = keepers;
        this.shops = shops;
    }

    private static final class Session {
        final UUID player;
        final UUID keeperId;
        Session(UUID player, UUID keeperId) {
            this.player = player;
            this.keeperId = keeperId;
        }
    }

    private static final class Holder implements InventoryHolder {
        final Session session;
        Holder(Session s) { this.session = s; }
        @Override public Inventory getInventory() { return null; }
    }

    /** Öffnet das Hauptmenü für den Besitzer */
    public void openMain(Player p, ShopKeeper k) {
        Session s = new Session(p.getUniqueId(), k.uuid());
        Inventory inv = Bukkit.createInventory(new Holder(s), 27, ChatColor.DARK_GREEN + "Shopkeeper-Menü");

        fill(inv, glass(Material.GRAY_STAINED_GLASS_PANE));

        inv.setItem(10, named(Material.CHEST, "§aVerknüpfte Shops anzeigen", "Siehe alle Shops dieses Keepers"));
        inv.setItem(12, named(Material.EMERALD_BLOCK, "§aNeuen Shop verknüpfen", "Schau auf eine Kiste und klicke."));
        inv.setItem(14, named(Material.BARRIER, "§cVerknüpfung entfernen", "Schau auf eine Kiste und klicke."));
        inv.setItem(16, named(Material.RED_WOOL, "§cShopkeeper löschen", "Entfernt diesen NPC dauerhaft."));
        inv.setItem(22, named(Material.OAK_DOOR, "§7Schließen", null));

        p.openInventory(inv);
    }

    /** Zeigt alle verknüpften Shops */
    private void openLinkedList(Player p, ShopKeeper k) {
        List<BlockPosKey> links = k.linked();
        int size = Math.min(54, Math.max(9, ((links.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.DARK_GREEN + "Verknüpfte Shops");

        fill(inv, glass(Material.BLACK_STAINED_GLASS_PANE));
        int slot = 0;
        for (BlockPosKey pos : links) {
            Shop s = shops.get(pos.toLocation().getBlock()).orElse(null);
            if (s == null) continue;

            ItemStack it = s.template().clone();
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "Shop an " + pos.world + " " + pos.x + "," + pos.y + "," + pos.z);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Bundle: " + s.bundleAmount() + "x");
            lore.add(ChatColor.GRAY + "Preis: " + s.price() + " " + s.currency().name());
            meta.setLore(lore);
            it.setItemMeta(meta);
            inv.setItem(slot++, it);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);

        Holder holder = (Holder) e.getInventory().getHolder();
        Session s = holder.session;
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player)) return;
        Player p = (Player) he;
        if (!p.getUniqueId().equals(s.player)) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        ShopKeeper k = keepers.get(s.keeperId);
        if (k == null) { p.closeInventory(); return; }

        switch (clicked.getType()) {
            case CHEST:
                openLinkedList(p, k);
                break;
            case EMERALD_BLOCK:
                p.closeInventory();
                p.sendMessage("§aSchau jetzt auf eine Shop-Kiste und tippe §e/shopkeeper link§a um sie zu verknüpfen.");
                break;
            case BARRIER:
                p.closeInventory();
                p.sendMessage("§eSchau jetzt auf eine Kiste und tippe §e/shopkeeper unlink§e um sie zu trennen.");
                break;
            case RED_WOOL:
                keepers.remove(k.uuid());
                p.sendMessage("§cShopkeeper gelöscht.");
                p.closeInventory();
                break;
            case OAK_DOOR:
                p.closeInventory();
                break;
            default:
                break;
        }
    }

    private static void fill(Inventory inv, ItemStack it) {
        for (int i = 0; i < inv.getSize(); i++)
            if (inv.getItem(i) == null) inv.setItem(i, it);
    }

    private static ItemStack glass(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(" ");
        it.setItemMeta(im);
        return it;
    }

    private static ItemStack named(Material mat, String name, String lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        if (lore != null) im.setLore(Collections.singletonList(ChatColor.GRAY + lore));
        it.setItemMeta(im);
        return it;
    }
}
