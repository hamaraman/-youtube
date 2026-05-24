package com.example.demo.config;

import com.example.demo.controller.AuthController;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class AdminChecker {

    private static final String ADMIN_USERNAME = "admin";

    public boolean isAdmin(HttpSession session, LoginUserResolver loginUserResolver) {
        AuthController.SessionUser user = loginUserResolver.getUser(session);
        return user != null && ADMIN_USERNAME.equals(user.getUsername());
    }

    public boolean isAdmin(AuthController.SessionUser user) {
        return user != null && ADMIN_USERNAME.equals(user.getUsername());
    }
}
