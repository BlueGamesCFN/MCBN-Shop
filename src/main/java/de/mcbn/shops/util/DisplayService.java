package de.mcbn.shops.util;

import de.mcbn.shops.Main;
import de.mcbn.shops.shop.Shop;
import de.mcbn.shops.shop.ShopManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;

import java.util.Optional;

/**
 * Aktualisiert Schilder an der Vorderseite eines Shops mit Itemname, Preis und Bestand.
 * Keine ItemFrames mehr.
 */
public class DisplayService {
    private final Main plugin;
    private final ShopManager shopManager;
    private int taskId = -1;

    public DisplayService(Main plugin, ShopManager shops) {
        this.plugin = plugin;
        this.shopManager = shops;
    }

    public void start() {
        int refresh = Math.max(1, plugin.getConfig().getInt("display.refresh-seconds", 10));
        long period = refresh * 20L;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 40L, period);
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
    }

    public void reload() {
        stop();
        start();
    }

    /** Wird regelmäßig aufgerufen und stellt sicher, dass jedes Schild korrekt ist. */
    private void tick() {
        for (Shop shop : shopManager.all()) {
            Block container = shop.pos().toLocation().getBlock();
            if (container == null || container.getType().isAir()) continue;

            BlockFace front = BlockFace.NORTH;
            if (container.getBlockData() instanceof Directional dir) {
                front = dir.getFacing();
            }

            Block signBlock = container.getRelative(front);
            Optional<org.bukkit.inventory.Inventory> invOpt = ShopManager.getContainerInventory(container);
            if (!invOpt.isPresent()) continue;

            int stock = InventoryUtils.countSimilar(invOpt.get(), shop.template());
            boolean hasStock = stock >= shop.bundleAmount();

            if (!hasStock) {
                // Kein Vorrat → ggf. Text ändern
                if (isShopSign(signBlock)) {
                    Sign sign = (Sign) signBlock.getState();
                    sign.setLine(0, ChatColor.RED + "[Leer]");
                    sign.setLine(1, "");
                    sign.setLine(2, "");
                    sign.setLine(3, "");
                    sign.update();
                }
                continue;
            }

            // Wenn kein Schild vorhanden → erstellen
            if (!isShopSign(signBlock)) {
                signBlock.setType(Material.OAK_WALL_SIGN, false);
                org.bukkit.block.data.type.WallSign wallSign = (org.bukkit.block.data.type.WallSign) signBlock.getBlockData();
                wallSign.setFacing(front);
                signBlock.setBlockData(wallSign);
            }

            // Text aktualisieren (sauberes Farbformat)
            Sign sign = (Sign) signBlock.getState();
            sign.setLine(0, ChatColor.GOLD.toString() + ChatColor.BOLD + prettyItem(shop.template()));
            sign.setLine(1, ChatColor.YELLOW.toString() + shop.bundleAmount() + "x");
            sign.setLine(2, ChatColor.AQUA.toString() + "für " + ChatColor.WHITE + shop.price() + " " + shop.currency().name());
            sign.setLine(3, hasStock ? ChatColor.GREEN + "Verfügbar" : ChatColor.RED + "Leer");
            sign.update();
        }
    }

    /** Prüft, ob Block ein Shop-Schild ist */
    private boolean isShopSign(Block b) {
        return b.getType() == Material.OAK_WALL_SIGN || b.getType() == Material.SPRUCE_WALL_SIGN
                || b.getType() == Material.BIRCH_WALL_SIGN || b.getType() == Material.DARK_OAK_WALL_SIGN
                || b.getType() == Material.ACACIA_WALL_SIGN || b.getType() == Material.JUNGLE_WALL_SIGN;
    }

    private String prettyItem(org.bukkit.inventory.ItemStack is) {
        if (is.getItemMeta() != null && is.getItemMeta().hasDisplayName())
            return is.getItemMeta().getDisplayName();
        String n = is.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
