package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_DURATION_MS = 15 * 60 * 1000L; // 15분

    private final ConcurrentHashMap<String, AttemptData> cache = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        AttemptData data = cache.get(key);
        if (data == null) return false;
        if (data.blockedUntil > 0 && System.currentTimeMillis() < data.blockedUntil) return true;
        if (data.blockedUntil > 0) cache.remove(key);
        return false;
    }

    public long blockRemainingSeconds(String key) {
        AttemptData data = cache.get(key);
        if (data == null || data.blockedUntil == 0) return 0;
        long remaining = (data.blockedUntil - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, 0);
    }

    public void recordFailure(String key) {
        AttemptData data = cache.computeIfAbsent(key, k -> new AttemptData());
        data.count++;
        if (data.count >= MAX_ATTEMPTS) {
            data.blockedUntil = System.currentTimeMillis() + BLOCK_DURATION_MS;
        }
    }

    public void recordSuccess(String key) {
        cache.remove(key);
    }

    private static class AttemptData {
        int count = 0;
        long blockedUntil = 0;
    }
}
