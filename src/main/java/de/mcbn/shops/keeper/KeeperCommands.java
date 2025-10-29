package de.mcbn.shops.keeper;

import de.mcbn.shops.Main;
import de.mcbn.shops.chat.ChatPromptService;
import de.mcbn.shops.order.OrderManager;
import de.mcbn.shops.order.PurchaseOrder;
import de.mcbn.shops.shop.ShopManager;
import de.mcbn.shops.util.BlockPosKey;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.*;

public class KeeperCommands implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final KeeperManager keeperManager;
    private final ShopManager shops;
    private final OrderManager orders;
    private final ChatPromptService prompts;

    public KeeperCommands(Main plugin, KeeperManager manager, ShopManager shops, OrderManager orders) {
        this.plugin = plugin;
        this.keeperManager = manager;
        this.shops = shops;
        this.orders = orders;
        this.prompts = plugin.prompts();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Only players."); return true; }
        Player p = (Player) sender;
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "create": return create(p);
            case "link": return link(p);
            case "unlink": return unlink(p);
            case "remove": return remove(p);
            case "list": return list(p);
            case "tp": return tp(p);
            case "order": return order(p);
            case "hire": return hire(p);
            default:
                p.sendMessage("/shopkeeper <create|link|unlink|remove|list|tp|order|hire>");
                return true;
        }
    }

    private boolean create(Player p) {
        org.bukkit.Location loc = p.getLocation();
        ShopKeeper k = keeperManager.create(loc, p.getUniqueId());
        if (k == null) { p.sendMessage("§cShopkeepers sind deaktiviert."); return true; }
        p.sendMessage("§aShopkeeper erstellt. UUID: §f" + k.uuid());
        return true;
    }

    private boolean link(Player p) {
        Block target = p.getTargetBlockExact(plugin.getConfig().getInt("look-range-blocks", 6));
        if (target == null || !shops.isShop(target)) {
            p.sendMessage(plugin.messages().prefixed("shop-not-found")); return true;
        }
        ShopKeeper chosen = lastOwnKeeper(p.getUniqueId());
        if (chosen == null) { p.sendMessage("§cDu hast keinen Shopkeeper. Nutze §e/shopkeeper create§c."); return true; }
        keeperManager.link(chosen.uuid(), new BlockPosKey(target.getLocation()));
        p.sendMessage("§aShop mit Keeper verknüpft.");
        return true;
    }

    private boolean unlink(Player p) {
        Block target = p.getTargetBlockExact(plugin.getConfig().getInt("look-range-blocks", 6));
        if (target == null) {
            p.sendMessage(plugin.messages().prefixedFormat("must-look-at-block",
                    "range", String.valueOf(plugin.getConfig().getInt("look-range-blocks", 6))));
            return true;
        }
        ShopKeeper chosen = lastOwnKeeper(p.getUniqueId());
        if (chosen == null) { p.sendMessage("§cDu hast keinen Shopkeeper."); return true; }
        keeperManager.unlink(chosen.uuid(), new BlockPosKey(target.getLocation()));
        p.sendMessage("§aShop vom Keeper getrennt.");
        return true;
    }

    private boolean remove(Player p) {
        ShopKeeper chosen = lastOwnKeeper(p.getUniqueId());
        if (chosen == null) { p.sendMessage("§cDu hast keinen Shopkeeper."); return true; }
        keeperManager.remove(chosen.uuid());
        p.sendMessage("§aShopkeeper entfernt.");
        return true;
    }

    private boolean list(Player p) {
        int count = 0;
        for (ShopKeeper k : keeperManager.all()) if (k.owner().equals(p.getUniqueId())) {
            count++;
            p.sendMessage("§7Keeper §f" + k.uuid() + " §7Links: §f" + k.linked().size());
        }
        if (count == 0) p.sendMessage("§7Keine Keeper vorhanden.");
        return true;
    }

    private boolean tp(Player p) {
        ShopKeeper chosen = lastOwnKeeper(p.getUniqueId());
        if (chosen == null) { p.sendMessage("§cDu hast keinen Shopkeeper."); return true; }
        org.bukkit.World w = p.getServer().getWorld(chosen.world());
        if (w == null) { p.sendMessage("§cWelt nicht geladen."); return true; }
        p.teleport(new org.bukkit.Location(w, chosen.x() + 0.5, chosen.y(), chosen.z() + 0.5));
        p.sendMessage("§aTeleportiert zum Shopkeeper.");
        return true;
    }

    /** Chat-basierter Dialog: Item -> Menge -> (optional) Maxpreis; am Ende Buch erzeugen. */
    private boolean order(Player p) {
        final PurchaseOrder tmp = orders.create(p.getUniqueId());
        p.sendMessage("§aEinkaufsliste erstellen. Tippe §e'cancel' §aum abzubrechen, §e'done' §azum Fertigstellen.");
        askItem(p, tmp);
        return true;
    }

    private void askItem(Player p, PurchaseOrder tmp) {
        prompts.ask(p, "§7Item-Name (z.B. STRING, DIAMOND, MENDING_BOOK) oder 'done':", (player, input) -> {
            String in = input.trim().toUpperCase(Locale.ROOT);
            if (in.equals("CANCEL")) { player.sendMessage("§7Abgebrochen."); return; }
            if (in.equals("DONE")) { finishOrder(player, tmp); return; }
            Material mat = Material.matchMaterial(in);
            if (mat == null) { player.sendMessage("§cUnbekanntes Material."); askItem(player, tmp); return; }
            askAmount(player, tmp, mat);
        });
    }

    private void askAmount(Player p, PurchaseOrder tmp, Material mat) {
        prompts.ask(p, "§7Menge für §e" + mat.name() + "§7 angeben (Zahl):", (player, input) -> {
            try {
                int amt = Integer.parseInt(input.trim());
                if (amt <= 0) throw new NumberFormatException();
                askMaxPrice(player, tmp, mat, amt);
            } catch (NumberFormatException ex) {
                player.sendMessage("§cBitte eine positive Zahl eingeben.");
                askAmount(player, tmp, mat);
            }
        });
    }

    private void askMaxPrice(Player p, PurchaseOrder tmp, Material mat, int amt) {
        prompts.ask(p, "§7Maximalpreis pro Stück (leer = egal):", (player, input) -> {
            int max = 0;
            if (!input.trim().isEmpty()) {
                try {
                    max = Integer.parseInt(input.trim());
                    if (max < 0) max = 0;
                } catch (NumberFormatException ignored) {}
            }
            tmp.put(mat, amt, max);
            player.sendMessage("§aHinzugefügt: §f" + amt + "x " + mat.name() + (max>0 ? " §7(max " + max + "/Stk)" : ""));
            askItem(player, tmp);
        });
    }

    private void finishOrder(Player p, PurchaseOrder tmp) {
        if (tmp.wanted().isEmpty()) { p.sendMessage("§7Keine Einträge."); return; }
        // Buch erzeugen
        ItemStack book = new ItemStack(org.bukkit.Material.WRITABLE_BOOK);
        BookMeta bm = (BookMeta) book.getItemMeta();
        bm.setTitle("Einkaufsliste");
        bm.setAuthor(p.getName());
        java.util.List<String> pages = orders.toBookPages(tmp);
        bm.setPages(pages);
        book.setItemMeta(bm);

        // Speichern + Buch geben
        orders.put(tmp);
        orders.save();
        HashMap<Integer, ItemStack> rem = p.getInventory().addItem(book);
        if (!rem.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), book);
        p.sendMessage("§aEinkaufs-Buch erstellt und gespeichert. Nutze §e/shopkeeper hire§a.");
    }

    /** Startet Einkaufsreise. Liest wenn möglich die Bestellung aus dem gehaltenen Buch. */
    private boolean hire(Player p) {
        // 1) Versuche, Buch aus der Hand zu lesen
        ItemStack inHand = p.getInventory().getItemInMainHand();
        PurchaseOrder orderFromBook = null;
        if (inHand != null && (inHand.getType() == org.bukkit.Material.WRITABLE_BOOK || inHand.getType() == org.bukkit.Material.WRITTEN_BOOK)) {
            orderFromBook = orders.parseFromBook(p.getUniqueId(), inHand);
        }
        // 2) Fallback: vorhandene Order des Spielers
        PurchaseOrder order = orderFromBook != null ? orderFromBook : orders.byOwner(p.getUniqueId()).orElse(null);
        if (order == null || order.wanted().isEmpty()) {
            p.sendMessage("§cKeine gültige Einkaufsliste gefunden. Halte ein Einkaufs-Buch oder nutze §e/shopkeeper order§c.");
            return true;
        }
        ShopKeeper chosen = lastOwnKeeper(p.getUniqueId());
        if (chosen == null) { p.sendMessage("§cDu hast keinen Shopkeeper."); return true; }

        new ShopperTask(plugin, keeperManager, shops, order, chosen).start(p);
        return true;
    }

    private ShopKeeper lastOwnKeeper(UUID owner) {
        ShopKeeper chosen = null;
        for (ShopKeeper k : keeperManager.all()) if (k.owner().equals(owner)) chosen = k;
        return chosen;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return java.util.Arrays.asList("create","link","unlink","remove","list","tp","order","hire");
        return new java.util.ArrayList<>();
    }
}
