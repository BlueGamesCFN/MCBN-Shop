package de.mcbn.shops.shop;

import de.mcbn.shops.Main;
import de.mcbn.shops.shop.gui.ShopCreateGUI;
import de.mcbn.shops.util.Messages;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ShopCommands implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final ShopManager shops;
    private final Messages msg;

    public ShopCommands(Main plugin, ShopManager shops, de.mcbn.shops.chat.ChatPromptService prompts) {
        this.plugin = plugin;
        this.shops = shops;
        this.msg = plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players.");
            return true;
        }
        Player p = (Player) sender;

        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "create": return handleCreate(p, args);
            case "remove": return handleRemove(p);
            case "buy": return handleBuy(p, args);
            case "info": return handleInfo(p);
            default:
                p.sendMessage("/shop <create|remove|buy|info>");
                return true;
        }
    }

    private boolean handleCreate(Player p, String[] args) {
        if (!p.hasPermission("mcbn.shops.create")) {
            p.sendMessage(msg.prefixed("no-permission")); return true;
        }

        Block target = p.getTargetBlockExact(plugin.getConfig().getInt("look-range-blocks", 6));
        if (target == null) {
            p.sendMessage(msg.prefixedFormat("must-look-at-block", "range",
                    String.valueOf(plugin.getConfig().getInt("look-range-blocks", 6))));
            return true;
        }
        if (shops.isShop(target)) {
            p.sendMessage(msg.prefixed("shop-exists")); return true;
        }
        Optional<Inventory> invOpt = ShopManager.getContainerInventory(target);
        if (!invOpt.isPresent()) {
            p.sendMessage(msg.prefixed("not-a-container")); return true;
        }

        // Template ermitteln
        Inventory inv = invOpt.get();
        ItemStack template = null;
        for (ItemStack it : inv.getContents()) {
            if (it != null && !it.getType().isAir()) { template = it.clone(); template.setAmount(1); break; }
        }
        if (template == null) {
            p.sendMessage(msg.prefixed("container-empty"));
            return true;
        }

        // Wenn Parameter vorhanden, direkt erstellen – sonst GUI öffnen
        if (args.length >= 3) {
            int bundle, price;
            try {
                bundle = Integer.parseInt(args[1]);
                price = Integer.parseInt(args[2]);
                if (bundle <= 0 || price <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                p.sendMessage("§cBitte gib gültige Zahlen für Menge und Preis an."); return true;
            }
            shops.createShop(p.getUniqueId(), target, template, bundle, price);
            p.sendMessage("§aShop erstellt: " + bundle + "x " + prettyItem(template) + " für " + price);
            return true;
        } else {
            // GUI-Variante
            new ShopCreateGUI(plugin, shops).open(p, target, template);
            return true;
        }
    }

    private boolean handleRemove(Player p) {
        if (!p.hasPermission("mcbn.shops.remove")) {
            p.sendMessage(msg.prefixed("no-permission")); return true;
        }
        Block target = p.getTargetBlockExact(plugin.getConfig().getInt("look-range-blocks", 6));
        if (target == null) {
            p.sendMessage(msg.prefixedFormat("must-look-at-block", "range",
                    String.valueOf(plugin.getConfig().getInt("look-range-blocks", 6))));
            return true;
        }
        // Atomic check-and-get zur Vermeidung von Race Conditions
        Optional<Shop> shopOpt = shops.get(target);
        if (!shopOpt.isPresent()) {
            p.sendMessage(msg.prefixed("shop-not-found")); return true;
        }
        Shop s = shopOpt.get();
        if (!s.owner().equals(p.getUniqueId()) && !p.hasPermission("mcbn.admin")) {
            p.sendMessage(msg.prefixed("not-owner")); return true;
        }
        shops.removeShop(target);
        p.sendMessage(msg.prefixed("shop-removed"));
        return true;
    }

    private boolean handleBuy(Player p, String[] args) {
        p.sendMessage("§7Tipp: Rechtsklick öffnet das Kauf-Menü. Linksklick = Schnellkauf (1 Bundle).");
        return true;
    }

    private boolean handleInfo(Player p) {
        Block target = p.getTargetBlockExact(plugin.getConfig().getInt("look-range-blocks", 6));
        if (target == null) {
            p.sendMessage(msg.prefixed("shop-not-found")); return true;
        }
        // Atomic check-and-get zur Vermeidung von Race Conditions
        Optional<Shop> shopOpt = shops.get(target);
        if (!shopOpt.isPresent()) {
            p.sendMessage(msg.prefixed("shop-not-found")); return true;
        }
        Shop s = shopOpt.get();
        p.sendMessage("§7Item: §f" + prettyItem(s.template()) +
                " §7Bundle: §f" + s.bundleAmount() +
                " §7Preis: §b" + s.price() + "x " + s.currency().name());
        return true;
    }

    /** ————————————————— Kauf-Logik (wird von Listenern & GUIs aufgerufen) ————————————————— */
    public void performPurchase(Player buyer, Block block, Shop s, int bundles) {
        Optional<Inventory> invOpt = ShopManager.getContainerInventory(block);
        if (!invOpt.isPresent()) {
            buyer.sendMessage(msg.prefixed("not-a-container"));
            return;
        }
        Inventory shopInv = invOpt.get();

        int stockItems = de.mcbn.shops.util.InventoryUtils.countSimilar(shopInv, s.template());
        int stockBundles = stockItems / s.bundleAmount();
        if (stockBundles < bundles) {
            buyer.sendMessage(msg.prefixedFormat("buy-insufficient-stock", "bundles", String.valueOf(stockBundles)));
            return;
        }

        int price = s.price() * bundles;
        int playerCurrency = 0;
        for (ItemStack is : buyer.getInventory().getContents()) {
            if (is != null && is.getType() == s.currency()) playerCurrency += is.getAmount();
        }
        if (playerCurrency < price) {
            buyer.sendMessage(msg.prefixedFormat("buy-insufficient-funds", "currency", s.currency().name()));
            return;
        }

        int removedCur = de.mcbn.shops.util.InventoryUtils.removeMaterial(buyer.getInventory(), s.currency(), price);
        if (removedCur != price) {
            buyer.sendMessage(msg.prefixedFormat("buy-insufficient-funds", "currency", s.currency().name()));
            return;
        }

        int toGiveItems = s.bundleAmount() * bundles;
        int removedItems = de.mcbn.shops.util.InventoryUtils.removeSimilar(shopInv, s.template(), toGiveItems);
        if (removedItems != toGiveItems) {
            buyer.getInventory().addItem(new ItemStack(s.currency(), price)); // rollback
            buyer.sendMessage(msg.prefixed("buy-insufficient-stock"));
            return;
        }

        ItemStack give = s.template().clone();
        give.setAmount(toGiveItems);
        de.mcbn.shops.util.InventoryUtils.giveOrDrop(buyer, give);

        ItemStack currencyStack = new ItemStack(s.currency(), price);
        de.mcbn.shops.util.InventoryUtils.addOrDropToInventory(shopInv, currencyStack, block.getLocation().add(0.5, 0.5, 0.5));

        buyer.sendMessage(msg.prefixedFormat("buy-success",
                "totalItems", String.valueOf(toGiveItems),
                "item", prettyItem(s.template()),
                "paid", String.valueOf(price),
                "currency", s.currency().name()));
    }

    private String prettyItem(ItemStack is) {
        if (is.getItemMeta() != null && is.getItemMeta().hasDisplayName()) return is.getItemMeta().getDisplayName();
        String n = is.getType().name().toLowerCase(Locale.ROOT).replace('_',' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) list.addAll(java.util.Arrays.asList("create","remove","buy","info"));
        return list;
    }
}
