package de.mcbn.shops.keeper;

import de.mcbn.shops.Main;
import de.mcbn.shops.util.BlockPosKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Verwaltung der Shopkeeper (Villager) inkl. Persistenz. */
public class KeeperManager {

    private final Main plugin;
    private final Map<UUID, ShopKeeper> keepers = new ConcurrentHashMap<>();
    private File file;
    private YamlConfiguration data;

    public KeeperManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "keepers.yml");
        this.data = new YamlConfiguration();
    }

    public Collection<ShopKeeper> all() {
        // Defensive copy für thread-safe Iteration
        return new ArrayList<>(keepers.values());
    }
    public ShopKeeper get(UUID uuid) { return keepers.get(uuid); }

    public void load() {
        keepers.clear();
        try {
            if (!file.exists()) { file.getParentFile().mkdirs(); file.createNewFile(); }
            data = YamlConfiguration.loadConfiguration(file);

            if (data.isConfigurationSection("keepers")) {
                for (String id : data.getConfigurationSection("keepers").getKeys(false)) {
                    try {
                        String base = "keepers." + id + ".";

                        UUID uuid;
                        try {
                            uuid = UUID.fromString(id);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Ungültige UUID für Keeper '" + id + "': " + e.getMessage());
                            plugin.getLogger().warning("Keeper wird übersprungen.");
                            continue;
                        }

                        UUID owner;
                        try {
                            owner = UUID.fromString(data.getString(base + "owner"));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Ungültige Owner-UUID in Keeper '" + id + "': " + e.getMessage());
                            plugin.getLogger().warning("Keeper wird übersprungen.");
                            continue;
                        }

                        String world = data.getString(base + "world");
                        int x = data.getInt(base + "x");
                        int y = data.getInt(base + "y");
                        int z = data.getInt(base + "z");
                        ShopKeeper k = new ShopKeeper(uuid, owner, world, x, y, z);
                        List<String> linked = data.getStringList(base + "linked");
                        for (String s : linked) k.add(BlockPosKey.fromString(s));
                        keepers.put(uuid, k);
                    } catch (Exception e) {
                        plugin.getLogger().severe("Fehler beim Laden von Keeper '" + id + "': " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Laden von keepers.yml: " + e.getMessage());
        }
    }

    public void save() {
        try {
            data = new YamlConfiguration();
            for (ShopKeeper k : keepers.values()) {
                String base = "keepers." + k.uuid().toString() + ".";
                data.set(base + "owner", k.owner().toString());
                data.set(base + "world", k.world());
                data.set(base + "x", k.x());
                data.set(base + "y", k.y());
                data.set(base + "z", k.z());
                List<String> linked = new ArrayList<>();
                for (BlockPosKey key : k.linked()) linked.add(key.toString());
                data.set(base + "linked", linked);
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Fehler beim Speichern von keepers.yml: " + e.getMessage());
        }
    }

    public ShopKeeper create(Location loc, UUID owner) {
        if (!plugin.getConfig().getBoolean("shopkeepers.enabled", true)) return null;
        Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        v.setAI(false);
        v.setInvulnerable(true);
        v.setCollidable(false);
        v.setGravity(false);
        v.setAdult();
        v.setSilent(true);
        v.setProfession(Villager.Profession.NONE);
        ShopKeeper k = new ShopKeeper(v.getUniqueId(), owner, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        keepers.put(v.getUniqueId(), k);
        save();
        return k;
    }

    public boolean remove(UUID uuid) {
        ShopKeeper k = keepers.remove(uuid);
        if (k == null) return false;
        Entity e = findEntity(uuid);
        if (e != null) e.remove();
        save();
        return true;
    }

    public void link(UUID keeper, BlockPosKey pos) {
        ShopKeeper k = keepers.get(keeper);
        if (k == null) return;
        k.add(pos);
        save();
    }

    public void unlink(UUID keeper, BlockPosKey pos) {
        ShopKeeper k = keepers.get(keeper);
        if (k == null) return;
        k.remove(pos);
        save();
    }

    public Entity findEntity(UUID id) {
        for (World w : Bukkit.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }
}
