package org.openpass.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.openpass.common.antibot.AntiBotManager;
import org.openpass.common.model.UserData;
import org.openpass.common.security.BruteForceManager;
import org.openpass.common.security.MojangService;
import org.openpass.common.security.PasswordHasher;
import org.openpass.common.security.SessionManager;
import org.openpass.common.storage.DatabaseService;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class OpenPassBungeePlugin extends Plugin implements Listener {
    private DatabaseService databaseService;
    private PasswordHasher passwordHasher;
    private BruteForceManager bruteForceManager;
    private SessionManager sessionManager;
    private AntiBotManager antiBotManager;
    private MojangService mojangService;

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        DatabaseService.Config dbConfig = new DatabaseService.Config("sqlite", "localhost", 3306, "openpass", "root", "", 10, getDataFolder());
        this.databaseService = new DatabaseService();
        this.databaseService.init(dbConfig);

        this.passwordHasher = new PasswordHasher();
        this.bruteForceManager = new BruteForceManager();
        this.sessionManager = new SessionManager();
        this.antiBotManager = new AntiBotManager();
        this.mojangService = new MojangService();

        getProxy().getPluginManager().registerListener(this, this);

        registerCommands();

        getProxy().getScheduler().schedule(this, sessionManager::cleanup, 10, 10, TimeUnit.MINUTES);
    }

    private void registerCommands() {
        getProxy().getPluginManager().registerCommand(this, new Command("login", "openpass.use", "l") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                if (!(sender instanceof ProxiedPlayer player)) return;
                if (authenticated.contains(player.getUniqueId())) {
                    player.sendMessage(new TextComponent("§9✦ OpenPass §7| §cВы уже авторизованы."));
                    return;
                }
                if (args.length < 1) {
                    player.sendMessage(new TextComponent("§9✦ OpenPass §7| §fИспользуйте: §9/login <пароль>"));
                    return;
                }
                databaseService.getUser(player.getUniqueId()).thenAccept(user -> {
                    if (user == null) {
                        player.sendMessage(new TextComponent("§9✦ OpenPass §7| §cВы не зарегистрированы: /register <пароль> <повтор>"));
                        return;
                    }
                    boolean valid = passwordHasher.verifyPassword(args[0], user.getPasswordHash(), user.getSalt());
                    if (valid) {
                        authenticated.add(player.getUniqueId());
                        sessionManager.createSession(player.getUniqueId(), getPlayerIp(player), 24);
                        player.sendMessage(new TextComponent("§9✦ OpenPass §7| §aВы успешно вошли в систему!"));
                    } else {
                        player.sendMessage(new TextComponent("§9✦ OpenPass §7| §cНеверный пароль!"));
                    }
                });
            }
        });

        getProxy().getPluginManager().registerCommand(this, new Command("register", "openpass.use", "reg") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                if (!(sender instanceof ProxiedPlayer player)) return;
                if (authenticated.contains(player.getUniqueId())) {
                    player.sendMessage(new TextComponent("§9✦ OpenPass §7| §cВы уже авторизованы."));
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(new TextComponent("§9✦ OpenPass §7| §fИспользуйте: §9/register <пароль> <повтор>"));
                    return;
                }
                if (!args[0].equals(args[1])) {
                    player.sendMessage(new TextComponent("§9✦ OpenPass §7| §cПароли не совпадают!"));
                    return;
                }
                PasswordHasher.HashedResult result = passwordHasher.hashPassword(args[0]);
                UserData newUser = new UserData(
                        player.getUniqueId(),
                        player.getName(),
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
                    player.sendMessage(new TextComponent("§9✦ OpenPass §7| §aВы успешно зарегистрированы!"));
                });
            }
        });
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        String ip = event.getConnection().getAddress().getAddress().getHostAddress();
        String username = event.getConnection().getName();

        AntiBotManager.CheckResult result = antiBotManager.validateConnection(ip, username);
        if (!result.allowed()) {
            event.setCancelled(true);
            event.setCancelReason(new TextComponent("§9✦ OpenPass\n§7Защита от ботов: §9" + result.reason()));
            return;
        }

        if (bruteForceManager.isBanned(ip, username)) {
            event.setCancelled(true);
            event.setCancelReason(new TextComponent("§9✦ OpenPass\n§cПревышено число неверных попыток ввода пароля."));
        }
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer player)) return;
        if (authenticated.contains(player.getUniqueId())) return;

        String msg = event.getMessage().toLowerCase();
        if (!msg.startsWith("/login") && !msg.startsWith("/l") && !msg.startsWith("/register") && !msg.startsWith("/reg")) {
            event.setCancelled(true);
            player.sendMessage(new TextComponent("§9✦ OpenPass §7| §cСначала авторизуйтесь!"));
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        authenticated.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onDisable() {
        if (databaseService != null) {
            databaseService.close();
        }
    }

    private String getPlayerIp(ProxiedPlayer player) {
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            return player.getAddress().getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }
}
