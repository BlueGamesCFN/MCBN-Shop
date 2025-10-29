package de.mcbn.shops.keeper;

import de.mcbn.shops.util.BlockPosKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Repräsentiert einen Shopkeeper (Villager), der mehrere Behälter-Shops bedient. */
public class ShopKeeper {
    private final UUID uuid;     // Entity UUID
    private final UUID owner;    // Besitzer
    private final String world;
    private final int x, y, z;   // Spawnposition (für Teleport/Restore)
    private final List<BlockPosKey> linked = new ArrayList<>();

    public ShopKeeper(UUID uuid, UUID owner, String world, int x, int y, int z) {
        this.uuid = uuid; this.owner = owner; this.world = world; this.x = x; this.y = y; this.z = z;
    }

    public UUID uuid() { return uuid; }
    public UUID owner() { return owner; }
    public String world() { return world; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    public List<BlockPosKey> linked() { return Collections.unmodifiableList(linked); }
    public void add(BlockPosKey key) { if (!linked.contains(key)) linked.add(key); }
    public void remove(BlockPosKey key) { linked.remove(key); }
    public boolean isLinked(BlockPosKey key) { return linked.contains(key); }
}
