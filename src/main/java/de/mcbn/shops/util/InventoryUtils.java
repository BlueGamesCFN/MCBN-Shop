package de.mcbn.shops.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryUtils {

    private InventoryUtils(){}

    public static int countSimilar(Inventory inv, ItemStack template) {
        if (template == null) return 0;
        int count = 0;
        for (ItemStack is : inv.getContents()) {
            if (is != null && is.isSimilar(template)) {
                count += is.getAmount();
            }
        }
        return count;
    }

    public static int removeSimilar(Inventory inv, ItemStack template, int amount) {
        int toRemove = amount;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack is = inv.getItem(i);
            if (is == null) continue;
            if (is.isSimilar(template)) {
                int take = Math.min(is.getAmount(), toRemove);
                is.setAmount(is.getAmount() - take);
                if (is.getAmount() <= 0) inv.setItem(i, null);
                toRemove -= take;
                if (toRemove <= 0) break;
            }
        }
        return amount - toRemove;
    }

    public static int removeMaterial(Inventory inv, Material mat, int amount) {
        int toRemove = amount;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack is = inv.getItem(i);
            if (is == null) continue;
            if (is.getType() == mat) {
                int take = Math.min(is.getAmount(), toRemove);
                is.setAmount(is.getAmount() - take);
                if (is.getAmount() <= 0) inv.setItem(i, null);
                toRemove -= take;
                if (toRemove <= 0) break;
            }
        }
        return amount - toRemove;
    }

    public static void giveOrDrop(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) return;
        java.util.HashMap<Integer, ItemStack> rem = player.getInventory().addItem(stack);
        if (!rem.isEmpty()) {
            for (ItemStack leftover : rem.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    public static void addOrDropToInventory(Inventory inv, ItemStack stack, Location dropLoc) {
        java.util.HashMap<Integer, ItemStack> rem = inv.addItem(stack);
        if (!rem.isEmpty()) {
            for (ItemStack leftover : rem.values()) {
                dropLoc.getWorld().dropItemNaturally(dropLoc, leftover);
            }
        }
    }
}
