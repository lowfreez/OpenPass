package org.openpass.common.model;

import java.util.UUID;

public class UserData {
    private final UUID uuid;
    private String username;
    private String passwordHash;
    private String salt;
    private boolean isPremium;
    private final long registeredAt;
    private long lastLogin;
    private String lastIp;

    public UserData(UUID uuid, String username, String passwordHash, String salt, boolean isPremium, long registeredAt, long lastLogin, String lastIp) {
        this.uuid = uuid;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.isPremium = isPremium;
        this.registeredAt = registeredAt;
        this.lastLogin = lastLogin;
        this.lastIp = lastIp;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public boolean isPremium() {
        return isPremium;
    }

    public void setPremium(boolean premium) {
        isPremium = premium;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getLastIp() {
        return lastIp;
    }

    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }
}
