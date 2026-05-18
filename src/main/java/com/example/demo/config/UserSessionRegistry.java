package com.example.demo.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserSessionRegistry {

    private final ConcurrentHashMap<Long, HttpSession> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, HttpSession session) {
        HttpSession existing = sessions.put(userId, session);
        if (existing != null && !existing.getId().equals(session.getId())) {
            try {
                existing.invalidate();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public void remove(Long userId) {
        sessions.remove(userId);
    }

    public void removeBySessionId(String sessionId) {
        sessions.entrySet().removeIf(e -> e.getValue().getId().equals(sessionId));
    }
}
