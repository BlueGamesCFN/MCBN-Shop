package de.mcbn.shops.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionLot {
    private final String id;
    private final ItemStack item;
    private final int startBid;
    private int highestBid;
    private UUID highestBidder; // null if none

    public AuctionLot(String id, ItemStack item, int startBid) {
        this.id = id;
        this.item = item.clone();
        this.item.setAmount(1);
        this.startBid = startBid;
        this.highestBid = 0;
        this.highestBidder = null;
    }

    public String id() { return id; }
    public ItemStack item() { return item.clone(); }
    public int startBid() { return startBid; }
    public int highestBid() { return highestBidder == null ? 0 : highestBid; }
    public UUID highestBidder() { return highestBidder; }

    public int currentPrice() { return highestBidder == null ? startBid : highestBid; }

    public void applyBid(UUID bidder, int amount) {
        this.highestBidder = bidder;
        this.highestBid = amount;
    }
}
