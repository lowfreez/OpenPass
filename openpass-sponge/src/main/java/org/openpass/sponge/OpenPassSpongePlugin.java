package org.openpass.sponge;

import com.google.inject.Inject;
import org.apache.logging.log4j.Logger;
import org.openpass.common.antibot.AntiBotManager;
import org.openpass.common.security.BruteForceManager;
import org.openpass.common.security.MojangService;
import org.openpass.common.security.PasswordHasher;
import org.openpass.common.security.SessionManager;
import org.openpass.common.storage.DatabaseService;
import org.spongepowered.api.Server;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.StartingEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin("openpass")
public class OpenPassSpongePlugin {
    private final Logger logger;
    private final Path configDir;

    private DatabaseService databaseService;
    private PasswordHasher passwordHasher;
    private BruteForceManager bruteForceManager;
    private SessionManager sessionManager;
    private AntiBotManager antiBotManager;
    private MojangService mojangService;

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();

    @Inject
    public OpenPassSpongePlugin(Logger logger, @ConfigDir(sharedRoot = false) Path configDir) {
        this.logger = logger;
        this.configDir = configDir;
    }

    @Listener
    public void onServerStarting(StartingEngineEvent<Server> event) {
        configDir.toFile().mkdirs();

        DatabaseService.Config dbConfig = new DatabaseService.Config("sqlite", "localhost", 3306, "openpass", "root", "", 10, configDir.toFile());
        this.databaseService = new DatabaseService();
        this.databaseService.init(dbConfig);

        this.passwordHasher = new PasswordHasher();
        this.bruteForceManager = new BruteForceManager();
        this.sessionManager = new SessionManager();
        this.antiBotManager = new AntiBotManager();
        this.mojangService = new MojangService();
    }

    @Listener
    public void onServerStopping(StoppingEngineEvent<Server> event) {
        if (databaseService != null) {
            databaseService.close();
        }
    }
}
