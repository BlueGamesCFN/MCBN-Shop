package de.mcbn.shops.keeper;

import de.mcbn.shops.Main;
import de.mcbn.shops.order.PurchaseOrder;
import de.mcbn.shops.shop.Shop;
import de.mcbn.shops.shop.ShopCommands;
import de.mcbn.shops.shop.ShopManager;
import de.mcbn.shops.util.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Führt eine globale Einkaufsreise durch.
 * Villager bewegt sich sichtbar zwischen Shops und kauft zum besten Preis
 * (unter Beachtung Maxpreis/Stk).
 */
public class ShopperTask {

    private final Main plugin;
    private final KeeperManager keepers;
    private final ShopManager shops;
    private final PurchaseOrder order;
    private final ShopKeeper keeper;
    private int taskId = -1;

    public ShopperTask(Main plugin, KeeperManager keepers, ShopManager shops, PurchaseOrder order, ShopKeeper keeper) {
        this.plugin = plugin;
        this.keepers = keepers;
        this.shops = shops;
        this.order = order;
        this.keeper = keeper;
    }

    public void start(Player requester) {
        Entity e = keepers.findEntity(keeper.uuid());
        if (!(e instanceof Villager)) {
            requester.sendMessage("§cShopkeeper-Entity nicht gefunden.");
            return;
        }
        Villager v = (Villager) e;

        List<RouteStep> route = buildRoute(v.getLocation(), order);
        if (route.isEmpty()) {
            requester.sendMessage("§7Keine passenden Shops für deine Einkaufsliste gefunden.");
            return;
        }
        requester.sendMessage("§aEinkaufsreise gestartet. Wegpunkte: §f" + route.size());
        final Iterator<RouteStep> it = route.iterator();
        proceedToNextWaypoint(v, it, requester);
    }

    private void proceedToNextWaypoint(Villager v, Iterator<RouteStep> it, Player requester) {
        if (!it.hasNext()) {
            requester.sendMessage("§aEinkaufsreise beendet.");
            return;
        }
        final RouteStep step = it.next();
        moveVillager(v, step.loc, () -> {
            performPurchaseAt(step, requester);
            proceedToNextWaypoint(v, it, requester);
        });
    }

