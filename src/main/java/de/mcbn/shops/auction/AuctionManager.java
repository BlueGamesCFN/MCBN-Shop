package de.mcbn.shops.auction;

import de.mcbn.shops.Main;
import de.mcbn.shops.chat.ChatPromptService;
import de.mcbn.shops.util.InventoryUtils;
import de.mcbn.shops.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Verwaltung von Auktionen inkl. Persistenz, Commands und Bieten-Logik (Escrow/Refund).
 */
public class AuctionManager {

    private final Main plugin;
    private final Messages msg;
    private final ChatPromptService prompts;

    private final Map<String, Auction> auctions = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingItems = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingCurrency = new ConcurrentHashMap<>();

    private File file;
    private YamlConfiguration data;

    public AuctionManager(Main plugin, ChatPromptService prompts) {
        this.plugin = plugin;
        this.msg = plugin.messages();
        this.prompts = prompts;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
        this.data = new YamlConfiguration();
    }

    /** Command-Executor für /auction */
    public CommandExecutor getCommandExecutor() {
        return new AuctionCommands();
    }

    /** Nur-Lesen-Zugriff auf aktuelle Auktionen (für GUIs etc.). */
    public Map<String, Auction> getAuctions() {
        return auctions;
    }

    /** Zugriff auf Messages (für GUIs/Prompts). */
    public Messages getMessages() {
        return msg;
    }

    /** Thread-safe Kopie aller Auktionen für sichere Iteration. */
    public Collection<Auction> allAuctions() {
        // Defensive copy für thread-safe Iteration
        return new ArrayList<>(auctions.values());
    }

    /* =================== Laden/Speichern =================== */

    public void loadAuctions() {
        auctions.clear();
        pendingItems.clear();
        pendingCurrency.clear();

        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            data = YamlConfiguration.loadConfiguration(file);

            if (data.isConfigurationSection("auctions")) {
                for (String id : data.getConfigurationSection("auctions").getKeys(false)) {
                    try {
                        String base = "auctions." + id + ".";

                        UUID owner;
                        try {
                            owner = UUID.fromString(data.getString(base + "owner"));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Ungültige UUID in Auktion '" + id + "': " + e.getMessage());
                            plugin.getLogger().warning("Auktion wird übersprungen.");
                            continue;
                        }

                        long start = data.getLong(base + "start");
                        long duration = data.getLong(base + "duration");
                        Material currency = Material.matchMaterial(data.getString(base + "currency", "DIAMOND"));
                        Auction a = new Auction(id, owner, start, duration, currency == null ? Material.DIAMOND : currency);

                        if (data.isConfigurationSection(base + "lots")) {
                            for (String lid : data.getConfigurationSection(base + "lots").getKeys(false)) {
                                String lb = base + "lots." + lid + ".";
                                ItemStack item = data.getItemStack(lb + "item");
                                int startBid = data.getInt(lb + "startBid");
                                AuctionLot lot = new AuctionLot(lid, item, startBid);

                                int hb = data.getInt(lb + "highestBid");
                                String hbId = data.getString(lb + "highestBidder", null);
                                if (hbId != null) {
                                    try {
                                        lot.applyBid(UUID.fromString(hbId), hb);
                                    } catch (IllegalArgumentException e) {
                                        plugin.getLogger().warning("Ungültige Bieter-UUID in Auktion '" + id + "', Lot '" + lid + "': " + e.getMessage());
                                        plugin.getLogger().warning("Gebot wird übersprungen.");
                                    }
                                }

                                a.lots().add(lot);
                            }
                        }
                        auctions.put(a.id(), a);
                    } catch (Exception e) {
                        plugin.getLogger().severe("Fehler beim Laden von Auktion '" + id + "': " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }

            if (data.isConfigurationSection("pendingItems")) {
                for (String uuid : data.getConfigurationSection("pendingItems").getKeys(false)) {
                    try {
                        List<ItemStack> items = new ArrayList<>();
                        for (Object o : data.getList("pendingItems." + uuid)) {
                            if (o instanceof ItemStack) items.add((ItemStack) o);
                        }
                        pendingItems.put(UUID.fromString(uuid), items);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Ungültige UUID in pendingItems '" + uuid + "': " + e.getMessage());
                        plugin.getLogger().warning("Eintrag wird übersprungen.");
                    }
                }
            }

            if (data.isConfigurationSection("pendingCurrency")) {
                for (String uuid : data.getConfigurationSection("pendingCurrency").getKeys(false)) {
                    try {
                        int val = data.getInt("pendingCurrency." + uuid);
                        pendingCurrency.put(UUID.fromString(uuid), val);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Ungültige UUID in pendingCurrency '" + uuid + "': " + e.getMessage());
                        plugin.getLogger().warning("Eintrag wird übersprungen.");
                    }
                }
            }

            // End-Termine replanen
            for (Auction a : auctions.values()) {
                scheduleEndTask(a);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Laden von auctions.yml: " + e.getMessage());
        }
    }

    public void saveAuctions() {
        try {
            data = new YamlConfiguration();

            for (Auction a : auctions.values()) {
                String base = "auctions." + a.id() + ".";
                data.set(base + "owner", a.owner().toString());
                data.set(base + "start", a.startMillis());
                data.set(base + "duration", a.durationMillis());
                data.set(base + "currency", a.currency().name());

                int i = 0;
                for (AuctionLot lot : a.lots()) {
                    String lb = base + "lots." + (i++) + ".";
                    data.set(lb + "item", lot.item());
                    data.set(lb + "startBid", lot.startBid());
                    data.set(lb + "highestBid", lot.highestBid());
                    if (lot.highestBidder() != null) {
                        data.set(lb + "highestBidder", lot.highestBidder().toString());
                    }
                }
            }

            for (Map.Entry<UUID, List<ItemStack>> e : pendingItems.entrySet()) {
                data.set("pendingItems." + e.getKey(), e.getValue());
            }
            for (Map.Entry<UUID, Integer> e : pendingCurrency.entrySet()) {
                data.set("pendingCurrency." + e.getKey(), e.getValue());
            }

            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Fehler beim Speichern von auctions.yml: " + e.getMessage());
        }
    }

    /* =================== Commands =================== */

    private class AuctionCommands implements CommandExecutor, TabCompleter {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players.");
                return true;
            }
            Player p = (Player) sender;
            String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "browse";
            switch (sub) {
                case "start":  return startAuctionFlow(p);
                case "browse": return browse(p);
                case "list":   return listActive(p);
                case "cancel": return cancelOwn(p);
                case "claim":  return claim(p);
                default:       return browse(p);
            }
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) return Arrays.asList("start","browse","list","cancel","claim");
            return Collections.emptyList();
        }
    }

