package de.mcbn.shops.util;

import de.mcbn.shops.Main;
import de.mcbn.shops.event.TutorialBroadcastEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Service für periodische Tutorial/Info-Broadcasts an alle Spieler.
 * <p>
 * Dieser Service:
 * <ul>
 *   <li>Lädt Tutorial-Nachrichten aus der config.yml</li>
 *   <li>Feuert ein {@link TutorialBroadcastEvent} VOR dem Senden</li>
 *   <li>Erlaubt externen Plugins das Broadcast-System zu übernehmen</li>
 *   <li>Sendet Nachrichten nur wenn das Event NICHT gecancelt wurde</li>
 * </ul>
 * <p>
 * <b>API für externe Plugins:</b><br>
 * Externe Plugins können einen Event-Handler registrieren:
 * <pre>{@code
 * @EventHandler
 * public void onTutorial(TutorialBroadcastEvent event) {
 *     event.setCancelled(true);  // Übernimmt Tutorial-System
 *     // Eigene Logik hier
 * }
 * }</pre>
 * <p>
 * Oder die Nachrichten modifizieren:
 * <pre>{@code
 * @EventHandler
 * public void onTutorial(TutorialBroadcastEvent event) {
 *     List<String> messages = event.getMessages();
 *     messages.add("Zusätzliche Nachricht");
 *     event.setMessages(messages);
 * }
 * }</pre>
 */
public class TutorialBroadcastService {

    private final Main plugin;
    private BukkitTask broadcastTask;

    /**
     * Erstellt einen neuen TutorialBroadcastService.
     *
     * @param plugin Die Main-Plugin-Instanz
     */
    public TutorialBroadcastService(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Startet den periodischen Broadcast-Service.
     * <p>
     * Lädt die Konfiguration und startet einen wiederkehrenden Task,
     * der gemäß dem konfigurierten Intervall Tutorial-Nachrichten sendet.
     */
    public void start() {
        FileConfiguration config = plugin.getConfig();

        // Prüfe ob Tutorial-System aktiviert ist
        if (!config.getBoolean("tutorial.enabled", true)) {
            plugin.getLogger().info("Tutorial-Broadcast-System ist deaktiviert.");
            return;
        }

        // Lade Broadcast-Intervall (in Minuten)
        int intervalMinutes = config.getInt("tutorial.broadcast-interval-minutes", 30);

        if (intervalMinutes <= 0) {
            plugin.getLogger().warning("Tutorial-Broadcast-Intervall muss größer als 0 sein. System deaktiviert.");
            return;
        }

        // Konvertiere Minuten in Ticks (1 Minute = 1200 Ticks)
        long intervalTicks = intervalMinutes * 1200L;

        // Starte wiederkehrenden Task
        broadcastTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::broadcastTutorial,
                100L, // Erste Ausführung nach 5 Sekunden (100 Ticks)
                intervalTicks
        );

        plugin.getLogger().info("Tutorial-Broadcast-System gestartet. Intervall: " + intervalMinutes + " Minuten.");
    }

    /**
     * Stoppt den Broadcast-Service.
     * <p>
     * Cancelt den wiederkehrenden Task.
     */
    public void stop() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
            plugin.getLogger().info("Tutorial-Broadcast-System gestoppt.");
        }
    }

    /**
     * Führt einen einzelnen Tutorial-Broadcast durch.
     * <p>
     * Diese Methode:
     * <ol>
     *   <li>Lädt die Tutorial-Nachrichten aus der Config</li>
     *   <li>Feuert ein {@link TutorialBroadcastEvent}</li>
     *   <li>Wenn das Event gecancelt wurde: Sendet NICHTS (externes Plugin übernimmt)</li>
     *   <li>Wenn das Event NICHT gecancelt wurde: Sendet die (möglicherweise modifizierten) Nachrichten</li>
     * </ol>
     */
    private void broadcastTutorial() {
        List<String> messages = getMessagesFromConfig();

        // Wenn keine Nachrichten konfiguriert sind, nichts tun
        if (messages.isEmpty()) {
            return;
        }

        // Feuere TutorialBroadcastEvent
        TutorialBroadcastEvent event = new TutorialBroadcastEvent(new ArrayList<>(messages));
        Bukkit.getPluginManager().callEvent(event);

        // Wenn Event gecancelt wurde, externes Plugin übernimmt
        if (event.isCancelled()) {
            plugin.getLogger().fine("Tutorial-Broadcast wurde von externem Plugin übernommen.");
            return;
        }

        // Verwende möglicherweise modifizierte Nachrichten vom Event
        List<String> finalMessages = event.getMessages();

        // Sende jede Nachricht als Broadcast
        for (String message : finalMessages) {
            if (message != null && !message.trim().isEmpty()) {
                // Übersetze Color-Codes (&a, &c, etc.)
                String coloredMessage = ChatColor.translateAlternateColorCodes('&', message);
                Bukkit.broadcastMessage(coloredMessage);
            }
        }
    }

    /**
     * Lädt die Tutorial-Nachrichten aus der Config.
     * <p>
     * Liest von config.yml unter dem Pfad {@code tutorial.messages}.
     *
     * @return Liste der konfigurierten Nachrichten
     */
    private List<String> getMessagesFromConfig() {
        FileConfiguration config = plugin.getConfig();
        List<String> messages = config.getStringList("tutorial.messages");

        if (messages == null) {
            return new ArrayList<>();
        }

        return messages;
    }

    /**
     * Sendet sofort einen Tutorial-Broadcast (unabhängig vom Timer).
     * <p>
     * Kann von Commands oder anderen Komponenten aufgerufen werden,
     * um einen manuellen Broadcast auszulösen.
     */
    public void broadcastNow() {
        Bukkit.getScheduler().runTask(plugin, this::broadcastTutorial);
    }

    /**
     * Lädt die Konfiguration neu und startet den Service neu.
     * <p>
     * Sollte aufgerufen werden, wenn die Config neu geladen wurde.
     */
    public void reload() {
        stop();
        start();
    }
}
