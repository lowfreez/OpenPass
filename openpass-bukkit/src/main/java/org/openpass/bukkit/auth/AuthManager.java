package org.openpass.bukkit.auth;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.openpass.bukkit.config.ConfigManager;
import org.openpass.bukkit.limbo.LimboManager;
import org.openpass.bukkit.util.SchedulerUtil;
import org.openpass.common.antibot.AntiBotManager;
import org.openpass.common.model.UserData;
import org.openpass.common.security.BruteForceManager;
import org.openpass.common.security.MojangService;
import org.openpass.common.security.PasswordHasher;
import org.openpass.common.security.SessionManager;
import org.openpass.common.storage.DatabaseService;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final DatabaseService databaseService;
    private final PasswordHasher passwordHasher;
    private final BruteForceManager bruteForceManager;
    private final SessionManager sessionManager;
    private final LimboManager limboManager;
    private final MojangService mojangService;
    private final AntiBotManager antiBotManager;

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UserData> cachedUsers = new ConcurrentHashMap<>();
    private final Map<UUID, SchedulerUtil.TaskWrapper> timerTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> countdownMap = new ConcurrentHashMap<>();

    public AuthManager(JavaPlugin plugin,
                       ConfigManager configManager,
                       DatabaseService databaseService,
                       PasswordHasher passwordHasher,
                       BruteForceManager bruteForceManager,
                       SessionManager sessionManager,
                       LimboManager limboManager,
                       MojangService mojangService,
                       AntiBotManager antiBotManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseService = databaseService;
        this.passwordHasher = passwordHasher;
        this.bruteForceManager = bruteForceManager;
        this.sessionManager = sessionManager;
        this.limboManager = limboManager;
        this.mojangService = mojangService;
        this.antiBotManager = antiBotManager;
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.contains(uuid);
    }

    public boolean isRegistered(UUID uuid) {
        return cachedUsers.containsKey(uuid);
    }

    public void handleJoin(Player player) {
        String ip = getPlayerIp(player);
        String username = player.getName();

        if (configManager.isAntiBotEnabled()) {
            AntiBotManager.CheckResult antiBotResult = antiBotManager.validateConnection(ip, username);
            if (!antiBotResult.allowed()) {
                player.kick(configManager.getMessage("kick-antibot", "reason", antiBotResult.reason()));
                return;
            }
        }

        if (bruteForceManager.isBanned(ip, username)) {
            long remaining = bruteForceManager.getRemainingBanSeconds(ip, username);
            long minutes = remaining / 60;
            player.kick(configManager.getMessage("kick-brute-force",
                    "minutes", String.valueOf(minutes),
                    "remaining", bruteForceManager.formatRemainingTime(remaining)
            ));
            return;
        }

        databaseService.getUser(player.getUniqueId()).thenAccept(user -> {
            SchedulerUtil.runEntity(plugin, player, () -> {
                if (!player.isOnline()) {
                    return;
                }

                if (user != null) {
                    cachedUsers.put(player.getUniqueId(), user);

                    if (configManager.isSessionEnabled() && sessionManager.hasValidSession(player.getUniqueId(), ip)) {
                        authenticate(player, true, false);
                        player.sendMessage(configManager.getMessage("session-login"));
                        return;
                    }

                    if (user.isPremium()) {
                        authenticate(player, false, true);
                        player.sendMessage(configManager.getMessage("session-login"));
                        return;
                    }
                }

                limboManager.handleJoin(player);
                startAuthTimer(player, user != null);
            });
        });
    }

    private void startAuthTimer(Player player, boolean registered) {
        UUID uuid = player.getUniqueId();
        stopTimer(uuid);

        int totalTimeout = configManager.getTimeoutSeconds();
        countdownMap.put(uuid, totalTimeout);

        sendPrompt(player, registered);

        SchedulerUtil.TaskWrapper task = SchedulerUtil.runEntityRepeating(plugin, player, () -> {
            if (!player.isOnline() || isAuthenticated(uuid)) {
                stopTimer(uuid);
                return;
            }

            int current = countdownMap.getOrDefault(uuid, totalTimeout) - 1;
            countdownMap.put(uuid, current);

            if (current <= 0) {
                stopTimer(uuid);
                player.kick(configManager.getMessage("kick-timeout"));
                return;
            }

            player.sendActionBar(configManager.getMessage("actionbar-timeout", "seconds", String.valueOf(current)));

            if (current % 10 == 0) {
                sendPrompt(player, registered);
            }
        }, 20L, 20L);

        timerTasks.put(uuid, task);
    }

    private void sendPrompt(Player player, boolean registered) {
        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(300));
        if (registered) {
            player.showTitle(Title.title(
                    configManager.getMessage("title-login"),
                    configManager.getMessage("subtitle-login"),
                    times
            ));
            player.sendMessage(configManager.getMessage("login-prompt"));
        } else {
            player.showTitle(Title.title(
                    configManager.getMessage("title-register"),
                    configManager.getMessage("subtitle-register"),
                    times
            ));
            player.sendMessage(configManager.getMessage("register-prompt"));
        }
    }

    public void authenticate(Player player, boolean fromSession, boolean fromPremium) {
        UUID uuid = player.getUniqueId();
        stopTimer(uuid);
        authenticated.add(uuid);

        String ip = getPlayerIp(player);
        if (configManager.isSessionEnabled()) {
            sessionManager.createSession(uuid, ip, configManager.getSessionDurationHours());
        }

        UserData user = cachedUsers.get(uuid);
        if (user != null) {
            user.setLastLogin(System.currentTimeMillis());
            user.setLastIp(ip);
            databaseService.saveUser(user);
        }

        bruteForceManager.resetAttempts(ip, player.getName());
        limboManager.returnFromLimbo(player);
        player.clearTitle();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    public void register(Player player, String password, String confirmPassword) {
        UUID uuid = player.getUniqueId();
        if (isAuthenticated(uuid)) {
            player.sendMessage(configManager.getMessage("already-logged-in"));
            return;
        }

        if (isRegistered(uuid)) {
            player.sendMessage(configManager.getMessage("already-registered"));
            return;
        }

        if (!password.equals(confirmPassword)) {
            player.sendMessage(configManager.getMessage("passwords-dont-match"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int minLen = configManager.getMinPasswordLength();
        int maxLen = configManager.getMaxPasswordLength();
        if (password.length() < minLen) {
            player.sendMessage(configManager.getMessage("password-too-short", "min", String.valueOf(minLen)));
            return;
        }
        if (password.length() > maxLen) {
            player.sendMessage(configManager.getMessage("password-too-long", "max", String.valueOf(maxLen)));
            return;
        }

        SchedulerUtil.runAsync(plugin, () -> {
            String ip = getPlayerIp(player);
            int maxAccounts = configManager.getMaxAccountsPerIp();
            int currentAccounts = databaseService.countAccountsByIp(ip).join();
            if (currentAccounts >= maxAccounts) {
                SchedulerUtil.runEntity(plugin, player, () -> {
                    player.sendMessage(configManager.getMessage("max-accounts-reached", "max", String.valueOf(maxAccounts)));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                });
                return;
            }

            PasswordHasher.HashedResult result = passwordHasher.hashPassword(password);
            UserData newUser = new UserData(
                    uuid,
                    player.getName(),
                    result.hash(),
                    result.salt(),
                    false,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    ip
            );

            databaseService.saveUser(newUser).thenRun(() -> {
                SchedulerUtil.runEntity(plugin, player, () -> {
                    cachedUsers.put(uuid, newUser);
                    authenticate(player, false, false);
                    player.sendMessage(configManager.getMessage("register-success"));
                });
            });
        });
    }

    public void login(Player player, String password) {
        UUID uuid = player.getUniqueId();
        if (isAuthenticated(uuid)) {
            player.sendMessage(configManager.getMessage("already-logged-in"));
            return;
        }

        UserData user = cachedUsers.get(uuid);
        if (user == null) {
            player.sendMessage(configManager.getMessage("not-registered"));
            return;
        }

        String ip = getPlayerIp(player);
        String username = player.getName();

        SchedulerUtil.runAsync(plugin, () -> {
            boolean valid = passwordHasher.verifyPassword(password, user.getPasswordHash(), user.getSalt());

            SchedulerUtil.runEntity(plugin, player, () -> {
                if (valid) {
                    authenticate(player, false, false);
                    player.sendMessage(configManager.getMessage("login-success"));
                } else {
                    int maxAttempts = configManager.getMaxFailedAttempts();
                    int initMin = configManager.getInitialBanMinutes();
                    int mult = configManager.getBanMultiplier();

                    long banMinutes = bruteForceManager.registerFailedAttempt(ip, username, maxAttempts, initMin, mult);
                    if (banMinutes > 0) {
                        player.kick(configManager.getMessage("kick-brute-force",
                                "minutes", String.valueOf(banMinutes),
                                "remaining", banMinutes + " мин 00 сек"
                        ));
                    } else {
                        int remaining = bruteForceManager.getRemainingAttempts(ip, username, maxAttempts);
                        player.sendMessage(configManager.getMessage("wrong-password", "remaining", String.valueOf(remaining)));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                }
            });
        });
    }

    public void changePassword(Player player, String oldPassword, String newPassword) {
        UUID uuid = player.getUniqueId();
        if (!isAuthenticated(uuid)) {
            player.sendMessage(configManager.getMessage("action-blocked"));
            return;
        }

        UserData user = cachedUsers.get(uuid);
        if (user == null) {
            player.sendMessage(configManager.getMessage("not-registered"));
            return;
        }

        int minLen = configManager.getMinPasswordLength();
        int maxLen = configManager.getMaxPasswordLength();
        if (newPassword.length() < minLen) {
            player.sendMessage(configManager.getMessage("password-too-short", "min", String.valueOf(minLen)));
            return;
        }
        if (newPassword.length() > maxLen) {
            player.sendMessage(configManager.getMessage("password-too-long", "max", String.valueOf(maxLen)));
            return;
        }

        SchedulerUtil.runAsync(plugin, () -> {
            boolean matches = passwordHasher.verifyPassword(oldPassword, user.getPasswordHash(), user.getSalt());
            if (!matches) {
                SchedulerUtil.runEntity(plugin, player, () -> {
                    player.sendMessage(configManager.getMessage("change-password-wrong-old"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                });
                return;
            }

            PasswordHasher.HashedResult result = passwordHasher.hashPassword(newPassword);
            user.setPasswordHash(result.hash());
            user.setSalt(result.salt());

            databaseService.saveUser(user).thenRun(() -> {
                SchedulerUtil.runEntity(plugin, player, () -> {
                    player.sendMessage(configManager.getMessage("change-password-success"));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                });
            });
        });
    }

    public void togglePremium(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isAuthenticated(uuid)) {
            player.sendMessage(configManager.getMessage("action-blocked"));
            return;
        }

        UserData user = cachedUsers.get(uuid);
        if (user == null) {
            player.sendMessage(configManager.getMessage("not-registered"));
            return;
        }

        if (user.isPremium()) {
            user.setPremium(false);
            databaseService.saveUser(user);
            player.sendMessage(configManager.getMessage("premium-disabled"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            return;
        }

        if (configManager.isMojangApiCheckEnabled()) {
            mojangService.verifyMojangAccount(player.getName()).thenAccept(verified -> {
                SchedulerUtil.runEntity(plugin, player, () -> {
                    if (verified) {
                        user.setPremium(true);
                        databaseService.saveUser(user);
                        player.sendMessage(configManager.getMessage("premium-enabled"));
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    } else {
                        player.sendMessage(configManager.getMessage("premium-not-owner"));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                });
            });
        } else {
            user.setPremium(true);
            databaseService.saveUser(user);
            player.sendMessage(configManager.getMessage("premium-enabled"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        stopTimer(uuid);
        if (!isAuthenticated(uuid)) {
            limboManager.cleanup(uuid);
        }
        authenticated.remove(uuid);
        cachedUsers.remove(uuid);
    }

    private void stopTimer(UUID uuid) {
        SchedulerUtil.TaskWrapper task = timerTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        countdownMap.remove(uuid);
    }

    public void unregister(String username, CommandSender sender) {
        databaseService.getUserByName(username).thenAccept(user -> {
            SchedulerUtil.runGlobal(plugin, () -> {
                if (user == null) {
                    sender.sendMessage(configManager.getMessage("admin-player-not-found", "player", username));
                    return;
                }

                databaseService.deleteUserByName(username).thenRun(() -> {
                    cachedUsers.remove(user.getUuid());
                    sessionManager.invalidateSession(user.getUuid());
                    authenticated.remove(user.getUuid());

                    Player target = Bukkit.getPlayerExact(username);
                    if (target != null && target.isOnline()) {
                        SchedulerUtil.runEntity(plugin, target, () -> {
                            authenticated.remove(target.getUniqueId());
                            cachedUsers.remove(target.getUniqueId());
                            handleJoin(target);
                        });
                    }

                    sender.sendMessage(configManager.getMessage("admin-unregistered", "player", username));
                });
            });
        });
    }

    public void forceAuth(String username, CommandSender sender) {
        Player target = Bukkit.getPlayerExact(username);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(configManager.getMessage("admin-player-not-found", "player", username));
            return;
        }

        authenticate(target, false, false);
        sender.sendMessage(configManager.getMessage("prefix").append(net.kyori.adventure.text.Component.text("Игрок " + username + " успешно принудительно авторизован.")));
    }

    public void logout(Player player) {
        UUID uuid = player.getUniqueId();
        authenticated.remove(uuid);
        sessionManager.invalidateSession(uuid);
        player.sendMessage(configManager.getMessage("logout-success"));
        handleJoin(player);
    }

    public void shutdown() {
        for (SchedulerUtil.TaskWrapper task : timerTasks.values()) {
            task.cancel();
        }
        timerTasks.clear();
        countdownMap.clear();
        authenticated.clear();
        cachedUsers.clear();
    }

    private String getPlayerIp(Player player) {
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            return player.getAddress().getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }
}
