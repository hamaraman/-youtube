package com.example.demo.service;

import com.example.demo.config.JwtUtil;
import com.example.demo.config.PasswordResetTokenStore;
import com.example.demo.config.UserSessionRegistry;
import com.example.demo.controller.AuthController.ForgotPasswordRequest;
import com.example.demo.controller.AuthController.LoginRequest;
import com.example.demo.controller.AuthController.LoginResponse;
import com.example.demo.controller.AuthController.MeResponse;
import com.example.demo.controller.AuthController.ResetPasswordRequest;
import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.AuthController.SimpleResponse;
import com.example.demo.controller.AuthController.TokenRefreshResponse;
import com.example.demo.dto.SignupRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.SubscriptionRepository;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionRegistry sessionRegistry;
    private final JwtUtil jwtUtil;
    private final PasswordResetTokenStore resetTokenStore;
    private final SubscriptionRepository subscriptionRepository;
    private final JavaMailSender mailSender;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String mailSenderUsername;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       UserSessionRegistry sessionRegistry, JwtUtil jwtUtil,
                       PasswordResetTokenStore resetTokenStore,
                       SubscriptionRepository subscriptionRepository,
                       JavaMailSender mailSender,
                       LoginAttemptService loginAttemptService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
        this.jwtUtil = jwtUtil;
        this.resetTokenStore = resetTokenStore;
        this.subscriptionRepository = subscriptionRepository;
        this.mailSender = mailSender;
        this.loginAttemptService = loginAttemptService;
        this.refreshTokenService = refreshTokenService;
    }

    public void signup(SignupRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword() == null ? "" : request.getPassword().trim();
        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim();

        if (username.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디, 비밀번호, 닉네임은 필수입니다.");
        }

        if (!username.matches("^[a-zA-Z0-9_]{4,20}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디는 4~20자의 영문, 숫자, 밑줄만 사용할 수 있습니다.");
        }

        if (password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호는 최소 8자 이상이어야 합니다.");
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용 중인 아이디입니다.");
        }

        if (!email.isEmpty() && userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용 중인 이메일입니다.");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setEmail(email.isEmpty() ? null : email);
        user.setChannelName(nickname);
        user.setProfileImage(null);

        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request, String ip, HttpSession session) {
        if (loginAttemptService.isBlocked(ip)) {
            long remaining = loginAttemptService.blockRemainingSeconds(ip);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "로그인 시도 횟수를 초과했습니다. " + remaining + "초 후에 다시 시도해주세요.");
        }

        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword() == null ? "" : request.getPassword().trim();

        if (username.isEmpty() || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디와 비밀번호를 입력해줘.");
        }

        Optional<User> optionalUser = userRepository.findByUsername(username);

        if (optionalUser.isEmpty()) {
            loginAttemptService.recordFailure(ip);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디 또는 비밀번호가 올바르지 않습니다.");
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
            loginAttemptService.recordFailure(ip);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        loginAttemptService.recordSuccess(ip);

        SessionUser sessionUser = new SessionUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getChannelName(),
                user.getProfileImage(),
                user.getRole()
        );

        session.setAttribute("loginUser", sessionUser);
        sessionRegistry.register(user.getId(), session);

        String token = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getChannelName(), user.getProfileImage(),
                user.getRole()
        );

        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return new LoginResponse(true, "로그인되었습니다.", sessionUser, token, refreshToken);
    }

    public MeResponse getMe(SessionUser sessionUser) {
        if (sessionUser == null) {
            return new MeResponse(false, null);
        }
        long subscriberCount = subscriptionRepository.countByChannelOwnerId(sessionUser.getId());
        return new MeResponse(true, sessionUser.withSubscriberCount(subscriberCount));
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim();

        if (username.isEmpty() || email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디와 이메일을 입력해줘.");
        }

        Optional<User> optUser = userRepository.findByUsername(username);
        if (optUser.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디 또는 이메일이 일치하지 않습니다.");
        }

        User user = optUser.get();
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디 또는 이메일이 일치하지 않습니다.");
        }

        String token = resetTokenStore.create(user.getId());
        String resetLink = baseUrl + "/forgot-password.html?token=" + token;

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(mailSenderUsername);
            mail.setTo(user.getEmail());
            mail.setSubject("[MyTube] 비밀번호 재설정 링크");
            mail.setText(
                    "안녕하세요, " + user.getNickname() + "님!\n\n" +
                            "비밀번호 재설정을 요청하셨습니다.\n" +
                            "아래 링크를 클릭해서 새 비밀번호를 설정해주세요.\n\n" +
                            resetLink + "\n\n" +
                            "이 링크는 10분 후 만료됩니다.\n" +
                            "본인이 요청하지 않은 경우 이 이메일을 무시해주세요."
            );
            mailSender.send(mail);
            log.info("비밀번호 재설정 이메일 발송: {}", user.getEmail());
        } catch (Exception e) {
            log.error("이메일 발송 실패: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이메일 전송에 실패했습니다. 잠시 후 다시 시도해줘.");
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        String token = request.getToken() == null ? "" : request.getToken().trim();
        String newPassword = request.getNewPassword() == null ? "" : request.getNewPassword().trim();

        if (token.isEmpty() || newPassword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
        }

        if (newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호는 최소 8자 이상이어야 합니다.");
        }

        Long userId = resetTokenStore.validate(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증이 만료됐어. 다시 시도해줘.");
        }

        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용자를 찾을 수 없습니다.");
        }

        User user = optUser.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        resetTokenStore.remove(token);
    }

    public void logout(SessionUser sessionUser, String refreshToken) {
        if (sessionUser != null) {
            sessionRegistry.remove(sessionUser.getId());
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.removeRefreshToken(refreshToken);
        }
    }

    public TokenRefreshResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "리프레시 토큰이 필요합니다.");
        }

        Long userId = refreshTokenService.validateRefreshToken(refreshToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "만료되거나 유효하지 않은 리프레시 토큰입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

        String newAccessToken = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getChannelName(), user.getProfileImage(),
                user.getRole()
        );

        String newRefreshToken = refreshTokenService.createRefreshToken(user.getId());

        return new TokenRefreshResponse(true, "토큰이 재발급되었습니다.", newAccessToken, newRefreshToken);
    }
}
