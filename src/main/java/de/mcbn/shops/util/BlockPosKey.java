package de.mcbn.shops.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public final class BlockPosKey {
    public final String world;
    public final int x, y, z;

    public BlockPosKey(Location loc) {
        this.world = Objects.requireNonNull(loc.getWorld()).getName();
        this.x = loc.getBlockX();
        this.y = loc.getBlockY();
        this.z = loc.getBlockZ();
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return new Location(w, x, y, z);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockPosKey)) return false;
        BlockPosKey that = (BlockPosKey) o;
        return x == that.x && y == that.y && z == that.z && world.equals(that.world);
    }

    @Override public int hashCode() {
        return Objects.hash(world, x, y, z);
    }

    @Override public String toString() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public static BlockPosKey fromString(String s) {
        String[] p = s.split(";");
        return new BlockPosKey(new Location(Bukkit.getWorld(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
    }
}
