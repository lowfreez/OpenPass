package org.openpass.bukkit.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.openpass.bukkit.auth.AuthManager;

public class PlayerConnectionListener implements Listener {
    private final AuthManager authManager;

    public PlayerConnectionListener(AuthManager authManager) {
        this.authManager = authManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        authManager.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        authManager.handleQuit(event.getPlayer());
    }
}
