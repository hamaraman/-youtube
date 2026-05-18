package com.example.demo.config;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SessionCleanupListener {

    @Bean
    public ServletListenerRegistrationBean<HttpSessionListener> httpSessionCleanupListener(UserSessionRegistry registry) {
        return new ServletListenerRegistrationBean<>(new HttpSessionListener() {
            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                registry.removeBySessionId(se.getSession().getId());
            }
        });
    }
}
