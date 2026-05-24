package com.example.demo.controller;

import com.example.demo.config.JwtUtil;
import com.example.demo.config.PasswordResetTokenStore;
import com.example.demo.config.UserSessionRegistry;
import com.example.demo.dto.SignupRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionRegistry sessionRegistry;
    private final JwtUtil jwtUtil;
    private final PasswordResetTokenStore resetTokenStore;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          UserSessionRegistry sessionRegistry, JwtUtil jwtUtil,
                          PasswordResetTokenStore resetTokenStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
        this.jwtUtil = jwtUtil;
        this.resetTokenStore = resetTokenStore;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        try {
            String username = request.getUsername() == null ? "" : request.getUsername().trim();
            String password = request.getPassword() == null ? "" : request.getPassword().trim();
            String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
            String email = request.getEmail() == null ? "" : request.getEmail().trim();

            if (username.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
                return ResponseEntity.badRequest().body(new SimpleResponse(false, "아이디, 비밀번호, 닉네임은 필수입니다."));
            }

            if (!username.matches("^[a-zA-Z0-9_]{4,20}$")) {
                return ResponseEntity.badRequest().body(new SimpleResponse(false, "아이디는 4~20자의 영문, 숫자, 밑줄만 사용할 수 있습니다."));
            }

            if (password.length() < 4) {
                return ResponseEntity.badRequest().body(new SimpleResponse(false, "비밀번호는 최소 4자 이상이어야 합니다."));
            }

            if (userRepository.existsByUsername(username)) {
                return ResponseEntity.badRequest().body(new SimpleResponse(false, "이미 사용 중인 아이디입니다."));
            }

            if (!email.isEmpty() && userRepository.existsByEmail(email)) {
                return ResponseEntity.badRequest().body(new SimpleResponse(false, "이미 사용 중인 이메일입니다."));
            }

            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setNickname(nickname);
            user.setEmail(email.isEmpty() ? null : email);
            user.setChannelName(nickname);
            user.setProfileImage(null);

            userRepository.save(user);

            return ResponseEntity.ok(new SimpleResponse(true, "회원가입이 완료되었습니다."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(new SimpleResponse(false, "회원가입 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpSession session) {
        try {
            String username = request.getUsername() == null ? "" : request.getUsername().trim();
            String password = request.getPassword() == null ? "" : request.getPassword().trim();

            if (username.isEmpty() || password.isEmpty()) {
                return ResponseEntity.badRequest().body(new SimpleResponse(false, "아이디와 비밀번호를 입력해줘."));
            }

            Optional<User> optionalUser = userRepository.findByUsername(username);

            if (optionalUser.isEmpty()) {
                return ResponseEntity.badRequest().body(new SimpleResponse(false, "아이디 또는 비밀번호가 올바르지 않습니다."));
            }

            User user = optionalUser.get();

            boolean passwordMatched = false;

            if (user.getPassword() != null) {
                if (user.getPassword().startsWith("$2a$")
                        || user.getPassword().startsWith("$2b$")
                        || user.getPassword().startsWith("$2y$")) {
                    passwordMatched = passwordEncoder.matches(password, user.getPassword());
                } else {
                    passwordMatched = user.getPassword().equals(password);

                    if (passwordMatched) {
                        user.setPassword(passwordEncoder.encode(password));
                        userRepository.save(user);
                    }
                }
            }

            if (!passwordMatched) {
                return ResponseEntity.badRequest().body(new SimpleResponse(false, "아이디 또는 비밀번호가 올바르지 않습니다."));
            }

            SessionUser sessionUser = new SessionUser(
                    user.getId(),
                    user.getUsername(),
                    user.getNickname(),
                    user.getEmail(),
                    user.getChannelName(),
                    user.getProfileImage()
            );

            session.setAttribute("loginUser", sessionUser);
            sessionRegistry.register(user.getId(), session);

            String token = jwtUtil.generateToken(
                    user.getId(), user.getUsername(), user.getNickname(),
                    user.getEmail(), user.getChannelName(), user.getProfileImage()
            );

            return ResponseEntity.ok(new LoginResponse(true, "로그인되었습니다.", sessionUser, token));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(new SimpleResponse(false, "로그인 중 오류가 발생했습니다."));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");

        if (loginUser == null) {
            return ResponseEntity.ok(new MeResponse(false, null));
        }

        return ResponseEntity.ok(new MeResponse(true, (SessionUser) loginUser));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim();

        if (username.isEmpty() || email.isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "아이디와 이메일을 입력해줘."));
        }

        Optional<User> optUser = userRepository.findByUsername(username);
        if (optUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "아이디 또는 이메일이 일치하지 않습니다."));
        }

        User user = optUser.get();
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(email)) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "아이디 또는 이메일이 일치하지 않습니다."));
        }

        String token = resetTokenStore.create(user.getId());
        return ResponseEntity.ok(new TokenResponse(true, "본인 확인이 완료됐어. 새 비밀번호를 설정해줘.", token));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        String token = request.getToken() == null ? "" : request.getToken().trim();
        String newPassword = request.getNewPassword() == null ? "" : request.getNewPassword().trim();

        if (token.isEmpty() || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "잘못된 요청입니다."));
        }

        if (newPassword.length() < 4) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "비밀번호는 최소 4자 이상이어야 합니다."));
        }

        Long userId = resetTokenStore.validate(token);
        if (userId == null) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "인증이 만료됐어. 다시 시도해줘."));
        }

        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "사용자를 찾을 수 없습니다."));
        }

        User user = optUser.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        resetTokenStore.remove(token);

        return ResponseEntity.ok(new SimpleResponse(true, "비밀번호가 변경됐어. 새 비밀번호로 로그인해줘."));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof SessionUser su) {
            sessionRegistry.remove(su.getId());
        }
        session.invalidate();
        return ResponseEntity.ok(new SimpleResponse(true, "로그아웃되었습니다."));
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

        public SessionUser(Long id, String username, String nickname, String email, String channelName, String profileImage) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
            this.email = email;
            this.channelName = channelName;
            this.profileImage = profileImage;
        }

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getNickname() {
            return nickname;
        }

        public String getEmail() {
            return email;
        }

        public String getChannelName() {
            return channelName;
        }

        public String getProfileImage() {
            return profileImage;
        }
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

        public LoginResponse(boolean success, String message, SessionUser user, String token) {
            this.success = success;
            this.message = message;
            this.user = user;
            this.token = token;
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