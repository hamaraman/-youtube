package com.example.demo.config;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

@Component
@WebListener
public class SessionCleanupListener implements HttpSessionListener {

    private final UserSessionRegistry registry;

    public SessionCleanupListener(UserSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        registry.removeBySessionId(se.getSession().getId());
    }
}
