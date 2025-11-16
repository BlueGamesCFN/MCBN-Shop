package de.mcbn.shops.util;

import de.mcbn.shops.Main;
import de.mcbn.shops.auction.Auction;
import de.mcbn.shops.auction.AuctionLot;
import de.mcbn.shops.auction.AuctionManager;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * BossBar-Service für Auktionsanzeigen.
 * Unterstützt zwei Modi:
 * - PERMANENT: BossBars bleiben sichtbar und zeigen verbleibende Zeit
 * - PERIODIC: BossBars erscheinen periodisch für kurze Zeit
 */
public class BossBarService {

    private final Main plugin;
    private final AuctionManager auctionManager;
    private int scheduleTaskId = -1;
    private final Map<String, BossBar> permanentBars = new HashMap<>();

    public BossBarService(Main plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
    }

    /** Startet das Broadcasting gemäß Config. */
    public void start() {
        reloadFromConfig();
    }

    /** Stoppt das Broadcasting und entfernt alle BossBars. */
    public void stop() {
        if (scheduleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(scheduleTaskId);
            scheduleTaskId = -1;
        }
        removeAllPermanentBars();
    }

    /** Liest die Config neu ein und startet den passenden Modus. */
    public void reloadFromConfig() {
        stop();

        String mode = plugin.getConfig().getString("bossbar.mode", "PERIODIC").toUpperCase();

        if ("PERMANENT".equals(mode)) {
            startPermanentMode();
        } else {
            startPeriodicMode();
        }
    }

    /**
     * PERMANENT Mode: BossBars bleiben sichtbar und zeigen verbleibende Zeit.
     */
    private void startPermanentMode() {
        int updateSeconds = plugin.getConfig().getInt("bossbar.update-interval-seconds", 30);
        long period = Math.max(1, updateSeconds) * 20L;

        // Sofort starten, dann wiederholen
        scheduleTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            plugin,
            this::updatePermanentBars,
            0L,  // Start immediately
            period
        );

