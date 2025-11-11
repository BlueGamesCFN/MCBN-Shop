package de.mcbn.shops.api.event;

import de.mcbn.shops.shop.Shop;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event that is fired when a shop is created.
 * Can be cancelled to prevent shop creation.
 */
public class ShopCreatedEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final Player creator;
    private final Shop shop;

    public ShopCreatedEvent(Player creator, Shop shop) {
        this.creator = creator;
        this.shop = shop;
    }

    /**
     * Gets the player who created the shop
     * @return the creator
     */
    public Player getCreator() {
        return creator;
    }

    /**
     * Gets the shop that was created
     * @return the shop
     */
    public Shop getShop() {
        return shop;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
