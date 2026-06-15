package com.example.demo.controller;

import com.example.demo.dto.SignupRequest;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        try {
            authService.signup(request);
            return ResponseEntity.ok(new SimpleResponse(true, "회원가입이 완료되었습니다."));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        } catch (Exception e) {
            log.error("회원가입 중 에러 발생", e);
            return ResponseEntity.internalServerError().body(new SimpleResponse(false, "회원가입 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpSession session,
                                   HttpServletRequest httpRequest) {
        try {
            String ip = getClientIp(httpRequest);
            LoginResponse response = authService.login(request, ip, session);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        } catch (Exception e) {
            log.error("로그인 중 에러 발생", e);
            return ResponseEntity.internalServerError().body(new SimpleResponse(false, "로그인 중 오류가 발생했습니다."));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip.trim();
        return request.getRemoteAddr();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        SessionUser su = (SessionUser) session.getAttribute("loginUser");
        return ResponseEntity.ok(authService.getMe(su));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            authService.forgotPassword(request);
            return ResponseEntity.ok(new SimpleResponse(true, "이메일을 확인해줘! 비밀번호 재설정 링크를 보냈어."));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new SimpleResponse(false, "이메일 전송에 실패했습니다."));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request);
            return ResponseEntity.ok(new SimpleResponse(true, "비밀번호가 변경됐어. 새 비밀번호로 로그인해줘."));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new SimpleResponse(false, "비밀번호 재설정 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session,
                                    @RequestParam(value = "refreshToken", required = false) String refreshToken) {
        SessionUser su = (SessionUser) session.getAttribute("loginUser");
        authService.logout(su, refreshToken);
        session.invalidate();
        return ResponseEntity.ok(new SimpleResponse(true, "로그아웃되었습니다."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody TokenRefreshRequest request) {
        try {
            TokenRefreshResponse response = authService.refresh(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new SimpleResponse(false, "토큰 재발급 중 오류가 발생했습니다."));
        }
    }

    public static class ForgotPasswordRequest {
        private String username;
        private String email;
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public void setUsername(String username) { this.username = username; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class ResetPasswordRequest {
        private String token;
        private String newPassword;
        public String getToken() { return token; }
        public String getNewPassword() { return newPassword; }
        public void setToken(String token) { this.token = token; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    public static class TokenResponse {
        private boolean success;
        private String message;
        private String token;
        public TokenResponse(boolean success, String message, String token) {
            this.success = success;
            this.message = message;
            this.token = token;
        }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getToken() { return token; }
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public LoginRequest() {
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class SessionUser {
        private Long id;
        private String username;
        private String nickname;
        private String email;
        private String channelName;
        private String profileImage;
        private String role;
        private long subscriberCount;

        public SessionUser(Long id, String username, String nickname, String email, String channelName, String profileImage, String role) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
            this.email = email;
            this.channelName = channelName;
            this.profileImage = profileImage;
            this.role = role != null ? role : "USER";
        }

        public SessionUser withSubscriberCount(long count) {
            SessionUser copy = new SessionUser(id, username, nickname, email, channelName, profileImage, role);
            copy.subscriberCount = count;
            return copy;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getNickname() { return nickname; }
        public String getEmail() { return email; }
        public String getChannelName() { return channelName; }
        public String getProfileImage() { return profileImage; }
        public String getRole() { return role; }
        public long getSubscriberCount() { return subscriberCount; }
        public boolean isAdmin() { return "ADMIN".equals(role); }
    }

    public static class SimpleResponse {
        private boolean success;
        private String message;

        public SimpleResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class LoginResponse {
        private boolean success;
        private String message;
        private SessionUser user;
        private String token;
        private String refreshToken;

        public LoginResponse() {}

        public LoginResponse(boolean success, String message, SessionUser user, String token) {
            this.success = success;
            this.message = message;
            this.user = user;
            this.token = token;
        }

        public LoginResponse(boolean success, String message, SessionUser user, String token, String refreshToken) {
            this.success = success;
            this.message = message;
            this.user = user;
            this.token = token;
            this.refreshToken = refreshToken;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public SessionUser getUser() {
            return user;
        }

        public String getToken() {
            return token;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }

    public static class TokenRefreshRequest {
        private String refreshToken;

        public TokenRefreshRequest() {}

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class TokenRefreshResponse {
        private boolean success;
        private String message;
        private String accessToken;
        private String refreshToken;

        public TokenRefreshResponse(boolean success, String message, String accessToken, String refreshToken) {
            this.success = success;
            this.message = message;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }

    public static class MeResponse {
        private boolean loggedIn;
        private SessionUser user;

        public MeResponse(boolean loggedIn, SessionUser user) {
            this.loggedIn = loggedIn;
            this.user = user;
        }

        public boolean isLoggedIn() {
            return loggedIn;
        }

        public SessionUser getUser() {
            return user;
        }
    }
}