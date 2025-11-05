package de.mcbn.shops.keeper;

import de.mcbn.shops.Main;
import de.mcbn.shops.shop.Shop;
import de.mcbn.shops.shop.ShopManager;
import de.mcbn.shops.util.BlockPosKey;
import de.mcbn.shops.util.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class KeeperBrowseGUI implements Listener {

    private final Main plugin;
    private final KeeperManager keeperManager;
    private final ShopManager shops;

    public KeeperBrowseGUI(Main plugin, KeeperManager km, ShopManager shops, de.mcbn.shops.chat.ChatPromptService prompts) {
        this.plugin = plugin;
        this.keeperManager = km;
        this.shops = shops;
    }

    public static void open(Player p, List<Shop> list) {
        int size = Math.min(54, Math.max(9, ((list.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(new BrowseHolder(), size, "§8§l⚑ §a§lShop-Übersicht §8§l⚑");
        int slot = 0;

        for (Shop s : list) {
            ItemStack it = s.template().clone();
            ItemMeta meta = it.getItemMeta();

            // Bestand berechnen
            Optional<org.bukkit.inventory.Inventory> invOpt = ShopManager.getContainerInventory(s.pos().toLocation().getBlock());
            int stockBundles = 0;
            if (invOpt.isPresent()) {
                int stockItems = InventoryUtils.countSimilar(invOpt.get(), s.template());
                stockBundles = stockItems / s.bundleAmount();
            }

            // Item-Name mit Farbe
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() :
                             ChatColor.AQUA + formatItemName(s.template().getType().name());
            meta.setDisplayName(itemName);

            // Lore mit visueller Trennung
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "▸ Bundle-Größe: " + ChatColor.WHITE + s.bundleAmount() + " Stück");
            lore.add(ChatColor.GRAY + "▸ Preis: " + ChatColor.GOLD + s.price() + "x " + ChatColor.YELLOW + formatItemName(s.currency().name()));
            lore.add("");

            // Bestand mit Farb-Indikator
            String stockColor = getStockColor(stockBundles);
            String stockIcon = getStockIcon(stockBundles);
            lore.add(ChatColor.GRAY + "▸ Vorrat: " + stockColor + stockIcon + " " + stockBundles + " Bundle" + (stockBundles != 1 ? "s" : ""));
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "» Klicke zum Kaufen «");
            lore.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + s.pos().toString());

            meta.setLore(lore);

            // Glühen für gut gefüllte Shops
            if (stockBundles >= 10) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }

            it.setItemMeta(meta);
            inv.setItem(slot++, it);
            if (slot >= size) break;
        }

        p.openInventory(inv);
    }

    private static String formatItemName(String name) {
        String[] parts = name.toLowerCase().replace('_', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                result.append(Character.toUpperCase(part.charAt(0)))
                      .append(part.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    private static String getStockColor(int bundles) {
        if (bundles == 0) return ChatColor.RED + "" + ChatColor.BOLD;
        if (bundles < 3) return ChatColor.RED + "";
        if (bundles < 10) return ChatColor.YELLOW + "";
        if (bundles < 20) return ChatColor.GREEN + "";
        return ChatColor.DARK_GREEN + "" + ChatColor.BOLD;
    }

    private static String getStockIcon(int bundles) {
        if (bundles == 0) return "✗";
        if (bundles < 3) return "▁";
        if (bundles < 10) return "▄";
        if (bundles < 20) return "▆";
        return "█";
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BrowseHolder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() >= event.getInventory().getSize()) return;
        Player p = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getLore() == null) return;

        String posStr = null;
        for (String s : clicked.getItemMeta().getLore()) {
            String raw = ChatColor.stripColor(s);
            if (raw != null && raw.startsWith("Shop: ")) { posStr = raw.substring(6).trim(); break; }
        }
        if (posStr == null) return;
        BlockPosKey key = BlockPosKey.fromString(posStr);
        Optional<Shop> shopOpt = shops.get(key.toLocation().getBlock());
        if (!shopOpt.isPresent()) { p.sendMessage(Main.get().messages().prefixed("shop-not-found")); return; }

        KeeperBuyGUI.open(p, shopOpt.get());
    }

    public static class BrowseHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