        plugin.getLogger().fine("BossBar PERMANENT mode gestartet (update alle " + updateSeconds + "s)");
    }

    /**
     * PERIODIC Mode: BossBars erscheinen periodisch für kurze Zeit.
     */
    private void startPeriodicMode() {
        int broadcastMinutes = plugin.getConfig().getInt("bossbar.broadcast-interval-minutes", 15);
        long period = Math.max(1, broadcastMinutes) * 60L * 20L;

        // Sofort starten, dann wiederholen
        scheduleTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            plugin,
            this::broadcastAllLotsTemporary,
            0L,  // Start immediately - FIX: was 'period' before
            period
        );

        plugin.getLogger().fine("BossBar PERIODIC mode gestartet (alle " + broadcastMinutes + " min)");
    }

    /**
     * Aktualisiert oder erstellt permanente BossBars für alle aktiven Auktionen.
     */
    private void updatePermanentBars() {
        List<Auction> auctions = new ArrayList<>(auctionManager.allAuctions());

        // Remove bars for auctions that no longer exist
        List<String> toRemove = new ArrayList<>();
        for (String lotId : permanentBars.keySet()) {
            if (!lotExists(auctions, lotId)) {
                toRemove.add(lotId);
            }
        }
        for (String lotId : toRemove) {
            removePermanentBar(lotId);
        }

        // Update or create bars for active auctions
        BarColor color = parseBarColor(plugin.getConfig().getString("bossbar.color", "BLUE"));
        BarStyle style = parseBarStyle(plugin.getConfig().getString("bossbar.style", "SEGMENTED_10"));

        for (Auction auction : auctions) {
            long endTime = auction.endMillis();
            long now = System.currentTimeMillis();
            long remaining = endTime - now;

            if (remaining <= 0) continue; // Skip expired auctions

            for (AuctionLot lot : auction.lots()) {
                String lotId = lot.id();
                String title = buildPermanentTitle(lot, remaining);
                double progress = calculateProgress(auction, now);

                BossBar bar = permanentBars.get(lotId);
                if (bar == null) {
                    // Create new permanent bar
                    bar = Bukkit.createBossBar(title, color, style);
                    bar.setProgress(progress);

                    // Add all online players
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        bar.addPlayer(p);
                    }

                    permanentBars.put(lotId, bar);
                } else {
                    // Update existing bar
                    bar.setTitle(title);
                    bar.setProgress(progress);

                    // PERFORMANCE FIX: Optimiere Player-Check
                    // contains() ist teuer bei vielen Spielern
                    // Verwende Set für schnelleren Lookup
                    java.util.Set<Player> currentPlayers = new java.util.HashSet<>(bar.getPlayers());
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!currentPlayers.contains(p)) {
                            bar.addPlayer(p);
                        }
                    }
                }
            }
        }
    }

    /**
     * Broadcastet alle Auktionen temporär (PERIODIC mode).
     * PERFORMANCE FIX: Limitiere Anzahl der temporären Tasks
     */
    private void broadcastAllLotsTemporary() {
        List<Auction> auctions = new ArrayList<>(auctionManager.allAuctions());
        if (auctions.isEmpty()) return;

        final int perItemSeconds = plugin.getConfig().getInt("bossbar.display-seconds-per-item", 5);
        final long perItemTicks = perItemSeconds * 20L;

        final BarColor color = parseBarColor(plugin.getConfig().getString("bossbar.color", "BLUE"));
        final BarStyle style = parseBarStyle(plugin.getConfig().getString("bossbar.style", "SEGMENTED_10"));

        // PERFORMANCE FIX: Limitiere auf max 10 Lots pro Broadcast
        // Verhindert hunderte von Tasks bei vielen Auktionen
        final int maxLots = plugin.getConfig().getInt("bossbar.max-lots-per-broadcast", 10);
        int lotCount = 0;

        long delay = 0L;
        outer:
        for (Auction auction : auctions) {
            long remaining = auction.endMillis() - System.currentTimeMillis();
            if (remaining <= 0) continue; // Skip expired

            for (AuctionLot lot : auction.lots()) {
                if (lotCount >= maxLots) break outer;

                final AuctionLot lotCopy = lot;
                final long remainingCopy = remaining;
                final long runAt = delay;

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    String title = buildTemporaryTitle(lotCopy, remainingCopy);
                    BossBar bar = Bukkit.createBossBar(title, color, style);

                    for (Player pl : Bukkit.getOnlinePlayers()) {
                        bar.addPlayer(pl);
                    }

                    bar.setProgress(1.0);
                    Bukkit.getScheduler().runTaskLater(plugin, bar::removeAll, perItemTicks);
                }, runAt);

                delay += perItemTicks;
                lotCount++;
            }
        }
    }

    /**
     * Erstellt den Titel für PERMANENT mode mit verbleibender Zeit.
     */
    private String buildPermanentTitle(AuctionLot lot, long remainingMs) {
        String timeStr = formatTime(remainingMs);
        String itemName = pretty(lot.item());
        int currentPrice = lot.currentPrice();

        if (lot.highestBidder() != null) {
            return "§6" + itemName + " §7| §e" + currentPrice + " §7| §aEndet in: §f" + timeStr;
        } else {
            return "§6" + itemName + " §7| §eStart: " + lot.startBid() + " §7| §cKeine Gebote §7| §f" + timeStr;
        }
    }

    /**
     * Erstellt den Titel für PERIODIC mode.
     */
    private String buildTemporaryTitle(AuctionLot lot, long remainingMs) {
        String timeStr = formatTime(remainingMs);
        String itemName = pretty(lot.item());
        int currentPrice = lot.currentPrice();

        return "§6Auktion: " + itemName + " §7- §eGebot: §f" + currentPrice + " §7- §aEndet in: §f" + timeStr;
    }

    /**
     * Formatiert Millisekunden zu lesbarer Zeit (z.B. "2h 15m" oder "45m" oder "5m 30s").
     */
    private String formatTime(long ms) {
        if (ms <= 0) return "abgelaufen";

        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;

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

    /**
     * Berechnet den Fortschrittsbalken (1.0 = gerade gestartet, 0.0 = fast abgelaufen).
     */
    private double calculateProgress(Auction auction, long now) {
        long total = auction.durationMillis();
        long elapsed = now - auction.startMillis();

        if (total <= 0) return 0.0;

        double progress = 1.0 - ((double) elapsed / total);
        return Math.max(0.0, Math.min(1.0, progress));
    }

    /**
     * Prüft ob ein Lot mit der ID noch existiert.
     */
    private boolean lotExists(List<Auction> auctions, String lotId) {
        for (Auction auction : auctions) {
            for (AuctionLot lot : auction.lots()) {
                if (lot.id().equals(lotId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Entfernt eine permanente BossBar.
     */
    private void removePermanentBar(String lotId) {
        BossBar bar = permanentBars.remove(lotId);
        if (bar != null) {
            bar.removeAll();
        }
    }

    /**
     * Entfernt alle permanenten BossBars.
     */
    private void removeAllPermanentBars() {
        for (BossBar bar : permanentBars.values()) {
            bar.removeAll();
        }
        permanentBars.clear();
    }

    private static BarColor parseBarColor(String name) {
        try {
            return BarColor.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return BarColor.BLUE;
        }
    }

    private static BarStyle parseBarStyle(String name) {
        try {
            return BarStyle.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return BarStyle.SOLID;
        }
    }

    private String pretty(org.bukkit.inventory.ItemStack is) {
        if (is.getItemMeta() != null && is.getItemMeta().hasDisplayName()) {
            return is.getItemMeta().getDisplayName();
        }
        String name = is.getType().name().replace('_', ' ').toLowerCase();
        if (is.getAmount() > 1) {
            return name + " x" + is.getAmount();
        }
        return name;
    }
}
