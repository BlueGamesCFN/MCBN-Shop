package de.mcbn.shops.util;

import de.mcbn.shops.Main;
import org.bukkit.Bukkit;

public class Scheduler {
    private final Main plugin;
    private int autosaveTaskId = -1;

    public Scheduler(Main plugin) {
        this.plugin = plugin;
    }

    public void startAutosave() {
        int minutes = plugin.getConfig().getInt("storage.autosave-minutes", 10);
        long ticks = minutes * 60L * 20L;
        if (autosaveTaskId != -1) Bukkit.getScheduler().cancelTask(autosaveTaskId);
        autosaveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            try {
                plugin.auctions().saveAuctions();
                plugin.shops().saveShops();
            } catch (Exception e) {
                plugin.getLogger().warning("Autosave fehlgeschlagen: " + e.getMessage());
            }
        }, ticks, ticks);
    }
}
