package com.example.demo.config;

import com.example.demo.controller.AuthController;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * JWT(Authorization 헤더) 또는 세션 중 하나에서 로그인 유저를 조회.
 * 웹(세션)과 모바일 앱(JWT) 모두 지원.
 */
@Component
public class LoginUserResolver {

    public AuthController.SessionUser getUser(HttpSession session) {
        // JWT 우선 확인
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest req = attrs.getRequest();
            Object jwtUser = req.getAttribute("jwtLoginUser");
            if (jwtUser instanceof AuthController.SessionUser su) return su;
        } catch (Exception ignored) {
        }
        // 세션 폴백
        Object sessionUser = session.getAttribute("loginUser");
        if (sessionUser instanceof AuthController.SessionUser su) return su;
        return null;
    }

    public Long getUserId(HttpSession session) {
        AuthController.SessionUser user = getUser(session);
        return user != null ? user.getId() : null;
    }
}
