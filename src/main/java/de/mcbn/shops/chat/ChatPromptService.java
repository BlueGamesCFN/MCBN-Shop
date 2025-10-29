package de.mcbn.shops.chat;

import de.mcbn.shops.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Einfacher Chat-Prompt-Manager für schrittweise Eingaben.
 * Unterstützt Paper AsyncChatEvent und Spigot AsyncPlayerChatEvent.
 */
public class ChatPromptService implements Listener {

    private final Main plugin;
    private final Map<UUID, BiConsumer<Player, String>> active = new ConcurrentHashMap<>();

    public ChatPromptService(Main plugin) {
        this.plugin = plugin;
    }

    public void ask(Player player, String promptMessage, BiConsumer<Player, String> handler) {
        player.sendMessage(plugin.messages().raw("prefix") + promptMessage);
        active.put(player.getUniqueId(), handler);
    }

    public boolean has(Player p) { return active.containsKey(p.getUniqueId()); }
    public void clear(Player p) { active.remove(p.getUniqueId()); }

    private void handle(Player player, String message) {
        BiConsumer<Player, String> h = active.remove(player.getUniqueId());
        if (h != null) {
            Bukkit.getScheduler().runTask(plugin, () -> h.accept(player, message));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChatPaper(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (!active.containsKey(p.getUniqueId())) return;
        Component comp = event.message();
        String msg = PlainTextComponentSerializer.plainText().serialize(comp).trim();
        event.setCancelled(true);
        handle(p, msg);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChatSpigot(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        if (!active.containsKey(p.getUniqueId())) return;
        String msg = event.getMessage().trim();
        event.setCancelled(true);
        handle(p, msg);
    }
}
