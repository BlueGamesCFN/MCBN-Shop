package de.mcbn.shops.integration;

import de.mcbn.shops.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Integration mit MCBNTabChat für Tutorial-Verwaltung.
 * <p>
 * Diese Klasse registriert das MCBN-Shops Tutorial bei MCBNTabChat,
 * sodass Spieler es über /tutorial abrufen können.
 * <p>
 * Die Integration ist optional - wenn MCBNTabChat nicht installiert ist,
 * funktioniert das Plugin normal weiter mit dem eigenen Broadcast-System.
 */
public class MCBNTabChatIntegration {

    private final Main plugin;
    private Object tutorialAPI;
    private boolean integrated = false;

    /**
     * Erstellt eine neue MCBNTabChat-Integration.
     *
     * @param plugin Die Main-Plugin-Instanz
     */
    public MCBNTabChatIntegration(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Versucht, MCBNTabChat zu finden und das Tutorial zu registrieren.
     * <p>
     * Diese Methode verwendet Reflection, um mit MCBNTabChat zu kommunizieren,
     * sodass keine Compile-Time-Dependency benötigt wird.
     *
     * @return true wenn die Integration erfolgreich war, false sonst
     */
    public boolean integrate() {
        // Prüfe ob MCBNTabChat installiert ist
        Plugin mcbnTabChat = Bukkit.getPluginManager().getPlugin("MCBNTabChat");

        if (mcbnTabChat == null) {
            plugin.getLogger().info("MCBNTabChat nicht gefunden - Tutorial-Integration übersprungen");
            return false;
        }

        try {
            // Versuche die TutorialAPI zu holen
            Class<?> mcbnTabChatPluginClass = mcbnTabChat.getClass();
            Object tutorialAPIInstance = mcbnTabChatPluginClass.getMethod("getTutorialAPI").invoke(mcbnTabChat);

            if (tutorialAPIInstance == null) {
                plugin.getLogger().warning("TutorialAPI konnte nicht geladen werden");
                return false;
            }

            this.tutorialAPI = tutorialAPIInstance;

            // Registriere das Tutorial
            registerTutorial();

            integrated = true;
            plugin.getLogger().info("Tutorial erfolgreich bei MCBNTabChat registriert!");
            return true;

        } catch (NoSuchMethodException e) {
            plugin.getLogger().warning("MCBNTabChat ist installiert, aber getTutorialAPI() Methode fehlt - möglicherweise falsche Version?");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Integrieren mit MCBNTabChat: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Registriert das MCBN-Shops Tutorial bei MCBNTabChat.
     * <p>
     * Lädt die Tutorial-Nachrichten aus der config.yml und registriert sie
     * bei MCBNTabChat unter dem Namen "MCBN-Shops".
     */
    private void registerTutorial() {
        if (tutorialAPI == null) {
            return;
        }

        try {
            // Lade Tutorial-Nachrichten aus Config
            List<String> messages = getTutorialMessages();

            if (messages.isEmpty()) {
                plugin.getLogger().warning("Keine Tutorial-Nachrichten in config.yml gefunden!");
                return;
            }

            // Registriere Tutorial mit Reflection
            // tutorialAPI.registerTutorial("MCBN-Shops", messages)
            Class<?> tutorialAPIClass = tutorialAPI.getClass();
            tutorialAPIClass.getMethod("registerTutorial", String.class, List.class)
                    .invoke(tutorialAPI, "MCBN-Shops", messages);

            plugin.getLogger().fine("Tutorial-Nachrichten bei MCBNTabChat registriert (" + messages.size() + " Zeilen)");

        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Registrieren des Tutorials: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Entfernt das Tutorial von MCBNTabChat.
     * <p>
     * Sollte aufgerufen werden, wenn das Plugin deaktiviert wird.
     */
    public void unregister() {
        if (!integrated || tutorialAPI == null) {
            return;
        }

        try {
            // tutorialAPI.unregisterTutorial("MCBN-Shops")
            Class<?> tutorialAPIClass = tutorialAPI.getClass();
            tutorialAPIClass.getMethod("unregisterTutorial", String.class)
                    .invoke(tutorialAPI, "MCBN-Shops");

            plugin.getLogger().fine("Tutorial von MCBNTabChat entfernt");

        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Entfernen des Tutorials: " + e.getMessage());
        }
    }

    /**
     * Lädt die Tutorial-Nachrichten aus der config.yml.
     * <p>
     * Konvertiert '&' Color-Codes in § Color-Codes für MCBNTabChat.
     *
     * @return Liste der Tutorial-Nachrichten
     */
    private List<String> getTutorialMessages() {
        List<String> rawMessages = plugin.getConfig().getStringList("tutorial.messages");

        if (rawMessages == null) {
            return new ArrayList<>();
        }

        // Konvertiere Color-Codes von & zu § für MCBNTabChat
        List<String> convertedMessages = new ArrayList<>();
        for (String message : rawMessages) {
            if (message != null) {
                // MCBNTabChat verwendet § statt &
                String converted = message.replace('&', '§');
                convertedMessages.add(converted);
            }
        }

        return convertedMessages;
    }

    /**
     * Prüft, ob die Integration erfolgreich war.
     *
     * @return true wenn MCBNTabChat integriert ist, false sonst
     */
    public boolean isIntegrated() {
        return integrated;
    }
}
