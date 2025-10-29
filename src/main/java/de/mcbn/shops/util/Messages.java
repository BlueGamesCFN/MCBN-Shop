package de.mcbn.shops.util;

import de.mcbn.shops.Main;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class Messages {

    private final Main plugin;
    private FileConfiguration cfg;

    public Messages(Main plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public String raw(String path) {
        String s = cfg.getString(path, "");
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public String prefixed(String path) {
        return raw("prefix") + raw(path);
    }

    public String format(String key, String... placeholders) {
        String base = raw(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            base = base.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return base;
    }

    public String prefixedFormat(String key, String... placeholders) {
        return raw("prefix") + format(key, placeholders);
    }
}
