package org.openpass.bukkit.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration config;
    private FileConfiguration messages;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);

        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            this.messages.setDefaults(defConfig);
        }
    }

    public Component getMessage(String key, String... replacements) {
        String template = messages.getString(key, "<red>Missing message: " + key);
        List<TagResolver> resolvers = new ArrayList<>();

        if (replacements != null && replacements.length > 1) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                resolvers.add(Placeholder.parsed(replacements[i], replacements[i + 1]));
            }
        }

        return miniMessage.deserialize(template, TagResolver.resolver(resolvers));
    }

    public int getTimeoutSeconds() {
        return config.getInt("security.timeout-seconds", 75);
    }

    public int getMinPasswordLength() {
        return config.getInt("security.min-password-length", 6);
    }

    public int getMaxPasswordLength() {
        return config.getInt("security.max-password-length", 32);
    }

    public int getMaxFailedAttempts() {
        return config.getInt("security.max-failed-attempts", 5);
    }

    public int getInitialBanMinutes() {
        return config.getInt("security.initial-ban-minutes", 15);
    }

    public int getBanMultiplier() {
        return config.getInt("security.ban-multiplier", 2);
    }

    public int getMaxAccountsPerIp() {
        return config.getInt("security.max-accounts-per-ip", 3);
    }

    public boolean isSessionEnabled() {
        return config.getBoolean("session.enabled", true);
    }

    public int getSessionDurationHours() {
        return config.getInt("session.duration-hours", 24);
    }

    public boolean isLimboEnabled() {
        return config.getBoolean("limbo.enabled", false);
    }

    public boolean isTeleportToSpawnOnJoin() {
        return config.getBoolean("limbo.teleport-to-spawn-on-join", false);
    }

    public String getLimboWorldName() {
        return config.getString("limbo.world-name", "auth_limbo");
    }

    public double getLimboSpawnX() {
        return config.getDouble("limbo.spawn-x", 0.5);
    }

    public double getLimboSpawnY() {
        return config.getDouble("limbo.spawn-y", 64.0);
    }

    public double getLimboSpawnZ() {
        return config.getDouble("limbo.spawn-z", 0.5);
    }

    public float getLimboSpawnYaw() {
        return (float) config.getDouble("limbo.spawn-yaw", 0.0);
    }

    public float getLimboSpawnPitch() {
        return (float) config.getDouble("limbo.spawn-pitch", 0.0);
    }

    public boolean isAntiBotEnabled() {
        return config.getBoolean("antibot.enabled", true);
    }

    public boolean isMojangApiCheckEnabled() {
        return config.getBoolean("license.mojang-api-check", true);
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
