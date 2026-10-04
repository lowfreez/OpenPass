package org.openpass.common.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BruteForceManager {
    private static class BanRecord {
        int failedAttempts = 0;
        int strikes = 0;
        long bannedUntil = 0;
    }

    private final Map<String, BanRecord> records = new ConcurrentHashMap<>();

    private String getKey(String ip, String username) {
        return (ip == null ? "" : ip.toLowerCase()) + "|" + (username == null ? "" : username.toLowerCase());
    }

    public boolean isBanned(String ip, String username) {
        BanRecord record = records.get(getKey(ip, username));
        if (record == null) {
            return false;
        }
        return System.currentTimeMillis() < record.bannedUntil;
    }

    public long getRemainingBanSeconds(String ip, String username) {
        BanRecord record = records.get(getKey(ip, username));
        if (record == null) {
            return 0;
        }
        long diff = record.bannedUntil - System.currentTimeMillis();
        return diff > 0 ? (diff / 1000) : 0;
    }

    public int getRemainingAttempts(String ip, String username, int maxAttempts) {
        BanRecord record = records.get(getKey(ip, username));
        if (record == null) {
            return maxAttempts;
        }
        return Math.max(0, maxAttempts - record.failedAttempts);
    }

    public long registerFailedAttempt(String ip, String username, int maxAttempts, int initialBanMinutes, int multiplier) {
        String key = getKey(ip, username);
        BanRecord record = records.computeIfAbsent(key, k -> new BanRecord());

        record.failedAttempts++;
        if (record.failedAttempts >= maxAttempts) {
            record.strikes++;
            long banMinutes = (long) (initialBanMinutes * Math.pow(multiplier, record.strikes - 1));
            record.bannedUntil = System.currentTimeMillis() + (banMinutes * 60 * 1000);
            record.failedAttempts = 0;
            return banMinutes;
        }
        return 0;
    }

    public void resetAttempts(String ip, String username) {
        BanRecord record = records.get(getKey(ip, username));
        if (record != null && System.currentTimeMillis() >= record.bannedUntil) {
            record.failedAttempts = 0;
        }
    }

    public void unban(String target) {
        if (target == null) return;
        String t = target.toLowerCase();
        records.entrySet().removeIf(entry -> entry.getKey().contains(t));
    }

    public String formatRemainingTime(long remainingSeconds) {
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        if (minutes > 0) {
            return minutes + " мин " + seconds + " сек";
        }
        return seconds + " сек";
    }
}
