package de.mcbn.shops.order;

import de.mcbn.shops.Main;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Verwaltung von Einkaufslisten und Buch-Parsing. */
public class OrderManager {

    private final Main plugin;
    private final Map<UUID, PurchaseOrder> orders = new HashMap<>();
    private File file;
    private YamlConfiguration data;

    public OrderManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "orders.yml");
        this.data = new YamlConfiguration();
    }

    public void load() {
        orders.clear();
        try {
            if (!file.exists()) { file.getParentFile().mkdirs(); file.createNewFile(); }
            data = YamlConfiguration.loadConfiguration(file);
            if (!data.isConfigurationSection("orders")) return;
            for (String id : data.getConfigurationSection("orders").getKeys(false)) {
                String base = "orders." + id + ".";
                UUID pid = UUID.fromString(id);
                UUID owner = UUID.fromString(data.getString(base + "owner"));
                int fee = data.getInt(base + "fee", plugin.getConfig().getInt("shopkeepers.shopper-fee-percent", 5));
                Map<Material,Integer> wanted = new HashMap<>();
                Map<Material,Integer> max = new HashMap<>();
                if (data.isConfigurationSection(base + "wanted")) {
                    for (String m : data.getConfigurationSection(base + "wanted").getKeys(false)) {
                        Material mat = Material.matchMaterial(m);
                        int amt = data.getInt(base + "wanted." + m, 0);
                        if (mat != null && amt > 0) wanted.put(mat, amt);
                        int maxPer = data.getInt(base + "max." + m, 0);
                        if (mat != null && maxPer > 0) max.put(mat, maxPer);
                    }
                }
                orders.put(pid, new PurchaseOrder(pid, owner, wanted, max, fee));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Laden von orders.yml: " + e.getMessage());
        }
    }

    public void save() {
        try {
            data = new YamlConfiguration();
            for (PurchaseOrder o : orders.values()) {
                String base = "orders." + o.id().toString() + ".";
                data.set(base + "owner", o.owner().toString());
                data.set(base + "fee", o.feePercent());
                for (Map.Entry<Material,Integer> e : o.wanted().entrySet()) {
                    data.set(base + "wanted." + e.getKey().name(), e.getValue());
                }
                for (Map.Entry<Material,Integer> e : o.maxPrice().entrySet()) {
                    data.set(base + "max." + e.getKey().name(), e.getValue());
                }
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Fehler beim Speichern von orders.yml: " + e.getMessage());
        }
    }

    public PurchaseOrder create(UUID owner) {
        UUID id = java.util.UUID.randomUUID();
        return new PurchaseOrder(id, owner, new HashMap<>(), new HashMap<>(),
                plugin.getConfig().getInt("shopkeepers.shopper-fee-percent", 5));
    }

    public Optional<PurchaseOrder> byOwner(UUID owner) {
        return orders.values().stream().filter(o -> o.owner().equals(owner)).findFirst();
    }

    public void put(PurchaseOrder o) {
        orders.put(o.id(), o);
    }

    /** Liest eine Bestellung aus einem Buch (WRITABLE_BOOK/WRITTEN_BOOK).
     *  Erlaubte Zeilen pro Order-Eintrag (ohne Farbe):
     *   - 64x STRING
     *   - 64x STRING max 2
     *   - STRING 64
     */
    public PurchaseOrder parseFromBook(UUID owner, ItemStack bookStack) {
        if (bookStack == null) return create(owner);
        if (!(bookStack.getItemMeta() instanceof BookMeta)) return create(owner);
        BookMeta bm = (BookMeta) bookStack.getItemMeta();
        PurchaseOrder o = create(owner);
        for (String page : bm.getPages()) {
            String[] lines = page.split("\\r?\\n");
            for (String line : lines) {
                String s = line.trim();
                if (s.isEmpty()) continue;
                s = s.replace("§", "");
                // Patterns: "64x STRING [max 2]"  or  "STRING 64 [max 2]"
                int amount = 0;
                String matToken = null;
                Integer max = 0;
                try {
                    if (s.matches("(?i)\\d+\\s*[xX]\\s+[A-Z0-9_]+(\\s+max\\s+\\d+)?")) {
                        String[] parts = s.split("\\s+");
                        amount = Integer.parseInt(parts[0].toLowerCase().replace("x","").replace("X",""));
                        matToken = parts[2];
                        if (s.toLowerCase().contains("max")) {
                            String[] ps = s.toLowerCase().split("max");
                            String tail = ps[1].trim();
                            String[] nums = tail.split("\\s+");
                            max = Integer.parseInt(nums[0]);
                        }
                    } else if (s.matches("(?i)[A-Z0-9_]+\\s+\\d+(\\s+max\\s+\\d+)?")) {
                        String[] parts = s.split("\\s+");
                        matToken = parts[0];
                        amount = Integer.parseInt(parts[1]);
                        if (s.toLowerCase().contains("max")) {
                            String[] ps = s.toLowerCase().split("max");
                            String tail = ps[1].trim();
                            String[] nums = tail.split("\\s+");
                            max = Integer.parseInt(nums[0]);
                        }
                    } else {
                        continue;
                    }
                    Material m = Material.matchMaterial(matToken);
                    if (m != null && amount > 0) {
                        o.put(m, amount, max == null ? 0 : max);
                    }
                } catch (Exception ignored) {}
            }
        }
        put(o);
        save();
        return o;
    }

    /** Erstellt den Buch-Inhalt (Seiten) aus einer Order. */
    public java.util.List<String> toBookPages(PurchaseOrder o) {
        java.util.List<String> pages = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("Einkaufsliste\n-------------\n");
        for (Map.Entry<Material,Integer> e : o.wanted().entrySet()) {
            int max = o.maxPrice().getOrDefault(e.getKey(), 0);
            if (max > 0) sb.append(e.getValue()).append("x ").append(e.getKey().name()).append(" max ").append(max).append("\n");
            else sb.append(e.getValue()).append("x ").append(e.getKey().name()).append("\n");
        }
        pages.add(sb.toString());
        return pages;
    }
}
