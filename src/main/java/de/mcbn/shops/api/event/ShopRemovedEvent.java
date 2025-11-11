package de.mcbn.shops.api.event;

import de.mcbn.shops.shop.Shop;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event that is fired when a shop is removed.
 * This event is not cancellable.
 */
public class ShopRemovedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player remover;
    private final Shop shop;

    public ShopRemovedEvent(Player remover, Shop shop) {
        this.remover = remover;
        this.shop = shop;
    }

    /**
     * Gets the player who removed the shop
     * May be null if removed programmatically
     * @return the remover or null
     */
    public Player getRemover() {
        return remover;
    }

    /**
     * Gets the shop that was removed
     * @return the shop
     */
    public Shop getShop() {
        return shop;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
