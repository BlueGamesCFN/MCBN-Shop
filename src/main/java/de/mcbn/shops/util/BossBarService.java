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
import java.util.List;

/**
 * Periodische Bossbar-Ausstrahlung aller aktiven Auktions-Lots.
 */
public class BossBarService {

    private final Main plugin;
    private final AuctionManager auctionManager;
    private int scheduleTaskId = -1;

    public BossBarService(Main plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
    }

    /** Startet das periodische Broadcasting gemäß Config. */
    public void start() {
        reloadFromConfig();
    }

    /** Stoppt das periodische Broadcasting. */
    public void stop() {
        if (scheduleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(scheduleTaskId);
            scheduleTaskId = -1;
        }
    }

    /** Liest das Intervall aus der Config neu ein und plant den Task neu. */
    public void reloadFromConfig() {
        stop();
        int everyMin = plugin.getConfig().getInt("bossbar.broadcast-interval-minutes", 15);
        long period = Math.max(1, everyMin) * 60L * 20L;
        scheduleTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::broadcastAllLots, period, period);
    }

    private void broadcastAllLots() {
        List<Auction> auctions = new ArrayList<>(auctionManager.allAuctions());
        if (auctions.isEmpty()) return;

        // Alle Variablen, die in Lambdas benutzt werden, final machen:
        final int perItemSeconds = plugin.getConfig().getInt("bossbar.display-seconds-per-item", 5);
        final long perItemTicks = perItemSeconds * 20L;

        final BarColor color = parseBarColor(plugin.getConfig().getString("bossbar.color", "BLUE"));
        final BarStyle style = parseBarStyle(plugin.getConfig().getString("bossbar.style", "SEGMENTED_10"));

        long delay = 0L;
        for (Auction a : auctions) {
            for (AuctionLot lot : a.lots()) {
                // effektiv finale Kopien fürs Lambda
                final AuctionLot lotCopy = lot;
                final long runAt = delay;

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    BossBar bar = Bukkit.createBossBar(
                            "Auktion: " + pretty(lotCopy.item()) + " - Gebot: " + lotCopy.currentPrice(),
                            color, style
                    );
                    for (Player pl : Bukkit.getOnlinePlayers()) {
                        bar.addPlayer(pl);
                    }
                    bar.setProgress(1.0);
                    Bukkit.getScheduler().runTaskLater(plugin, bar::removeAll, perItemTicks);
                }, runAt);

                delay += perItemTicks;
            }
        }
    }

    private static BarColor parseBarColor(String name) {
        try {
            return BarColor.valueOf(name);
        } catch (Exception e) {
            return BarColor.BLUE;
        }
    }

    private static BarStyle parseBarStyle(String name) {
        try {
            return BarStyle.valueOf(name);
        } catch (Exception e) {
            // Fallback konsistent mit vorherigem Verhalten
            return BarStyle.SOLID;
        }
    }

    private String pretty(org.bukkit.inventory.ItemStack is) {
        if (is.getItemMeta() != null && is.getItemMeta().hasDisplayName()) {
            return is.getItemMeta().getDisplayName();
        }
        return is.getType().name();
    }
}
