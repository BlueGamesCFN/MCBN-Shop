package de.mcbn.shops.shop;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Shop {
    private final UUID owner;
    private final de.mcbn.shops.util.BlockPosKey pos;
    private final ItemStack template;
    private final int bundleAmount;
    private final int price;
    private final Material currency;
    private final BlockFace signFace;

    public Shop(UUID owner, Location loc, ItemStack template, int bundleAmount, int price, Material currency, BlockFace signFace) {
        this.owner = owner;
        this.pos = new de.mcbn.shops.util.BlockPosKey(loc);
        this.template = template;
        this.bundleAmount = bundleAmount;
        this.price = price;
        this.currency = currency;
        this.signFace = signFace;
    }

    public UUID owner() { return owner; }
    public de.mcbn.shops.util.BlockPosKey pos() { return pos; }
    public ItemStack template() { return template; }
    public int bundleAmount() { return bundleAmount; }
    public int price() { return price; }
    public Material currency() { return currency; }
    public BlockFace signFace() { return signFace; }
}
