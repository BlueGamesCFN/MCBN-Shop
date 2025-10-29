package de.mcbn.shops.auction;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Auction {
    private final String id;
    private final UUID owner;
    private final List<AuctionLot> lots = new ArrayList<>();
    private final long startMillis;
    private final long durationMillis;
    private final Material currency;

    public Auction(String id, UUID owner, long startMillis, long durationMillis, Material currency) {
        this.id = id;
        this.owner = owner;
        this.startMillis = startMillis;
        this.durationMillis = durationMillis;
        this.currency = currency;
    }

    public String id() { return id; }
    public UUID owner() { return owner; }
    public List<AuctionLot> lots() { return lots; }
    public long startMillis() { return startMillis; }
    public long durationMillis() { return durationMillis; }
    public long endMillis() { return startMillis + durationMillis; }
    public Material currency() { return currency; }

    public boolean isActive() {
        return System.currentTimeMillis() < endMillis();
    }
}
