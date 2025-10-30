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
        if (minutes <= 0) {
            plugin.getLogger().info("Autosave deaktiviert (Intervall <= 0)");
            return;
        }
        long ticks = minutes * 60L * 20L;
        if (autosaveTaskId != -1) Bukkit.getScheduler().cancelTask(autosaveTaskId);
        autosaveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            try {
                plugin.auctions().saveAuctions();
                plugin.shops().saveShops();
                plugin.keepers().save();
                plugin.orders().save();
                plugin.getLogger().fine("Autosave abgeschlossen.");
            } catch (Exception e) {
                plugin.getLogger().warning("Autosave fehlgeschlagen: " + e.getMessage());
            }
        }, ticks, ticks);
        plugin.getLogger().info("Autosave gestartet (Intervall: " + minutes + " Minuten, TaskID: " + autosaveTaskId + ")");
    }

    /**
     * Stoppt den Autosave-Task.
     * Verhindert Memory Leak bei Plugin-Reload.
     */
    public void stop() {
        if (autosaveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autosaveTaskId);
            plugin.getLogger().info("Autosave-Task gestoppt (TaskID: " + autosaveTaskId + ")");
            autosaveTaskId = -1;
        }
    }
}