    private void moveVillager(Villager v, Location target, Runnable onArrive) {
        final Location targetFinal = target.clone(); // final Kopie
        final double step = Math.max(0.2, plugin.getConfig().getDouble("shopkeepers.walk-speed", 0.20));
        final int fallback = Math.max(1, plugin.getConfig().getInt("shopkeepers.step-teleport-fallback", 4));

        // MEMORY LEAK FIX: Cancle alten Task bevor neuer erstellt wird
        // Verhindert dass alte Tasks weiterlaufen
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int ticks = 0;
            @Override public void run() {
                if (v.isDead() || !v.isValid()) { Bukkit.getScheduler().cancelTask(taskId); return; }
                Location cur = v.getLocation();
                if (cur.getWorld() != targetFinal.getWorld()) {
                    v.teleport(targetFinal);
                    Bukkit.getScheduler().cancelTask(taskId);
                    onArrive.run();
                    return;
                }

                double dx = targetFinal.getX() - cur.getX();
                double dy = targetFinal.getY() - cur.getY();
                double dz = targetFinal.getZ() - cur.getZ();
                double distSq = dx*dx + dy*dy + dz*dz;
                if (distSq < 1.0) {
                    Bukkit.getScheduler().cancelTask(taskId);
                    onArrive.run();
                    return;
                }
                if (ticks % 2 == 0) {
                    double len = Math.sqrt(distSq);
                    Location next = new Location(cur.getWorld(),
                            cur.getX() + (dx/len)*step,
                            cur.getY() + (dy/len)*step,
                            cur.getZ() + (dz/len)*step);
                    v.teleport(next);
                }
                ticks++;
                if (ticks % (fallback*5) == 0) {
                    Location mid = cur.clone().add(dx*0.5, 0, dz*0.5);
                    int y = cur.getWorld().getHighestBlockYAt(mid);
                    v.teleport(new Location(cur.getWorld(), mid.getX(), y + 1, mid.getZ()));
                }
            }
        }, 1L, 1L);
    }

    private void performPurchaseAt(RouteStep step, Player requester) {
        Material mat = step.shop.template().getType();
        int stillNeeded = order.wanted().getOrDefault(mat, 0);
        if (stillNeeded <= 0) return;

        int maxPerItem = order.maxPrice().getOrDefault(mat, 0);
        int perItemCost = (int) Math.ceil(step.shop.price() / (double) step.shop.bundleAmount());
        if (maxPerItem > 0 && perItemCost > maxPerItem) {
            requester.sendMessage("§7Übersprungen (zu teuer): " + mat.name() + " @ " + perItemCost + "/Stk > max " + maxPerItem);
            return;
        }

        Block b = step.shop.pos().toLocation().getBlock();
        Optional<org.bukkit.inventory.Inventory> invOpt = ShopManager.getContainerInventory(b);
        if (!invOpt.isPresent()) return;

        int stockItems = InventoryUtils.countSimilar(invOpt.get(), step.shop.template());
        int stockBundles = stockItems / step.shop.bundleAmount();
        if (stockBundles <= 0) return;

        int bundlesNeeded = (int) Math.ceil(stillNeeded / (double) step.shop.bundleAmount());
        int bundlesToBuy = Math.min(bundlesNeeded, stockBundles);
        if (bundlesToBuy <= 0) return;

        int totalPrice = bundlesToBuy * step.shop.price();
        int fee = Math.max(0, (totalPrice * order.feePercent()) / 100);
        int charge = totalPrice + fee;

        Player owner = Bukkit.getPlayer(order.owner());
        if (owner == null) {
            requester.sendMessage("§7Owner offline, überspringe Kauf.");
            return;
        }

        int balance = 0;
        for (ItemStack is : owner.getInventory().getContents())
            if (is != null && is.getType() == step.shop.currency()) balance += is.getAmount();
        if (balance < charge) {
            requester.sendMessage("§7Zu wenig " + step.shop.currency().name() + " für " + mat.name());
            return;
        }

        new ShopCommands(plugin, shops, plugin.prompts()).performPurchase(owner, b, step.shop, bundlesToBuy);
        InventoryUtils.removeMaterial(owner.getInventory(), step.shop.currency(), fee);

        int itemsBought = step.shop.bundleAmount() * bundlesToBuy;
        int remaining = Math.max(0, stillNeeded - itemsBought);
        order._setRemaining(mat, remaining);
        requester.sendMessage("§aGekauft: §f" + itemsBought + "x " + mat.name() + " §7für §b" + totalPrice + " + Gebühr " + fee);
    }

    private static class RouteStep {
        final Shop shop;
        final Location loc;
        RouteStep(Shop s) { this.shop = s; this.loc = s.pos().toLocation().add(0.5, 1, 0.5); }
    }

    private List<RouteStep> buildRoute(Location start, PurchaseOrder order) {
        List<Shop> candidates = new ArrayList<>();
        for (Shop s : shops.all())
            if (order.wanted().containsKey(s.template().getType())) candidates.add(s);
        if (candidates.isEmpty()) return Collections.emptyList();

        candidates.sort(Comparator.comparingInt(Shop::price));
        List<RouteStep> route = new ArrayList<>();
        Location cur = start.clone();
        List<Shop> remaining = new ArrayList<>(candidates);

        while (!remaining.isEmpty()) {
            final Location curFinal = cur.clone(); // <- final Kopie für Lambda
            Shop next = remaining.stream()
                    .min(Comparator.comparingDouble(s -> s.pos().toLocation().distanceSquared(curFinal)))
                    .orElse(null);
            if (next == null) break;
            route.add(new RouteStep(next));
            cur = next.pos().toLocation();
            remaining.remove(next);
        }
        return route;
    }
}
