package de.mcbn.shops.keeper;

import de.mcbn.shops.Main;
import de.mcbn.shops.shop.Shop;
import de.mcbn.shops.shop.ShopCommands;
import de.mcbn.shops.shop.ShopManager;
import de.mcbn.shops.util.BlockPosKey;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Öffnet beim Rechtsklick auf einen Shopkeeper das Kauf- oder Verwaltungs-GUI.
 */
public class KeeperListener implements Listener {

    private final Main plugin;
    private final KeeperManager manager;
    private final ShopManager shops;

    public KeeperListener(Main plugin, KeeperManager manager, ShopManager shops, de.mcbn.shops.chat.ChatPromptService prompts) {
        this.plugin = plugin;
        this.manager = manager;
        this.shops = shops;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        if (!(e instanceof Villager)) return;
        ShopKeeper k = manager.get(e.getUniqueId());
        if (k == null) return;

        event.setCancelled(true);
        Player p = event.getPlayer();

        // Besitzer sieht Verwaltungsmenü
        if (k.owner().equals(p.getUniqueId())) {
            openManagerGUI(p, k);
        } else {
            openKeeperShopGUI(p, k);
        }
    }

    /* === KAUF-GUI (für normale Spieler) === */
    private void openKeeperShopGUI(Player p, ShopKeeper k) {
        List<Shop> list = new ArrayList<>();
        for (BlockPosKey pos : k.linked()) {
            Shop s = shops.get(pos.toLocation().getBlock()).orElse(null);
            if (s != null) list.add(s);
        }
        if (list.isEmpty()) {
            p.sendMessage(plugin.messages().prefixed("shop-not-found"));
            return;
        }

        int size = Math.min(54, Math.max(9, ((list.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(new KeeperHolder(), size, ChatColor.DARK_GREEN + "Shopkeeper – Kaufen");

        int slot = 0;
        for (Shop s : list) {
            ItemStack it = s.template().clone();
            ItemMeta meta = it.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Bundle: " + ChatColor.WHITE + s.bundleAmount());
            lore.add(ChatColor.GRAY + "Preis: " + ChatColor.AQUA + s.price() + "x " + s.currency().name());
            lore.add(ChatColor.DARK_GRAY + "Shop: " + s.pos().toString());
            meta.setLore(lore);
            it.setItemMeta(meta);
            inv.setItem(slot++, it);
            if (slot >= size) break;
        }
        p.openInventory(inv);
    }

    /* === MANAGER-GUI (für Besitzer) === */
    private void openManagerGUI(Player p, ShopKeeper k) {
        Inventory inv = Bukkit.createInventory(new ManagerHolder(k.uuid()), 27, ChatColor.GOLD + "Shopkeeper – Verwaltung");

        fill(inv, glass(Material.GRAY_STAINED_GLASS_PANE, " "));

        inv.setItem(10, icon(Material.CHEST, "§aVerknüpfte Shops anzeigen"));
        inv.setItem(12, icon(Material.EMERALD_BLOCK, "§aNeuen Shop verknüpfen"));
        inv.setItem(14, icon(Material.BARRIER, "§cVerknüpfung entfernen"));
        inv.setItem(16, icon(Material.RED_WOOL, "§cShopkeeper löschen"));
        inv.setItem(22, icon(Material.OAK_DOOR, "§7Schließen"));

        p.openInventory(inv);
    }

    /* === INVENTORY HOLDERS === */
    public static class KeeperHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    public static class ManagerHolder implements InventoryHolder {
        final java.util.UUID keeperId;
        ManagerHolder(java.util.UUID id) { this.keeperId = id; }
        @Override public Inventory getInventory() { return null; }
    }

    /* === CLICK HANDLER === */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof KeeperHolder) {
            handleBuyClick(event);
        } else if (holder instanceof ManagerHolder) {
            handleManagerClick(event);
        }
    }

    /* --- Kaufmenü --- */
    private void handleBuyClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getRawSlot() >= event.getInventory().getSize()) return;
        Player p = (Player) event.getWhoClicked();

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        if (clicked.getItemMeta() == null || clicked.getItemMeta().getLore() == null) return;

        // Shop aus Lore auslesen
        String idLine = null;
        for (String s : clicked.getItemMeta().getLore()) {
            String raw = ChatColor.stripColor(s);
            if (raw != null && raw.startsWith("Shop: ")) {
                idLine = raw.substring("Shop: ".length()).trim();
                break;
            }
        }
        if (idLine == null) return;

        BlockPosKey key = BlockPosKey.fromString(idLine);
        Optional<Shop> shopOpt = shops.get(key.toLocation().getBlock());
        if (!shopOpt.isPresent()) {
            p.sendMessage(plugin.messages().prefixed("shop-not-found"));
            return;
        }

        Shop s = shopOpt.get();
        new ShopCommands(plugin, shops, plugin.prompts()).performPurchase(p, key.toLocation().getBlock(), s, 1);
    }

    /* --- Verwaltungsmenü --- */
    private void handleManagerClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Player p = (Player) event.getWhoClicked();
        ManagerHolder holder = (ManagerHolder) event.getInventory().getHolder();
        ShopKeeper k = manager.get(holder.keeperId);
        if (k == null) {
            p.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        switch (clicked.getType()) {
            case CHEST:
                // Liste zeigen
                p.closeInventory();
                openKeeperShopGUI(p, k);
                break;
            case EMERALD_BLOCK:
                p.closeInventory();
                p.sendMessage("§aSchau auf eine Kiste und nutze §e/shopkeeper link");
                break;
            case BARRIER:
                p.closeInventory();
                p.sendMessage("§aSchau auf eine Kiste und nutze §e/shopkeeper unlink");
                break;
            case RED_WOOL:
                manager.remove(k.uuid());
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

    /* === HILFSMETHODEN === */
    private static void fill(Inventory inv, ItemStack it) {
        for (int i = 0; i < inv.getSize(); i++)
            if (inv.getItem(i) == null) inv.setItem(i, it);
    }

    private static ItemStack glass(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }

    private static ItemStack icon(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }
}