    private boolean browse(Player p) {
        if (auctions.isEmpty()) {
            p.sendMessage(msg.prefixed("auction-browse-none"));
            return true;
        }
        AuctionGUI.openBrowse(p, new ArrayList<>(auctions.values()));
        return true;
    }

    private boolean listActive(Player p) {
        if (auctions.isEmpty()) {
            p.sendMessage(msg.prefixed("auction-browse-none"));
        } else {
            p.sendMessage("§7Aktive Auktionen: §f" + auctions.size());
            for (Auction a : auctions.values()) {
                long rem = Math.max(0, a.endMillis() - System.currentTimeMillis());
                p.sendMessage("§8- §7" + a.id() + " §7von §f" + a.owner() + " §7Ende in §f" + formatDuration(rem));
            }
        }
        return true;
    }

    private boolean cancelOwn(Player p) {
        List<String> canCancel = auctions.values().stream()
                .filter(a -> a.owner().equals(p.getUniqueId()))
                .filter(a -> a.lots().stream().allMatch(l -> l.highestBidder() == null))
                .map(Auction::id).collect(Collectors.toList());

        if (canCancel.isEmpty()) {
            p.sendMessage("§cDu hast keine stornierbaren Auktionen (oder bereits Gebote vorhanden).");
            return true;
        }

        for (String id : canCancel) {
            Auction a = auctions.remove(id);
            for (AuctionLot lot : a.lots()) {
                pendingItems.computeIfAbsent(a.owner(), k -> new ArrayList<>()).add(lot.item());
            }
        }
        saveAuctions();
        p.sendMessage("§aAuktion(en) storniert und Items zur Abholung bereit (/auction claim).");
        return true;
    }

