package de.mcbn.shops;

import de.mcbn.shops.auction.AuctionGUI;
import de.mcbn.shops.auction.AuctionManager;
import de.mcbn.shops.chat.ChatPromptService;
import de.mcbn.shops.keeper.KeeperCommands;
import de.mcbn.shops.keeper.KeeperListener;
import de.mcbn.shops.keeper.KeeperManager;
import de.mcbn.shops.order.OrderManager;
import de.mcbn.shops.shop.ScoreboardService;
import de.mcbn.shops.shop.ShopCommands;
import de.mcbn.shops.shop.ShopListener;
import de.mcbn.shops.shop.ShopManager;
import de.mcbn.shops.shop.gui.ShopBuyGUI;
import de.mcbn.shops.shop.gui.ShopCreateGUI;
import de.mcbn.shops.util.BossBarService;
import de.mcbn.shops.util.DisplayService;
import de.mcbn.shops.util.Messages;
import de.mcbn.shops.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class Main extends JavaPlugin {
    private static Main instance;
    private Messages messages;
    private ChatPromptService prompts;
    private ShopManager shopManager;
    private ScoreboardService scoreboardService;
    private AuctionManager auctionManager;
    private BossBarService bossBarService;
    private DisplayService displayService;
    private KeeperManager keeperManager;
    private OrderManager orderManager;
    private Scheduler scheduler;

    public static Main get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.messages = new Messages(this);
        this.prompts = new ChatPromptService(this);
        this.shopManager = new ShopManager(this);
        this.scoreboardService = new ScoreboardService(this, shopManager);
        this.auctionManager = new AuctionManager(this, prompts);
        this.bossBarService = new BossBarService(this, auctionManager);
        this.displayService = new DisplayService(this, shopManager);
        this.keeperManager = new KeeperManager(this);
        this.orderManager = new OrderManager(this);
        this.scheduler = new Scheduler(this);

        // --- Listener-Registrierungen ---
        Bukkit.getPluginManager().registerEvents(prompts, this);
        Bukkit.getPluginManager().registerEvents(new ShopListener(this, shopManager, prompts), this);
        Bukkit.getPluginManager().registerEvents(new AuctionGUI(this, auctionManager, prompts), this);
        Bukkit.getPluginManager().registerEvents(new KeeperListener(this, keeperManager, shopManager, prompts), this);
        Bukkit.getPluginManager().registerEvents(new de.mcbn.shops.keeper.gui.KeeperMenuGUI(this, keeperManager, shopManager), this);

        // --- NEU: GUI-basierte Shop-Funktionen ---
        Bukkit.getPluginManager().registerEvents(new ShopCreateGUI(this, shopManager), this);
        Bukkit.getPluginManager().registerEvents(new ShopBuyGUI(this, shopManager), this);

        // --- Commands ---
        Objects.requireNonNull(getCommand("shop"))
                .setExecutor(new ShopCommands(this, shopManager, prompts));
        Objects.requireNonNull(getCommand("auction"))
                .setExecutor(auctionManager.getCommandExecutor());
        Objects.requireNonNull(getCommand("shopkeeper"))
                .setExecutor(new KeeperCommands(this, keeperManager, shopManager, orderManager));
        Objects.requireNonNull(getCommand("mcbnshops"))
                .setExecutor((sender, cmd, label, args) -> {
                    if (!sender.hasPermission("mcbn.admin")) {
                        sender.sendMessage(messages.prefixed("no-permission"));
                        return true;
                    }
                    reloadEverything();
                    sender.sendMessage(messages.prefixed("reloaded"));
                    return true;
                });

        // --- Daten laden ---
        shopManager.loadShops();
        auctionManager.loadAuctions();
        keeperManager.load();
        orderManager.load();

        // --- Hintergrunddienste starten ---
        scoreboardService.start();
        bossBarService.start();
        displayService.start();
        scheduler.startAutosave();

        getLogger().info("MCBN-Shops erfolgreich gestartet!");
    }

    @Override
    public void onDisable() {
        try {
            scoreboardService.stop();
            bossBarService.stop();
            displayService.stop();
            auctionManager.saveAuctions();
            shopManager.saveShops();
            keeperManager.save();
            orderManager.save();
            getLogger().info("MCBN-Shops Daten gespeichert und Dienste beendet.");
        } catch (Exception e) {
            getLogger().severe("Fehler beim Speichern: " + e.getMessage());
        }
    }

    // --- Getter ---
    public Messages messages() { return messages; }
    public ChatPromptService prompts() { return prompts; }
    public ShopManager shops() { return shopManager; }
    public AuctionManager auctions() { return auctionManager; }
    public DisplayService displayService() { return displayService; }
    public KeeperManager keepers() { return keeperManager; }
    public OrderManager orders() { return orderManager; }

    // --- Reload-Funktion ---
    public void reloadEverything() {
        reloadConfig();
        messages.reload();

        shopManager.saveShops();
        auctionManager.saveAuctions();
        keeperManager.save();
        orderManager.save();

        shopManager.loadShops();
        auctionManager.loadAuctions();
        keeperManager.load();
        orderManager.load();

        bossBarService.reloadFromConfig();
        displayService.reload();
    }
}
