package de.mcbn.shops.api;

import de.mcbn.shops.Main;
import de.mcbn.shops.api.event.ShopCreatedEvent;
import de.mcbn.shops.api.event.ShopRemovedEvent;
import de.mcbn.shops.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main API for MCBN-Shop plugin.
 * This class provides methods for external plugins to interact with the shop system.
 *
 * Usage example:
 * <pre>
 * Plugin mcbnShop = Bukkit.getPluginManager().getPlugin("MCBN-Shop");
 * if (mcbnShop != null && mcbnShop instanceof Main) {
 *     ShopAPI api = ((Main) mcbnShop).getShopAPI();
 *     // Use API methods...
 * }
 * </pre>
 */
public class ShopAPI {
    private final Main plugin;

    public ShopAPI(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets a shop at a specific block location
     *
     * @param block the block to check
     * @return Optional containing the shop if one exists at that location
     */
    public Optional<Shop> getShop(Block block) {
        return plugin.shops().get(block);
    }

    /**
     * Gets a shop at a specific location
     *
     * @param location the location to check
     * @return Optional containing the shop if one exists at that location
     */
    public Optional<Shop> getShop(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return getShop(location.getBlock());
    }

    /**
     * Checks if a block is a shop
     *
     * @param block the block to check
     * @return true if the block is a shop
     */
    public boolean isShop(Block block) {
        return plugin.shops().isShop(block);
    }

    /**
     * Gets all shops in the system
     *
     * @return collection of all shops
     */
    public Collection<Shop> getAllShops() {
        return plugin.shops().all();
    }

    /**
     * Gets all shops owned by a specific player
     *
     * @param owner the UUID of the shop owner
     * @return list of shops owned by the player
     */
    public List<Shop> getShopsByOwner(UUID owner) {
        return plugin.shops().all().stream()
                .filter(shop -> shop.owner().equals(owner))
                .collect(Collectors.toList());
    }

    /**
     * Gets all shops owned by a specific player
     *
     * @param owner the shop owner
     * @return list of shops owned by the player
     */
    public List<Shop> getShopsByOwner(Player owner) {
        return getShopsByOwner(owner.getUniqueId());
    }

    /**
     * Gets all shops selling a specific material
     *
     * @param material the material to search for
     * @return list of shops selling that material
     */
    public List<Shop> getShopsByMaterial(Material material) {
        return plugin.shops().all().stream()
                .filter(shop -> shop.template().getType() == material)
                .collect(Collectors.toList());
    }

    /**
     * Creates a new shop programmatically.
     * This will fire a ShopCreatedEvent which can be cancelled.
     *
     * @param owner the UUID of the shop owner
     * @param block the block where the shop should be created
     * @param template the item template to sell
     * @param bundleAmount how many items per bundle
     * @param price the price per bundle
     * @return true if the shop was created successfully
     */
    public boolean createShop(UUID owner, Block block, ItemStack template, int bundleAmount, int price) {
        Player player = Bukkit.getPlayer(owner);
        if (player == null) {
            plugin.getLogger().warning("Cannot create shop: Player " + owner + " is not online");
            return false;
        }
        return createShop(player, block, template, bundleAmount, price);
    }

    /**
     * Creates a new shop programmatically.
     * This will fire a ShopCreatedEvent which can be cancelled.
     *
     * @param owner the shop owner
     * @param block the block where the shop should be created
     * @param template the item template to sell
     * @param bundleAmount how many items per bundle
     * @param price the price per bundle
     * @return true if the shop was created successfully
     */
    public boolean createShop(Player owner, Block block, ItemStack template, int bundleAmount, int price) {
        if (owner == null || block == null || template == null) {
            plugin.getLogger().warning("Cannot create shop: Invalid parameters");
            return false;
        }

        if (isShop(block)) {
            plugin.getLogger().warning("Cannot create shop: Block already has a shop");
            return false;
        }

        // Create shop instance for the event
        Material currency = Material.matchMaterial(plugin.getConfig().getString("currency-material", "DIAMOND"));
        Shop shop = new Shop(
            owner.getUniqueId(),
            block.getLocation(),
            template,
            bundleAmount,
            price,
            currency == null ? Material.DIAMOND : currency,
            owner.getFacing().getOppositeFace()
        );

        // Fire event
        ShopCreatedEvent event = new ShopCreatedEvent(owner, shop);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            plugin.getLogger().info("Shop creation cancelled by event");
            return false;
        }

        // Actually create the shop
        plugin.shops().createShop(owner.getUniqueId(), block, template, bundleAmount, price);
        return true;
    }

    /**
     * Removes a shop at a specific location.
     * This will fire a ShopRemovedEvent.
     *
     * @param block the block where the shop is located
     * @return true if a shop was removed
     */
    public boolean removeShop(Block block) {
        return removeShop(null, block);
    }

    /**
     * Removes a shop at a specific location.
     * This will fire a ShopRemovedEvent.
     *
     * @param remover the player removing the shop (can be null)
     * @param block the block where the shop is located
     * @return true if a shop was removed
     */
    public boolean removeShop(Player remover, Block block) {
        Optional<Shop> shopOpt = getShop(block);
        if (!shopOpt.isPresent()) {
            return false;
        }

        Shop shop = shopOpt.get();

        // Fire event
        ShopRemovedEvent event = new ShopRemovedEvent(remover, shop);
        Bukkit.getPluginManager().callEvent(event);

        // Remove the shop
        plugin.shops().removeShop(block);
        return true;
    }

    /**
     * Gets the shop owner's UUID from a shop block
     *
     * @param block the shop block
     * @return Optional containing the owner UUID if the block is a shop
     */
    public Optional<UUID> getShopOwner(Block block) {
        return getShop(block).map(Shop::owner);
    }

    /**
     * Gets the price of a shop
     *
     * @param block the shop block
     * @return Optional containing the price if the block is a shop
     */
    public Optional<Integer> getShopPrice(Block block) {
        return getShop(block).map(Shop::price);
    }

    /**
     * Gets the bundle amount of a shop
     *
     * @param block the shop block
     * @return Optional containing the bundle amount if the block is a shop
     */
    public Optional<Integer> getShopBundleAmount(Block block) {
        return getShop(block).map(Shop::bundleAmount);
    }

    /**
     * Gets the currency material used by a shop
     *
     * @param block the shop block
     * @return Optional containing the currency material if the block is a shop
     */
    public Optional<Material> getShopCurrency(Block block) {
        return getShop(block).map(Shop::currency);
    }

    /**
     * Gets the item template being sold by a shop
     *
     * @param block the shop block
     * @return Optional containing the item template if the block is a shop
     */
    public Optional<ItemStack> getShopTemplate(Block block) {
        return getShop(block).map(Shop::template);
    }

    /**
     * Gets the MCBN-Shop plugin instance
     *
     * @return the plugin instance
     */
    public Main getPlugin() {
        return plugin;
    }

    /**
     * Static method to get the ShopAPI instance from any plugin
     *
     * @return Optional containing the ShopAPI if MCBN-Shop is loaded
     */
    public static Optional<ShopAPI> getInstance() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MCBN-Shop");
        if (plugin != null && plugin instanceof Main) {
            return Optional.of(((Main) plugin).getShopAPI());
        }
        return Optional.empty();
    }
}
