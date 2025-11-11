package de.mcbn.shops.api.event;

import de.mcbn.shops.shop.Shop;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event that is fired when a player purchases from a shop.
 * Can be cancelled to prevent the purchase.
 */
public class ShopPurchaseEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final Player buyer;
    private final Shop shop;
    private final int amount;

    public ShopPurchaseEvent(Player buyer, Shop shop, int amount) {
        this.buyer = buyer;
        this.shop = shop;
        this.amount = amount;
    }

    /**
     * Gets the player who is purchasing from the shop
     * @return the buyer
     */
    public Player getBuyer() {
        return buyer;
    }

    /**
     * Gets the shop being purchased from
     * @return the shop
     */
    public Shop getShop() {
        return shop;
    }

    /**
     * Gets the amount being purchased
     * @return the amount
     */
    public int getAmount() {
        return amount;
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
