package de.mcbn.shops.auction;

import de.mcbn.shops.Main;
import de.mcbn.shops.chat.ChatPromptService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI für Auktionserstellung (Setup) und Auktionshaus (Browse/Bieten).
 */
public class AuctionGUI implements Listener {

    private final Main plugin;
    private final AuctionManager manager;
    private final ChatPromptService prompts;

    public AuctionGUI(Main plugin, AuctionManager manager, ChatPromptService prompts) {
        this.plugin = plugin;
        this.manager = manager;
        this.prompts = prompts;
    }

    /* =================== Setup GUI =================== */

    public static void openSetup(Player p) {
        Inventory inv = Bukkit.createInventory(new SetupHolder(p.getUniqueId()), 54, ChatColor.DARK_AQUA + "Auktion erstellen");
        inv.setItem(53, button(Material.LIME_CONCRETE, ChatColor.GREEN + "Bestätigen"));
        inv.setItem(45, button(Material.BARRIER, ChatColor.RED + "Abbrechen"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onSetupClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SetupHolder)) return;
        event.setCancelled(true);

        Player p = (Player) event.getWhoClicked();

        if (event.getRawSlot() < event.getInventory().getSize()) {
            // Klick im oberen (Setup-) Inventar
            if (event.getRawSlot() == 53) {
                // Bestätigen: Items aus Slots 0..44 einsammeln (Buttons ignorieren)
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < 45; i++) {
                    ItemStack it = event.getInventory().getItem(i);
                    if (it != null && it.getType() != Material.AIR) {
                        int amount = it.getAmount();
                        for (int j = 0; j < amount; j++) {
                            ItemStack copy = it.clone();
                            copy.setAmount(1);
                            items.add(copy);
                        }
                    }
                }
                // Setup-Felder leeren und GUI schließen
                for (int i = 0; i < 45; i++) event.getInventory().setItem(i, null);
                p.closeInventory();
                manager.createAuctionFromSetup(p, items);

            } else if (event.getRawSlot() == 45) {
                // Abbrechen: Items zurückgeben
                for (int i = 0; i < 45; i++) {
                    ItemStack it = event.getInventory().getItem(i);
                    if (it != null) p.getInventory().addItem(it);
                    event.getInventory().setItem(i, null);
                }
                p.closeInventory();
            }
        } else {
            // Klick im Spielerinventar (unten) -> Shift-Move als Komfort
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                ItemStack cursor = event.getCurrentItem();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    int remain = cursor.getAmount();
                    for (int i = 0; i < 45 && remain > 0; i++) {
                        if (event.getInventory().getItem(i) == null) {
                            ItemStack one = cursor.clone();
                            one.setAmount(1);
                            event.getInventory().setItem(i, one);
                            remain--;
                        }
                    }
                    cursor.setAmount(remain);
                    event.setCurrentItem(remain <= 0 ? null : cursor);
                }
            }
        }
    }

    @EventHandler
    public void onSetupClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SetupHolder)) return;
        // Restliche Items zurück
        Player p = (Player) event.getPlayer();
        for (int i = 0; i < 45; i++) {
            ItemStack it = event.getInventory().getItem(i);
            if (it != null) p.getInventory().addItem(it);
        }
    }

    private static class SetupHolder implements InventoryHolder {
        private final java.util.UUID owner;
        public SetupHolder(java.util.UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }

    private static ItemStack button(Material mat, String name) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        meta.setDisplayName(name);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        is.setItemMeta(meta);
        return is;
    }

    /* =================== Browse GUI =================== */

    public static void openBrowse(Player p, List<Auction> auctions) {
        int lots = auctions.stream().mapToInt(a -> a.lots().size()).sum();
        int size = ((lots + 8) / 9) * 9;
        size = Math.min(Math.max(9, size), 54);

        Inventory inv = Bukkit.createInventory(new BrowseHolder(), size, ChatColor.DARK_AQUA + "Auktionshaus");

        int slot = 0;
        for (Auction a : auctions) {
            // Calculate time remaining
            long remaining = a.endMillis() - System.currentTimeMillis();
            String timeStr = formatTimeRemaining(remaining);
            boolean isActive = a.isActive();

            for (AuctionLot lot : a.lots()) {
                ItemStack it = lot.item().clone();
                ItemMeta meta = it.getItemMeta();
                List<String> lore = new ArrayList<>();

                // Amount info (if stack)
                if (it.getAmount() > 1) {
                    lore.add(ChatColor.GOLD + "Menge: " + ChatColor.WHITE + it.getAmount() + "x");
                }

                lore.add(ChatColor.GRAY + "Start: " + ChatColor.AQUA + lot.startBid());
                lore.add(ChatColor.GRAY + "Aktuell: " + ChatColor.AQUA + lot.currentPrice());
                lore.add(ChatColor.GRAY + "Währung: " + ChatColor.WHITE + a.currency().name());

                // Time remaining
                if (isActive) {
                    lore.add(ChatColor.GREEN + "Endet in: " + ChatColor.WHITE + timeStr);
                } else {
                    lore.add(ChatColor.RED + "Beendet!");
                }

                // Bidder status
                if (lot.highestBidder() != null) {
                    if (lot.highestBidder().equals(p.getUniqueId())) {
                        lore.add(ChatColor.GOLD + "★ Du bist Höchstbieter! ★");
                    } else {
                        lore.add(ChatColor.GRAY + "Höchstbieter: " + ChatColor.YELLOW + "Anderer Spieler");
                    }
                } else {
                    lore.add(ChatColor.RED + "Keine Gebote");
                }

                lore.add("");
                lore.add(ChatColor.DARK_GRAY + "Auktions-ID: " + a.id() + " | Los: " + lot.id());
                meta.setLore(lore);
                it.setItemMeta(meta);

                inv.setItem(slot++, it);
                if (slot >= size) break;
            }
            if (slot >= size) break;
        }

        p.openInventory(inv);
    }

    @EventHandler
    public void onBrowseClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BrowseHolder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() >= event.getInventory().getSize()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player p = (Player) event.getWhoClicked();

        if (clicked.getItemMeta() == null || clicked.getItemMeta().getLore() == null) return;
        String idLine = clicked.getItemMeta().getLore().stream()
                .filter(s -> ChatColor.stripColor(s).contains("Auktions-ID"))
                .findFirst().orElse(null);
        if (idLine == null) return;

        String[] parts = ChatColor.stripColor(idLine).split("\\|");
        if (parts.length < 2) return;

        String auctionId = parts[0].split(":")[1].trim();
        String lotId = parts[1].split(":")[1].trim();

        Auction a = manager.getAuctions().get(auctionId);
        if (a == null) return;

        AuctionLot lot = a.lots().stream().filter(l -> l.id().equals(lotId)).findFirst().orElse(null);
        if (lot == null) return;

        int current = lot.currentPrice();

        String itemName = (clicked.getItemMeta().hasDisplayName()
                ? clicked.getItemMeta().getDisplayName()
                : clicked.getType().name());

        prompts.ask(p, manager.getMessages().format("auction-bid-prompt",
                        "item", itemName,
                        "current", String.valueOf(current)),
                (pl, input) -> {
                    int bid;
                    try {
                        bid = Integer.parseInt(input.trim());
                        if (bid <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException ex) {
                        pl.sendMessage(manager.getMessages().prefixed("auction-bid-too-low"));
                        return;
                    }
                    manager.tryBid(pl, a, lot, bid);
                });

        p.closeInventory();
    }

    private static class BrowseHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    /**
     * Formatiert Millisekunden zu lesbarer Zeit.
     */
    private static String formatTimeRemaining(long ms) {
        if (ms <= 0) return "Abgelaufen";

        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms / (1000 * 60)) % 60;
        long seconds = (ms / 1000) % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            if (minutes > 5) {
                return minutes + "m";
            } else {
                return minutes + "m " + seconds + "s";
            }
        } else {
            return seconds + "s";
        }
    }
}
