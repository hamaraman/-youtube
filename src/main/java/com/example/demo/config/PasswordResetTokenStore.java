package com.example.demo.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PasswordResetTokenStore {

    private static final long EXPIRY_MS = 10 * 60 * 1000; // 10분

    private final Map<String, TokenEntry> store = new ConcurrentHashMap<>();

    public String create(Long userId) {
        // 기존 토큰 제거 (유저당 하나만 유지)
        store.entrySet().removeIf(e -> e.getValue().userId.equals(userId));
        String token = UUID.randomUUID().toString();
        store.put(token, new TokenEntry(userId, System.currentTimeMillis() + EXPIRY_MS));
        return token;
    }

    public Long validate(String token) {
        TokenEntry entry = store.get(token);
        if (entry == null) return null;
        if (entry.expiry < System.currentTimeMillis()) {
            store.remove(token);
            return null;
        }
        return entry.userId;
    }

    public void remove(String token) {
        store.remove(token);
    }

    private static class TokenEntry {
        final Long userId;
        final long expiry;

        TokenEntry(Long userId, long expiry) {
            this.userId = userId;
            this.expiry = expiry;
        }
    }
}
