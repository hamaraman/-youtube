package com.example.demo.config;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class PasswordChangeCodeStore {

    private static final int EXPIRY_MINUTES = 5;
    private final ConcurrentHashMap<Long, CodeEntry> store = new ConcurrentHashMap<>();

    public String create(Long userId, String hashedNewPassword) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        store.put(userId, new CodeEntry(code, hashedNewPassword, LocalDateTime.now().plusMinutes(EXPIRY_MINUTES)));
        return code;
    }

    public String validateAndGetHash(Long userId, String code) {
        CodeEntry entry = store.get(userId);
        if (entry == null) return null;
        if (entry.expiresAt.isBefore(LocalDateTime.now())) {
            store.remove(userId);
            return null;
        }
        if (!entry.code.equals(code)) return null;
        return entry.hashedNewPassword;
    }

    public void remove(Long userId) {
        store.remove(userId);
    }

    private record CodeEntry(String code, String hashedNewPassword, LocalDateTime expiresAt) {}
}
