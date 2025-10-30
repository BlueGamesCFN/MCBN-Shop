package de.mcbn.shops.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Event das gefeuert wird, bevor Tutorial-Nachrichten an alle Spieler gebroadcastet werden.
 * <p>
 * Externe Plugins können dieses Event nutzen um:
 * <ul>
 *   <li>Das Event zu canceln und ein eigenes Tutorial-System zu verwenden</li>
 *   <li>Die Nachrichten zu modifizieren bevor sie gesendet werden</li>
 *   <li>Eigene Logik vor oder nach dem Broadcast auszuführen</li>
 * </ul>
 * <p>
 * Wenn das Event gecancelt wird, sendet das Plugin KEINE Nachrichten -
 * das externe Plugin übernimmt die Verantwortung für die Tutorial-Anzeige.
 *
 * @see #isCancelled()
 * @see #getMessages()
 * @see #setMessages(List)
 */
public class TutorialBroadcastEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private List<String> messages;

    /**
     * Erstellt ein neues TutorialBroadcastEvent.
     *
     * @param messages Die Liste der Nachrichten, die gebroadcastet werden sollen
     */
    public TutorialBroadcastEvent(List<String> messages) {
        this.messages = messages;
    }

    /**
     * Gibt die Liste der Nachrichten zurück, die gebroadcastet werden sollen.
     * <p>
     * Diese Liste kann von Event-Listenern modifiziert werden, um die
     * gesendeten Nachrichten anzupassen.
     *
     * @return Die Liste der Broadcast-Nachrichten
     */
    public List<String> getMessages() {
        return messages;
    }

    /**
     * Setzt die Liste der Nachrichten, die gebroadcastet werden sollen.
     * <p>
     * Event-Listener können diese Methode nutzen, um die Nachrichten
     * komplett zu ersetzen oder zu ergänzen.
     *
     * @param messages Die neue Liste der Broadcast-Nachrichten
     */
    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    /**
     * Prüft, ob das Event gecancelt wurde.
     * <p>
     * Wenn gecancelt, wird das Plugin KEINE Nachrichten senden.
     * Das externe Plugin, das das Event gecancelt hat, übernimmt
     * die Verantwortung für die Tutorial-Anzeige.
     *
     * @return true wenn das Event gecancelt wurde, false sonst
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Setzt den Cancel-Status des Events.
     * <p>
     * Wenn auf true gesetzt, wird das Plugin KEINE Nachrichten senden.
     *
     * @param cancel true um das Event zu canceln, false um fortzufahren
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Gibt die HandlerList für dieses Event zurück.
     * <p>
     * Benötigt für Bukkits Event-System.
     *
     * @return Die statische HandlerList
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
