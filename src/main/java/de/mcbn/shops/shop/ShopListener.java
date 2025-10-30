package de.mcbn.shops.shop;

import de.mcbn.shops.Main;
import de.mcbn.shops.chat.ChatPromptService;
import de.mcbn.shops.shop.gui.ShopBuyGUI;
import de.mcbn.shops.util.Messages;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class ShopListener implements Listener {

    private final Main plugin;
    private final ShopManager shops;
    private final Messages msg;
    private final ChatPromptService prompts;
    private final ShopBuyGUI buyGui;

    public ShopListener(Main plugin, ShopManager shops, ChatPromptService prompts) {
        this.plugin = plugin;
        this.shops = shops;
        this.msg = plugin.messages();
        this.prompts = prompts;
        this.buyGui = null; // wird über Main separat registriert
    }

    /** Linksklick = sofort 1 Bundle kaufen */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        // Atomic check-and-get zur Vermeidung von Race Conditions
        Optional<Shop> shopOpt = shops.get(block);
        if (!shopOpt.isPresent()) return;
        Shop s = shopOpt.get();
        Player p = event.getPlayer();

        // Owner darf normal abbauen
        if (s.owner().equals(p.getUniqueId())) return;

        event.setCancelled(true);
        new ShopCommands(plugin, shops, prompts).performPurchase(p, block, s, 1);
    }

    /** Rechtsklick = Kauf-GUI öffnen (für Käufer). Owner darf Inventar öffnen. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        // Atomic check-and-get zur Vermeidung von Race Conditions
        Optional<Shop> shopOpt = shops.get(block);
        if (!shopOpt.isPresent()) return;
        Shop s = shopOpt.get();
        Player p = event.getPlayer();

        if (s.owner().equals(p.getUniqueId())) {
            // Owner darf Inventar normal öffnen
            return;
        }

        // Käufer: GUI statt Inventar
        event.setCancelled(true);
        // Zugriff auf das registrierte GUI über Plugin:
        de.mcbn.shops.shop.gui.ShopBuyGUI gui = plugin.getServer().getServicesManager()
                .load(de.mcbn.shops.shop.gui.ShopBuyGUI.class); // optional, falls als Service gebunden
        if (gui != null) {
            gui.open(p, block, s);
        } else {
            // Fallback: neue Instanz (Listener muss in Main registriert sein!)
            new de.mcbn.shops.shop.gui.ShopBuyGUI(plugin, shops).open(p, block, s);
        }
    }

    /** Vorsichtshalber auch Inventaröffnung blockieren (Nicht-Owner) */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player p = (Player) event.getPlayer();
        Block block = event.getInventory().getLocation() != null ? event.getInventory().getLocation().getBlock() : null;
        if (block == null) return;

        // Atomic check-and-get zur Vermeidung von Race Conditions
        Optional<Shop> shopOpt = shops.get(block);
        if (!shopOpt.isPresent()) return;
        Shop s = shopOpt.get();
        if (!s.owner().equals(p.getUniqueId()) && !p.hasPermission("mcbn.admin")) {
            event.setCancelled(true);
            p.sendMessage(plugin.messages().prefixed("chest-locked-shop"));
        }
    }

    /** Nur Owner/Admin dürfen abbauen */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();

        // Atomic check-and-get zur Vermeidung von Race Conditions
        Optional<Shop> shopOpt = shops.get(b);
        if (!shopOpt.isPresent()) return;
        Shop s = shopOpt.get();
        if (!s.owner().equals(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("mcbn.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.messages().prefixed("not-owner"));
            return;
        }
        shops.removeShop(b);
        event.getPlayer().sendMessage(plugin.messages().prefixed("chest-broken"));
    }

    /** Explosionen ignorieren Shops */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> shops.isShop(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> shops.isShop(block));
    }
}
