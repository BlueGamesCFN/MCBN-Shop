package de.mcbn.shops.shop;

import de.mcbn.shops.Main;
import de.mcbn.shops.util.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ScoreboardService implements Listener {

    private final Main plugin;
    private final ShopManager shops;
    private int taskId = -1;
    private final Map<UUID, Scoreboard> prevBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> ourBoards = new HashMap<>();

    public ScoreboardService(Main plugin, ShopManager shops) {
        this.plugin = plugin;
        this.shops = shops;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        long period = 10L; // 10 ticks ~ 0.5s
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                update(p);
            }
        }, 40L, period);
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        ourBoards.forEach((uuid, board) -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && prevBoards.containsKey(uuid)) {
                p.setScoreboard(prevBoards.get(uuid));
            }
        });
        ourBoards.clear();
        prevBoards.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ourBoards.remove(uuid);
        prevBoards.remove(uuid);
    }

    private void update(Player p) {
        int range = plugin.getConfig().getInt("look-range-blocks", 6);
        Block target = p.getTargetBlockExact(range);
        if (target == null || !shops.isShop(target)) {
            // remove if present
            if (ourBoards.containsKey(p.getUniqueId())) {
                Scoreboard prev = prevBoards.remove(p.getUniqueId());
                if (prev != null) p.setScoreboard(prev);
                ourBoards.remove(p.getUniqueId());
            }
            return;
        }
        Shop shop = shops.get(target).orElse(null);
        if (shop == null) return;

        Optional<Inventory> invOpt = ShopManager.getContainerInventory(target);
        if (!invOpt.isPresent()) return;
        Inventory inv = invOpt.get();

        int stockItems = InventoryUtils.countSimilar(inv, shop.template());
        int bundles = stockItems / shop.bundleAmount();
        String title = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("scoreboard.title", "&aMCBN &7Shop"));

        Scoreboard board = ourBoards.computeIfAbsent(p.getUniqueId(), u -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective obj = board.getObjective("mcbnshop");
        if (obj == null) obj = board.registerNewObjective("mcbnshop", "dummy", title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(title);

        // Clear previous entries by creating new board objective lines
        board.getEntries().forEach(board::resetScores);

        int score = 7;
        String itemName = (shop.template().getItemMeta() != null && shop.template().getItemMeta().hasDisplayName())
                ? shop.template().getItemMeta().getDisplayName()
                : formatMaterial(shop.template().getType());
        addLine(board, obj, ChatColor.YELLOW + "Item: " + ChatColor.WHITE + itemName, score--);
        addLine(board, obj, ChatColor.YELLOW + "Bundle: " + ChatColor.WHITE + shop.bundleAmount(), score--);
        addLine(board, obj, ChatColor.YELLOW + "Preis: " + ChatColor.AQUA + shop.price() + "x " + shop.currency().name(), score--);
        addLine(board, obj, ChatColor.YELLOW + "Bestand: " + ChatColor.WHITE + bundles + " Bundles", score--);
        if (bundles <= 0) {
            addLine(board, obj, ChatColor.RED + "Nicht genug Bestand!", score--);
        } else {
            addLine(board, obj, ChatColor.GRAY + "Sneak-Rechtsklick: kaufen", score--);
        }
        addLine(board, obj, ChatColor.DARK_GRAY + "Kiste ansehen zum Anzeigen", score--);

        if (!prevBoards.containsKey(p.getUniqueId())) {
            prevBoards.put(p.getUniqueId(), p.getScoreboard());
        }
        p.setScoreboard(board);
    }

    private void addLine(Scoreboard board, Objective obj, String text, int score) {
        String entry = text.substring(0, Math.min(40, text.length()));
        obj.getScore(entry).setScore(score);
    }

    private String formatMaterial(Material mat) {
        String name = mat.name().toLowerCase(java.util.Locale.ROOT).replace('_',' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
