package org.openpass.common.security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    public record Session(UUID uuid, String ip, long expireAt) {}

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public boolean hasValidSession(UUID uuid, String ip) {
        if (uuid == null || ip == null) {
            return false;
        }

        Session session = sessions.get(uuid);
        if (session == null) {
            return false;
        }

        if (System.currentTimeMillis() > session.expireAt()) {
            sessions.remove(uuid);
            return false;
        }

        return session.ip().equalsIgnoreCase(ip);
    }

    public void createSession(UUID uuid, String ip, int durationHours) {
        if (uuid == null || ip == null) {
            return;
        }

        long expireAt = System.currentTimeMillis() + ((long) durationHours * 60 * 60 * 1000);
        sessions.put(uuid, new Session(uuid, ip, expireAt));
    }

    public void invalidateSession(UUID uuid) {
        if (uuid != null) {
            sessions.remove(uuid);
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> now > entry.getValue().expireAt());
    }
}
