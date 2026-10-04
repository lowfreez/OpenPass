package org.openpass.bukkit.limbo;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.openpass.bukkit.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LimboManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private World limboWorld;
    private Location spawnLocation;
    private final Map<UUID, Location> originalLocations = new ConcurrentHashMap<>();

    public LimboManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void init() {
        if (!configManager.isLimboEnabled()) {
            return;
        }

        String worldName = configManager.getLimboWorldName();
        limboWorld = Bukkit.getWorld(worldName);

        if (limboWorld == null) {
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(World.Environment.THE_END);
            creator.generator(new VoidChunkGenerator());
            limboWorld = creator.createWorld();
        }

        if (limboWorld != null) {
            setBooleanGameRule(limboWorld, "doDaylightCycle", false);
            setBooleanGameRule(limboWorld, "doMobSpawning", false);
            setBooleanGameRule(limboWorld, "doWeatherCycle", false);
            setBooleanGameRule(limboWorld, "keepInventory", true);
            limboWorld.setTime(18000);

            buildVoidSpot();

            spawnLocation = new Location(
                    limboWorld,
                    configManager.getLimboSpawnX(),
                    configManager.getLimboSpawnY(),
                    configManager.getLimboSpawnZ(),
                    configManager.getLimboSpawnYaw(),
                    configManager.getLimboSpawnPitch()
            );
        }
    }

    @SuppressWarnings("unchecked")
    private void setBooleanGameRule(World world, String name, boolean value) {
        GameRule<?> rule = GameRule.getByName(name);
        if (rule != null && rule.getType() == Boolean.class) {
            world.setGameRule((GameRule<Boolean>) rule, value);
        }
    }

    private void buildVoidSpot() {
        if (limboWorld == null) return;
        Block floor = limboWorld.getBlockAt(0, 63, 0);
        floor.setType(Material.BARRIER, false);
    }

    public void handleJoin(Player player) {
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setGravity(false);

        if (configManager.isLimboEnabled() && limboWorld != null && spawnLocation != null) {
            Location current = player.getLocation();
            if (current.getWorld() != null && !current.getWorld().equals(limboWorld)) {
                originalLocations.put(player.getUniqueId(), current);
            }
            player.teleportAsync(spawnLocation).thenAccept(success -> {
                player.setGameMode(GameMode.ADVENTURE);
                hidePlayerFromAll(player);
            });
        } else if (configManager.isTeleportToSpawnOnJoin()) {
            World currentWorld = player.getWorld();
            player.teleportAsync(currentWorld.getSpawnLocation()).thenAccept(success -> {
                hidePlayerFromAll(player);
            });
        } else {
            hidePlayerFromAll(player);
        }
    }

    public void returnFromLimbo(Player player) {
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setGravity(true);
        restorePlayerVisibility(player);

        if (configManager.isLimboEnabled()) {
            Location target = originalLocations.remove(player.getUniqueId());
            if (target != null && target.getWorld() != null && !target.getWorld().equals(limboWorld)) {
                player.teleportAsync(target).thenAccept(success -> {
                    player.setGameMode(GameMode.SURVIVAL);
                });
            } else {
                World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (defaultWorld != null && !defaultWorld.equals(limboWorld)) {
                    player.teleportAsync(defaultWorld.getSpawnLocation()).thenAccept(success -> {
                        player.setGameMode(GameMode.SURVIVAL);
                    });
                }
            }
        }
    }

    public void hidePlayerFromAll(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                player.hidePlayer(plugin, other);
                other.hidePlayer(plugin, player);
            }
        }
    }

    public void restorePlayerVisibility(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                player.showPlayer(plugin, other);
                other.showPlayer(plugin, player);
            }
        }
    }

    public void cleanup(UUID uuid) {
        originalLocations.remove(uuid);
    }
}