    private boolean claim(Player p) {
        int cur = pendingCurrency.getOrDefault(p.getUniqueId(), 0);
        List<ItemStack> items = pendingItems.getOrDefault(p.getUniqueId(), new ArrayList<>());
        boolean any = false;

        if (cur > 0) {
            any = true;
            int rest = cur;
            while (rest > 0) {
                int give = Math.min(64, rest);
                InventoryUtils.giveOrDrop(p, new ItemStack(getCurrency(), give));
                rest -= give;
            }
            pendingCurrency.remove(p.getUniqueId());
        }

        if (!items.isEmpty()) {
            any = true;
            for (ItemStack is : items) {
                InventoryUtils.giveOrDrop(p, is);
            }
            pendingItems.remove(p.getUniqueId());
        }

        if (!any) p.sendMessage(msg.prefixed("claim-nothing"));
        else p.sendMessage(msg.prefixed("claim-done"));

        saveAuctions();
        return true;
    }

    private boolean startAuctionFlow(Player p) {
        AuctionGUI.openSetup(p);
        p.sendMessage(msg.prefixed("auction-setup-open"));
        return true;
    }

    /* =================== Auktionserstellung & Ende =================== */

    public void createAuctionFromSetup(Player owner, List<ItemStack> items) {
        if (items.isEmpty()) {
            owner.sendMessage(msg.prefixed("auction-setup-confirm-empty"));
            return;
        }

        Material currency = getCurrency();
        List<Integer> bids = new ArrayList<>();
        final int[] idx = {0};

        java.util.function.Consumer<Runnable> askNext = new java.util.function.Consumer<Runnable>() {
            @Override
            public void accept(Runnable finish) {
                if (idx[0] >= items.size()) {
                    finish.run();
                    return;
                }
                ItemStack it = items.get(idx[0]);
                String pretty = it.getItemMeta() != null && it.getItemMeta().hasDisplayName()
                        ? it.getItemMeta().getDisplayName()
                        : it.getType().name();

                prompts.ask(owner, msg.format("auction-start-prompt-bid", "item", pretty, "currency", currency.name()), (pl, input) -> {
                    int start;
                    try {
                        start = Integer.parseInt(input.trim());
                        if (start <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException ex) {
                        pl.sendMessage(msg.prefixedFormat("auction-start-prompt-bid", "item", pretty, "currency", currency.name()));
                        accept(finish);
                        return;
                    }
                    bids.add(start);
                    idx[0]++;
                    accept(finish);
                });
            }
        };

        askNext.accept(() -> {
            prompts.ask(owner, msg.raw("auction-start-prompt-duration"), (pl, input) -> {
                long duration = parseDurationMillis(input.trim());
                long min = plugin.getConfig().getInt("auctions.min-duration-minutes", 10) * 60L * 1000L;
                long max = plugin.getConfig().getInt("auctions.max-duration-hours", 72) * 60L * 60L * 1000L;
                if (duration < min) duration = min;
                if (duration > max) duration = max;

                String id = randomId();
                Auction a = new Auction(id, owner.getUniqueId(), System.currentTimeMillis(), duration, currency);
                for (int i = 0; i < items.size(); i++) {
                    ItemStack it = items.get(i).clone();
                    it.setAmount(1);
                    a.lots().add(new AuctionLot(String.valueOf(i), it, bids.get(i)));
                }
                auctions.put(a.id(), a);
                scheduleEndTask(a);
                saveAuctions();

                owner.sendMessage(msg.prefixedFormat("auction-started",
                        "lots", String.valueOf(a.lots().size()),
                        "duration", formatDuration(duration)));
            });
        });
    }

    private void scheduleEndTask(Auction a) {
        long delayTicks = Math.max(1L, (a.endMillis() - System.currentTimeMillis()) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> endAuction(a.id()), delayTicks);
    }

    private void endAuction(String id) {
        Auction a = auctions.remove(id);
        if (a == null) return;

        for (AuctionLot lot : a.lots()) {
            if (lot.highestBidder() != null) {
                pendingItems.computeIfAbsent(lot.highestBidder(), k -> new ArrayList<>()).add(lot.item());
                pendingCurrency.merge(a.owner(), lot.highestBid(), Integer::sum);

                Player win = Bukkit.getPlayer(lot.highestBidder());
                if (win != null) win.sendMessage(msg.prefixedFormat("auction-ended-winner", "item", pretty(lot.item()), "amount", String.valueOf(lot.highestBid())));
                Player seller = Bukkit.getPlayer(a.owner());
                if (seller != null) seller.sendMessage(msg.prefixedFormat("auction-ended-seller", "item", pretty(lot.item()), "amount", String.valueOf(lot.highestBid())));
            } else {
                Player seller = Bukkit.getPlayer(a.owner());
                if (seller != null) seller.sendMessage(msg.prefixedFormat("auction-ended-no-bids", "item", pretty(lot.item())));
                pendingItems.computeIfAbsent(a.owner(), k -> new ArrayList<>()).add(lot.item());
            }
        }
        saveAuctions();
    }

    /* =================== Bieten (Escrow/Refund) =================== */

    public boolean tryBid(Player bidder, Auction a, AuctionLot lot, int amount) {
        int min = (lot.highestBidder() == null) ? lot.startBid() : (lot.highestBid() + 1);
        if (amount < min) {
            bidder.sendMessage(msg.prefixedFormat("auction-bid-too-low", "min", String.valueOf(min)));
            return false;
        }

        int removed = InventoryUtils.removeMaterial(bidder.getInventory(), a.currency(), amount);
        if (removed != amount) {
            bidder.sendMessage(msg.prefixedFormat("buy-insufficient-funds", "currency", a.currency().name()));
            if (removed > 0) bidder.getInventory().addItem(new ItemStack(a.currency(), removed));
            return false;
        }

        if (lot.highestBidder() != null) {
            pendingCurrency.merge(lot.highestBidder(), lot.highestBid(), Integer::sum);
            Player prev = Bukkit.getPlayer(lot.highestBidder());
            if (prev != null) prev.sendMessage(msg.prefixedFormat("auction-refund", "amount", String.valueOf(lot.highestBid())));
        }

        lot.applyBid(bidder.getUniqueId(), amount);
        bidder.sendMessage(msg.prefixedFormat("auction-bid-ok", "amount", String.valueOf(amount), "currency", a.currency().name()));
        saveAuctions();
        return true;
    }

    public Material getCurrency() {
        String c = plugin.getConfig().getString("currency-material", "DIAMOND");
        Material mat = Material.matchMaterial(c);
        return mat == null ? Material.DIAMOND : mat;
    }

    /* =================== Utilities =================== */

    private String randomId() {
        return Long.toString(ThreadLocalRandom.current().nextLong(36_000_000L, 99_000_000L), 36);
    }

    private long parseDurationMillis(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        long total = 0L;
        String num = "";
        for (char ch : s.toCharArray()) {
            if (Character.isDigit(ch)) {
                num += ch;
            } else {
                long n = num.isEmpty() ? 0 : Long.parseLong(num);
                if (ch == 's') total += n * 1000L;
                else if (ch == 'm') total += n * 60_000L;
                else if (ch == 'h') total += n * 3_600_000L;
                else if (ch == 'd') total += n * 86_400_000L;
                num = "";
            }
        }
        if (!num.isEmpty()) total += Long.parseLong(num) * 1000L;
        return total <= 0 ? 30 * 60_000L : total;
    }

    public String formatDuration(long millis) {
        long seconds = millis / 1000L;
        long d = seconds / 86400; seconds %= 86400;
        long h = seconds / 3600;  seconds %= 3600;
        long m = seconds / 60;    seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (seconds > 0 && sb.length() == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    private String pretty(ItemStack is) {
        if (is.getItemMeta() != null && is.getItemMeta().hasDisplayName()) return is.getItemMeta().getDisplayName();
        return is.getType().name();
    }

    /**
     * Gibt die Map mit nicht abgeholten Items zurück.
     * Wird vom AuctionReminderService verwendet.
     *
     * @return Map von UUID zu Liste von ItemStacks
     */
    public Map<UUID, List<ItemStack>> getPendingItems() {
        return pendingItems;
    }

    /**
     * Gibt die Map mit nicht abgeholter Währung zurück.
     * Wird vom AuctionReminderService verwendet.
     *
     * @return Map von UUID zu Integer (Währungsmenge)
     */
    public Map<UUID, Integer> getPendingCurrency() {
        return pendingCurrency;
    }
}
