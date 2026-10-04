package org.openpass.common.antibot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AntiBotManager {
    private static final Pattern VALID_NICKNAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private static final Pattern BOT_NAME_PATTERN = Pattern.compile("(?i)^(bot|mcbot|player)?[a-z0-9]{2,8}[0-9]{4,8}$");

    private final Deque<Long> globalJoinTimestamps = new ArrayDeque<>();
    private final Map<String, Deque<Long>> ipJoinTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Long> throttledIps = new ConcurrentHashMap<>();

    private boolean enabled = true;
    private int maxJoinsPerInterval = 8;
    private long intervalMillis = 3000;
    private int maxPerIpPerInterval = 3;
    private long ipIntervalMillis = 5000;
    private long throttleDurationMillis = 60000;

    public record CheckResult(boolean allowed, String reason) {}

    public synchronized CheckResult validateConnection(String ip, String username) {
        if (!enabled) {
            return new CheckResult(true, null);
        }

        if (username == null || !VALID_NICKNAME.matcher(username).matches()) {
            return new CheckResult(false, "Недопустимый никнейм");
        }

        if (BOT_NAME_PATTERN.matcher(username).matches()) {
            return new CheckResult(false, "Никнейм определен как шаблонный бот");
        }

        long now = System.currentTimeMillis();

        Long throttleUntil = throttledIps.get(ip);
        if (throttleUntil != null) {
            if (now < throttleUntil) {
                return new CheckResult(false, "Слишком частые подключения с вашего IP");
            } else {
                throttledIps.remove(ip);
            }
        }

        Deque<Long> ipHistory = ipJoinTimestamps.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (ipHistory) {
            while (!ipHistory.isEmpty() && now - ipHistory.peekFirst() > ipIntervalMillis) {
                ipHistory.pollFirst();
            }
            if (ipHistory.size() >= maxPerIpPerInterval) {
                throttledIps.put(ip, now + throttleDurationMillis);
                return new CheckResult(false, "Лимит подключений с IP превышен");
            }
            ipHistory.addLast(now);
        }

        while (!globalJoinTimestamps.isEmpty() && now - globalJoinTimestamps.peekFirst() > intervalMillis) {
            globalJoinTimestamps.pollFirst();
        }

        if (globalJoinTimestamps.size() >= maxJoinsPerInterval) {
            return new CheckResult(false, "Активирован режим защиты от бот-атаки");
        }

        globalJoinTimestamps.addLast(now);
        return new CheckResult(true, null);
    }

    public boolean isUnderAttack() {
        long now = System.currentTimeMillis();
        synchronized (this) {
            while (!globalJoinTimestamps.isEmpty() && now - globalJoinTimestamps.peekFirst() > intervalMillis) {
                globalJoinTimestamps.pollFirst();
            }
            return globalJoinTimestamps.size() >= maxJoinsPerInterval;
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
