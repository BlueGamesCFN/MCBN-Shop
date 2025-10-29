package de.mcbn.shops.order;

import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Einkaufsliste eines Spielers (serverweit), inkl. optionalem Maximalpreis pro Item (pro Stück). */
public class PurchaseOrder {
    private final UUID id;
    private final UUID owner;
    private final Map<Material, Integer> wanted;     // gewünschte Menge (Stück)
    private final Map<Material, Integer> maxPrice;   // Maximalpreis pro Stück (Währung-Items), 0 = egal
    private final int feePercent;

    public PurchaseOrder(UUID id, UUID owner, Map<Material, Integer> wanted, Map<Material, Integer> maxPrice, int feePercent) {
        this.id = id; this.owner = owner; this.wanted = wanted; this.maxPrice = maxPrice; this.feePercent = feePercent;
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public Map<Material, Integer> wanted() { return Collections.unmodifiableMap(wanted); }
    public Map<Material, Integer> maxPrice() { return Collections.unmodifiableMap(maxPrice); }
    public int feePercent() { return feePercent; }

    public void put(Material m, int amount, int maxPerItem) {
        wanted.put(m, amount);
        if (maxPerItem > 0) maxPrice.put(m, maxPerItem);
        else maxPrice.remove(m);
    }
    public void clear() { wanted.clear(); maxPrice.clear(); }

    // helper for internal mutation
    public void _setRemaining(Material m, int amount) { wanted.put(m, amount); }
}
