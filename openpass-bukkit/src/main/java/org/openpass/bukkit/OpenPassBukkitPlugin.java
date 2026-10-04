package org.openpass.bukkit;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.openpass.bukkit.auth.AuthManager;
import org.openpass.bukkit.command.ChangePasswordCommand;
import org.openpass.bukkit.command.LoginCommand;
import org.openpass.bukkit.command.OpenPassAdminCommand;
import org.openpass.bukkit.command.PremiumCommand;
import org.openpass.bukkit.command.RegisterCommand;
import org.openpass.bukkit.config.ConfigManager;
import org.openpass.bukkit.limbo.LimboManager;
import org.openpass.bukkit.listener.PlayerConnectionListener;
import org.openpass.bukkit.listener.PlayerSecurityListener;
import org.openpass.bukkit.util.SchedulerUtil;
import org.openpass.common.antibot.AntiBotManager;
import org.openpass.common.security.BruteForceManager;
import org.openpass.common.security.MojangService;
import org.openpass.common.security.PasswordHasher;
import org.openpass.common.security.SessionManager;
import org.openpass.common.storage.DatabaseService;

public class OpenPassBukkitPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private DatabaseService databaseService;
    private PasswordHasher passwordHasher;
    private BruteForceManager bruteForceManager;
    private SessionManager sessionManager;
    private LimboManager limboManager;
    private MojangService mojangService;
    private AntiBotManager antiBotManager;
    private AuthManager authManager;
    private SchedulerUtil.TaskWrapper sessionCleanupTask;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);

        String type = configManager.getConfig().getString("database.type", "sqlite");
        String host = configManager.getConfig().getString("database.mysql.host", "localhost");
        int port = configManager.getConfig().getInt("database.mysql.port", 3306);
        String db = configManager.getConfig().getString("database.mysql.database", "openpass");
        String user = configManager.getConfig().getString("database.mysql.username", "root");
        String pass = configManager.getConfig().getString("database.mysql.password", "");
        int poolSize = configManager.getConfig().getInt("database.mysql.pool-size", 10);

        DatabaseService.Config dbConfig = new DatabaseService.Config(type, host, port, db, user, pass, poolSize, getDataFolder());
        this.databaseService = new DatabaseService();
        this.databaseService.init(dbConfig);

        this.passwordHasher = new PasswordHasher();
        this.bruteForceManager = new BruteForceManager();
        this.sessionManager = new SessionManager();
        this.antiBotManager = new AntiBotManager();

        this.limboManager = new LimboManager(this, configManager);
        this.limboManager.init();

        this.mojangService = new MojangService();

        this.authManager = new AuthManager(
                this,
                configManager,
                databaseService,
                passwordHasher,
                bruteForceManager,
                sessionManager,
                limboManager,
                mojangService,
                antiBotManager
        );

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(authManager), this);
        getServer().getPluginManager().registerEvents(new PlayerSecurityListener(authManager, configManager), this);

        registerCommand("register", new RegisterCommand(authManager, configManager));
        registerCommand("login", new LoginCommand(authManager, configManager));
        registerCommand("changepassword", new ChangePasswordCommand(authManager, configManager));
        registerCommand("premium", new PremiumCommand(authManager));
        registerCommand("openpass", new OpenPassAdminCommand(configManager, bruteForceManager));

        this.sessionCleanupTask = SchedulerUtil.runAsyncRepeating(this, sessionManager::cleanup, 600L, 600L);
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            if (executor instanceof org.bukkit.command.CommandExecutor ce) {
                command.setExecutor(ce);
            }
            if (executor instanceof org.bukkit.command.TabCompleter tc) {
                command.setTabCompleter(tc);
            }
        }
    }

    @Override
    public void onDisable() {
        if (sessionCleanupTask != null) {
            sessionCleanupTask.cancel();
        }
        if (authManager != null) {
            authManager.shutdown();
        }
        if (databaseService != null) {
            databaseService.close();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public DatabaseService getDatabaseService() {
        return databaseService;
    }

    public LimboManager getLimboManager() {
        return limboManager;
    }
}
