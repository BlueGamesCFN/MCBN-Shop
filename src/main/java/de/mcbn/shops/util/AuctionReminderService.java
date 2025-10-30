package de.mcbn.shops.util;

import de.mcbn.shops.Main;
import de.mcbn.shops.auction.AuctionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service für periodische Erinnerungen an nicht abgeholte Auktionsgewinne.
 * <p>
 * Sendet alle X Minuten (konfigurierbar) Erinnerungen an Spieler,
 * die noch Items oder Währung abholen müssen.
 */
public class AuctionReminderService {

    private final Main plugin;
    private final AuctionManager auctionManager;
    private int taskId = -1;

    public AuctionReminderService(Main plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
    }

    /**
     * Startet den Reminder-Service.
     */
    public void start() {
        stop(); // Stop existing task if any

        int intervalMinutes = plugin.getConfig().getInt("auctions.reminder-interval-minutes", 15);
        if (intervalMinutes <= 0) {
            plugin.getLogger().info("Auction reminders deaktiviert (interval <= 0)");
            return;
        }

        long interval = intervalMinutes * 60L * 20L;

        // Start immediately, then repeat
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            plugin,
            this::sendReminders,
            0L,  // Start immediately
            interval
        );

        plugin.getLogger().fine("Auction reminder service gestartet (alle " + intervalMinutes + " Minuten)");
    }

    /**
     * Stoppt den Reminder-Service.
     */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    /**
     * Sendet Erinnerungen an alle Spieler mit nicht abgeholten Items/Währung.
     */
    private void sendReminders() {
        Map<UUID, List<ItemStack>> pendingItems = auctionManager.getPendingItems();
        Map<UUID, Integer> pendingCurrency = auctionManager.getPendingCurrency();

        // Collect all players with pending claims
        for (UUID playerId : pendingItems.keySet()) {
            sendReminder(playerId, pendingItems.get(playerId), pendingCurrency.getOrDefault(playerId, 0));
        }

        // Also check players with only currency
        for (UUID playerId : pendingCurrency.keySet()) {
            if (!pendingItems.containsKey(playerId)) {
                sendReminder(playerId, null, pendingCurrency.get(playerId));
            }
        }
    }

    /**
     * Sendet eine Erinnerung an einen Spieler.
     *
     * @param playerId Der Spieler
     * @param items Liste der nicht abgeholten Items (kann null sein)
     * @param currency Menge der nicht abgeholten Währung
     */
    private void sendReminder(UUID playerId, List<ItemStack> items, int currency) {
        Player player = Bukkit.getPlayer(playerId);

        // Only send to online players
        if (player == null || !player.isOnline()) {
            return;
        }

        int itemCount = (items != null) ? items.size() : 0;

        // Build reminder message
        if (itemCount > 0 && currency > 0) {
            // Both items and currency
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§e§lAuktions-Erinnerung");
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");
            player.sendMessage("§7Du hast noch §e" + itemCount + " Item(s) §7und");
            player.sendMessage("§e" + currency + " Währung §7abzuholen!");
            player.sendMessage("");
            player.sendMessage("§7Nutze §a/auction claim §7um alles abzuholen.");
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } else if (itemCount > 0) {
            // Only items
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§e§lAuktions-Erinnerung");
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");
            player.sendMessage("§7Du hast noch §e" + itemCount + " Item(s) §7abzuholen!");
            player.sendMessage("");
            player.sendMessage("§7Nutze §a/auction claim §7um alles abzuholen.");
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } else if (currency > 0) {
            // Only currency
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§e§lAuktions-Erinnerung");
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");
            player.sendMessage("§7Du hast noch §e" + currency + " Währung §7abzuholen!");
            player.sendMessage("");
            player.sendMessage("§7Nutze §a/auction claim §7um alles abzuholen.");
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        }
    }

    /**
     * Lädt die Konfiguration neu.
     */
    public void reload() {
        start();
    }
}
