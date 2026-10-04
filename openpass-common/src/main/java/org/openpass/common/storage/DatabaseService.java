package org.openpass.common.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.openpass.common.model.UserData;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseService {
    public record Config(String type, String host, int port, String database, String user, String password, int poolSize, File dataFolder) {}

    private HikariDataSource dataSource;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public void init(Config config) {
        HikariConfig hikariConfig = new HikariConfig();

        if ("mysql".equalsIgnoreCase(config.type())) {
            hikariConfig.setJdbcUrl("jdbc:mysql://" + config.host() + ":" + config.port() + "/" + config.database() + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
            hikariConfig.setUsername(config.user());
            hikariConfig.setPassword(config.password());
            hikariConfig.setMaximumPoolSize(config.poolSize());
        } else {
            if (!config.dataFolder().exists()) {
                config.dataFolder().mkdirs();
            }
            File dbFile = new File(config.dataFolder(), "openpass.db");
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setMaximumPoolSize(1);
        }

        hikariConfig.setPoolName("OpenPass-Pool");
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(hikariConfig);
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS openpass_users (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "username VARCHAR(32) NOT NULL, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "salt VARCHAR(64) NOT NULL, " +
                "is_premium BOOLEAN DEFAULT FALSE, " +
                "registered_at BIGINT NOT NULL, " +
                "last_login BIGINT NOT NULL, " +
                "last_ip VARCHAR(64) NOT NULL" +
                ");";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<UserData> getUser(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid, username, password_hash, salt, is_premium, registered_at, last_login, last_ip FROM openpass_users WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSet(rs);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }, executor);
    }

    public CompletableFuture<UserData> getUserByName(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid, username, password_hash, salt, is_premium, registered_at, last_login, last_ip FROM openpass_users WHERE LOWER(username) = LOWER(?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSet(rs);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }, executor);
    }

    public CompletableFuture<Void> saveUser(UserData user) {
        return CompletableFuture.runAsync(() -> {
            String sql = "REPLACE INTO openpass_users (uuid, username, password_hash, salt, is_premium, registered_at, last_login, last_ip) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, user.getUuid().toString());
                stmt.setString(2, user.getUsername());
                stmt.setString(3, user.getPasswordHash());
                stmt.setString(4, user.getSalt());
                stmt.setBoolean(5, user.isPremium());
                stmt.setLong(6, user.getRegisteredAt());
                stmt.setLong(7, user.getLastLogin());
                stmt.setString(8, user.getLastIp());
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, executor);
    }

    public CompletableFuture<Boolean> deleteUserByName(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM openpass_users WHERE LOWER(username) = LOWER(?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }, executor);
    }

    public CompletableFuture<Integer> countAccountsByIp(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM openpass_users WHERE last_ip = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ip);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }, executor);
    }

    private UserData mapResultSet(ResultSet rs) throws SQLException {
        return new UserData(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("salt"),
                rs.getBoolean("is_premium"),
                rs.getLong("registered_at"),
                rs.getLong("last_login"),
                rs.getString("last_ip")
        );
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        executor.shutdown();
    }
}
