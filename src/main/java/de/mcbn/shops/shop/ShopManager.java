package de.mcbn.shops.shop;

import de.mcbn.shops.Main;
import de.mcbn.shops.util.BlockPosKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ShopManager {
    private final Main plugin;
    private final Map<BlockPosKey, Shop> shops = new ConcurrentHashMap<>();
    private File file;
    private YamlConfiguration data;

    public ShopManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shops.yml");
        this.data = new YamlConfiguration();
    }

    public Optional<Shop> get(Block block) {
        return block == null ? Optional.empty() : Optional.ofNullable(shops.get(new BlockPosKey(block.getLocation())));
    }

    public boolean isShop(Block block) {
        return get(block).isPresent();
    }

    public Collection<Shop> all() {
        // Defensive copy für thread-safe Iteration
        return new ArrayList<>(shops.values());
    }

    public void createShop(UUID owner, Block block, ItemStack template, int bundleAmount, int price) {
        Material currency = Material.matchMaterial(plugin.getConfig().getString("currency-material", "DIAMOND"));
        BlockFace face = plugin.getServer().getPlayer(owner).getFacing().getOppositeFace();
        Shop s = new Shop(owner, block.getLocation(), template, bundleAmount, price,
                currency == null ? Material.DIAMOND : currency, face);
        shops.put(new BlockPosKey(block.getLocation()), s);
        createSign(s);
        saveShops();
    }

    public void removeShop(Block block) {
        BlockPosKey key = new BlockPosKey(block.getLocation());
        Shop s = shops.remove(key);
        if (s != null) {
            removeSign(s);
        }
        saveShops();
    }

    public void loadShops() {
        shops.clear();
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            data = YamlConfiguration.loadConfiguration(file);
            if (!data.isConfigurationSection("shops")) return;

            for (String key : data.getConfigurationSection("shops").getKeys(false)) {
                String base = "shops." + key + ".";
                UUID owner = UUID.fromString(data.getString(base + "owner"));
                ItemStack template = data.getItemStack(base + "template");
                int bundle = data.getInt(base + "bundle");
                int price = data.getInt(base + "price");
                String world = data.getString(base + "world");
                int x = data.getInt(base + "x");
                int y = data.getInt(base + "y");
                int z = data.getInt(base + "z");
                Material currency = Material.matchMaterial(data.getString(base + "currency", "DIAMOND"));
                BlockFace face = BlockFace.valueOf(data.getString(base + "signFace", "NORTH"));
                Location loc = new Location(Bukkit.getWorld(world), x, y, z);

                Shop s = new Shop(owner, loc, template, bundle, price,
                        currency == null ? Material.DIAMOND : currency, face);
                shops.put(new BlockPosKey(loc), s);
                createSign(s);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Laden von shops.yml: " + e.getMessage());
        }
    }

    public void saveShops() {
        try {
            data = new YamlConfiguration();
            int i = 0;
            for (Shop s : shops.values()) {
                String base = "shops." + (i++) + ".";
                data.set(base + "owner", s.owner().toString());
                data.set(base + "template", s.template());
                data.set(base + "bundle", s.bundleAmount());
                data.set(base + "price", s.price());
                data.set(base + "currency", s.currency().name());
                data.set(base + "world", s.pos().world);
                data.set(base + "x", s.pos().x);
                data.set(base + "y", s.pos().y);
                data.set(base + "z", s.pos().z);
                data.set(base + "signFace", s.signFace().name());
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Fehler beim Speichern von shops.yml: " + e.getMessage());
        }
    }

    public void createSign(Shop shop) {
        Block block = shop.pos().toLocation().getBlock();
        Block signBlock = block.getRelative(shop.signFace());
        if (!signBlock.getType().isAir()) return;
        signBlock.setType(Material.OAK_WALL_SIGN);
        if (!(signBlock.getState() instanceof Sign)) return;
        Sign sign = (Sign) signBlock.getState();
        WallSign wall = (WallSign) sign.getBlockData();
        wall.setFacing(shop.signFace());
        sign.setBlockData(wall);

        sign.setLine(0, "§6Shop");
        sign.setLine(1, prettyItem(shop.template()));
        sign.setLine(2, shop.bundleAmount() + "x");
        sign.setLine(3, shop.price() + " " + shop.currency().name());
        sign.update(true);
    }

    public void removeSign(Shop shop) {
        Block block = shop.pos().toLocation().getBlock();
        Block signBlock = block.getRelative(shop.signFace());
        if (signBlock.getState() instanceof Sign) {
            signBlock.setType(Material.AIR);
        }
    }

    private String prettyItem(ItemStack is) {
        if (is.getItemMeta() != null && is.getItemMeta().hasDisplayName()) {
            return is.getItemMeta().getDisplayName();
        }
        String n = is.getType().name().toLowerCase(Locale.ROOT).replace('_',' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public static Optional<org.bukkit.inventory.Inventory> getContainerInventory(Block block) {
        if (block == null) return Optional.empty();
        org.bukkit.block.BlockState state = block.getState();
        if (!(state instanceof org.bukkit.block.Container)) return Optional.empty();
        return Optional.ofNullable(((org.bukkit.block.Container) state).getInventory());
    }
}
