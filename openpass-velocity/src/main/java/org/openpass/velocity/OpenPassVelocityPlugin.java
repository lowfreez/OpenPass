package org.openpass.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.openpass.common.antibot.AntiBotManager;
import org.openpass.common.model.UserData;
import org.openpass.common.security.BruteForceManager;
import org.openpass.common.security.MojangService;
import org.openpass.common.security.PasswordHasher;
import org.openpass.common.security.SessionManager;
import org.openpass.common.storage.DatabaseService;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(id = "openpass", name = "OpenPass", version = "1.0.0", authors = {"OpenPass"})
public class OpenPassVelocityPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private DatabaseService databaseService;
    private PasswordHasher passwordHasher;
    private BruteForceManager bruteForceManager;
    private SessionManager sessionManager;
    private AntiBotManager antiBotManager;
    private MojangService mojangService;

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();

    @Inject
    public OpenPassVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        dataDirectory.toFile().mkdirs();

        DatabaseService.Config dbConfig = new DatabaseService.Config("sqlite", "localhost", 3306, "openpass", "root", "", 10, dataDirectory.toFile());
        this.databaseService = new DatabaseService();
        this.databaseService.init(dbConfig);

        this.passwordHasher = new PasswordHasher();
        this.bruteForceManager = new BruteForceManager();
        this.sessionManager = new SessionManager();
        this.antiBotManager = new AntiBotManager();
        this.mojangService = new MojangService();

        registerCommands();

        server.getScheduler().buildTask(this, sessionManager::cleanup)
                .repeat(10, TimeUnit.MINUTES)
                .schedule();
    }

    private void registerCommands() {
        CommandManager cm = server.getCommandManager();

        cm.register(cm.metaBuilder("login").aliases("l").build(), new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!(invocation.source() instanceof Player player)) return;
                if (authenticated.contains(player.getUniqueId())) {
                    player.sendMessage(miniMessage.deserialize("<#3b82f6>✦ OpenPass <#475569>| <#ef4444>Вы уже авторизованы."));
                    return;
                }
                String[] args = invocation.arguments();
                if (args.length < 1) {
                    player.sendMessage(miniMessage.deserialize("<#3b82f6>✦ OpenPass <#475569>| <#94a3b8>Используйте: <#3b82f6>/login <пароль>"));
                    return;
                }
                databaseService.getUser(player.getUniqueId()).thenAccept(user -> {
                    if (user == null) {
                        player.sendMessage(miniMessage.deserialize("<#3b82f6>✦ OpenPass <#475569>| <#ef4444>Вы еще не зарегистрированы: <#3b82f6>/register <пароль> <повтор>"));
                        return;
                    }
                    boolean valid = passwordHasher.verifyPassword(args[0], user.getPasswordHash(), user.getSalt());
                    if (valid) {
                        authenticated.add(player.getUniqueId());
                        sessionManager.createSession(player.getUniqueId(), getPlayerIp(player), 24);
                        player.sendMessage(miniMessage.deserialize("<#3b82f6>✦ OpenPass <#475569>| <#22c55e>Вы успешно вошли в систему!"));
                    } else {
                        player.sendMessage(miniMessage.deserialize("<#3b82f6>✦ OpenPass <#475569>| <#ef4444>Неверный пароль!"));
                    }
                });
            }
        });

        cm.register(cm.metaBuilder("register").aliases("reg").build(), new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!(invocation.source() instanceof Player player)) return;
                if (authenticated.contains(player.getUniqueId())) {
                    player.sendMessage(miniMessage.deserialize("<#3b82f6>✦ OpenPass <#475569>| <#ef4444>Вы уже авторизованы."));
                    return;
                }
                String[] args = invocation.arguments();
                if (args.length < 2) {
                    player.sendMessage(miniMessage.deserialize("<#3b82f6>✦ OpenPass <#475569>| <#94a3b8>Используйте: <#3b82f6>/register <пароль> <повтор>"));
                    return;
                }
                if (!args[0].equals(args[1])) {
                    player.sendMessage(miniMessage.deserialize("<#3b82f6>✦ OpenPass <#475569>| <#ef4444>Введенные пароли не совпадают!"));
                    return;
                }
                PasswordHasher.HashedResult result = passwordHasher.hashPassword(args[0]);
                UserData newUser = new UserData(
                        player.getUniqueId(),
                        player.getUsername(),
                        result.hash(),
                        result.salt(),
                        false,
                        System.currentTimeMillis(),
                        System.currentTimeMillis(),
                        getPlayerIp(player)
                );
                databaseService.saveUser(newUser).thenRun(() -> {
                    authenticated.add(player.getUniqueId());
                    sessionManager.createSession(player.getUniqueId(), getPlayerIp(player), 24);
                    player.sendMessage(miniMessage.deserialize("<#3b82f6>✦ OpenPass <#475569>| <#22c55e>Вы успешно зарегистрировались!"));
                });
            }
        });
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        String username = event.getUsername();

        AntiBotManager.CheckResult result = antiBotManager.validateConnection(ip, username);
        if (!result.allowed()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    miniMessage.deserialize("<#3b82f6>✦ OpenPass\n<#94a3b8>Защита от ботов: <#3b82f6>" + result.reason())
            ));
            return;
        }

        if (bruteForceManager.isBanned(ip, username)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    miniMessage.deserialize("<#3b82f6>✦ OpenPass\n<#ef4444>Превышено число попыток ввода пароля.")
            ));
        }
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (authenticated.contains(player.getUniqueId())) return;

        String msg = event.getMessage().toLowerCase();
        if (!msg.startsWith("/login") && !msg.startsWith("/l") && !msg.startsWith("/register") && !msg.startsWith("/reg")) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            player.sendMessage(miniMessage.deserialize("<#3b82f6>✦ OpenPass <#475569>| <#ef4444>Сначала авторизуйтесь!"));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        authenticated.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (databaseService != null) {
            databaseService.close();
        }
    }

    private String getPlayerIp(Player player) {
        if (player.getRemoteAddress() != null && player.getRemoteAddress().getAddress() != null) {
            return player.getRemoteAddress().getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }
}
