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
import com.example.demo.controller.AuthController.TokenRefreshResponse;
import com.example.demo.dto.SignupRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.SubscriptionRepository;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserSessionRegistry sessionRegistry;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordResetTokenStore resetTokenStore;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private JavaMailSender mailSender;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private HttpSession session;

    @InjectMocks private AuthService authService;

    @BeforeEach
    void injectValueFields() {
        ReflectionTestUtils.setField(authService, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(authService, "mailSenderUsername", "test@mytube.com");
    }

    private SignupRequest signupReq(String u, String p, String n, String e) {
        SignupRequest r = new SignupRequest();
        r.setUsername(u); r.setPassword(p); r.setNickname(n); r.setEmail(e);
        return r;
    }

    private LoginRequest loginReq(String u, String p) {
        LoginRequest r = new LoginRequest();
        r.setUsername(u); r.setPassword(p);
        return r;
    }

    @Nested
    class Signup {

        @Test
        void emptyFields_throwsBadRequest() {
            assertThatThrownBy(() -> authService.signup(signupReq("", "pass1234", "nick", "e@e.com")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("필수");
        }

        @Test
        void invalidUsernamePattern_throwsBadRequest() {
            assertThatThrownBy(() -> authService.signup(signupReq("abc", "pass1234", "nick", "e@e.com")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("아이디");
            assertThatThrownBy(() -> authService.signup(signupReq("한글아이디", "pass1234", "nick", "e@e.com")))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void shortPassword_throwsBadRequest() {
            assertThatThrownBy(() -> authService.signup(signupReq("validuser", "short", "nick", "e@e.com")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("8자");
        }

        @Test
        void duplicateUsername_throwsBadRequest() {
            when(userRepository.existsByUsername("validuser")).thenReturn(true);
            assertThatThrownBy(() -> authService.signup(signupReq("validuser", "pass1234", "nick", "e@e.com")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("아이디");
        }

        @Test
        void duplicateEmail_throwsBadRequest() {
            when(userRepository.existsByUsername("validuser")).thenReturn(false);
            when(userRepository.existsByEmail("e@e.com")).thenReturn(true);
            assertThatThrownBy(() -> authService.signup(signupReq("validuser", "pass1234", "nick", "e@e.com")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("이메일");
        }

        @Test
        void validRequest_persistsUserWithEncodedPassword() {
            when(userRepository.existsByUsername("validuser")).thenReturn(false);
            when(userRepository.existsByEmail("e@e.com")).thenReturn(false);
            when(passwordEncoder.encode("pass1234")).thenReturn("$2a$encoded");

            authService.signup(signupReq("validuser", "pass1234", "닉네임", "e@e.com"));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getUsername()).isEqualTo("validuser");
            assertThat(saved.getPassword()).isEqualTo("$2a$encoded");
            assertThat(saved.getNickname()).isEqualTo("닉네임");
            assertThat(saved.getEmail()).isEqualTo("e@e.com");
            assertThat(saved.getChannelName()).isEqualTo("닉네임");
        }

        @Test
        void emailBlank_isStoredAsNull() {
            when(userRepository.existsByUsername("validuser")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$x");

            authService.signup(signupReq("validuser", "pass1234", "nick", ""));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isNull();
            verify(userRepository, never()).existsByEmail(anyString());
        }
    }

    @Nested
    class Login {

        @Test
        void ipBlocked_throwsTooManyRequests() {
            when(loginAttemptService.isBlocked("1.1.1.1")).thenReturn(true);
            when(loginAttemptService.blockRemainingSeconds("1.1.1.1")).thenReturn(120L);
            assertThatThrownBy(() -> authService.login(loginReq("u", "p"), "1.1.1.1", session))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("초과");
        }

        @Test
        void emptyCredentials_throwsBadRequest() {
            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            assertThatThrownBy(() -> authService.login(loginReq("", ""), "1.1.1.1", session))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void unknownUser_recordsFailureAndThrows() {
            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginReq("nobody", "pass1234"), "1.1.1.1", session))
                    .isInstanceOf(ResponseStatusException.class);
            verify(loginAttemptService).recordFailure("1.1.1.1");
        }

        @Test
        void wrongPassword_recordsFailure() {
            User u = new User();
            u.setId(1L); u.setUsername("alice"); u.setPassword("$2a$hashed");
            u.setNickname("A"); u.setChannelName("A"); u.setRole("USER");

            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
            when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginReq("alice", "wrong"), "1.1.1.1", session))
                    .isInstanceOf(ResponseStatusException.class);
            verify(loginAttemptService).recordFailure("1.1.1.1");
        }

        @Test
        void validCredentials_returnsResponseAndPersistsSession() {
            User u = new User();
            u.setId(42L); u.setUsername("alice"); u.setPassword("$2a$hashed");
            u.setNickname("앨리스"); u.setChannelName("Alice TV"); u.setRole("USER");
            u.setEmail("alice@e.com");

            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
            when(passwordEncoder.matches("pass1234", "$2a$hashed")).thenReturn(true);
            when(jwtUtil.generateToken(eq(42L), eq("alice"), any(), any(), any(), any(), eq("USER")))
                    .thenReturn("access-token");
            when(refreshTokenService.createRefreshToken(42L)).thenReturn("refresh-token");

            LoginResponse response = authService.login(loginReq("alice", "pass1234"), "1.1.1.1", session);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            verify(loginAttemptService).recordSuccess("1.1.1.1");
            verify(session).setAttribute(eq("loginUser"), any(SessionUser.class));
            verify(sessionRegistry).register(eq(42L), eq(session));
        }

        @Test
        void plainTextLegacyPassword_isRehashedOnSuccess() {
            User u = new User();
            u.setId(1L); u.setUsername("legacy"); u.setPassword("plainpass");
            u.setNickname("n"); u.setChannelName("c"); u.setRole("USER");

            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userRepository.findByUsername("legacy")).thenReturn(Optional.of(u));
            when(passwordEncoder.encode("plainpass")).thenReturn("$2a$new");
            when(jwtUtil.generateToken(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn("t");
            when(refreshTokenService.createRefreshToken(anyLong())).thenReturn("rt");

            authService.login(loginReq("legacy", "plainpass"), "1.1.1.1", session);

            verify(passwordEncoder).encode("plainpass");
            verify(userRepository).save(u);
            assertThat(u.getPassword()).isEqualTo("$2a$new");
        }
    }

    @Nested
    class Me {

        @Test
        void nullSessionUser_returnsNotLoggedIn() {
            MeResponse response = authService.getMe(null);
            assertThat(response.isLoggedIn()).isFalse();
            assertThat(response.getUser()).isNull();
            verifyNoInteractions(subscriptionRepository);
        }

        @Test
        void loggedIn_returnsUserWithSubscriberCount() {
            SessionUser user = new SessionUser(5L, "u", "n", "e", "c", "img", "USER");
            when(subscriptionRepository.countByChannelOwnerId(5L)).thenReturn(42L);

            MeResponse response = authService.getMe(user);

            assertThat(response.isLoggedIn()).isTrue();
            assertThat(response.getUser().getSubscriberCount()).isEqualTo(42L);
        }
    }

    @Nested
    class ForgotAndReset {

        @Test
        void forgotPassword_emptyFields_throwsBadRequest() {
            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setUsername(""); req.setEmail("");
            assertThatThrownBy(() -> authService.forgotPassword(req))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void forgotPassword_unknownUser_throwsBadRequest() {
            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setUsername("nobody"); req.setEmail("e@e.com");
            when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> authService.forgotPassword(req))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void forgotPassword_emailMismatch_throwsBadRequest() {
            User u = new User();
            u.setId(1L); u.setUsername("alice"); u.setEmail("real@e.com"); u.setNickname("A");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setUsername("alice"); req.setEmail("fake@e.com");
            assertThatThrownBy(() -> authService.forgotPassword(req))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void forgotPassword_validRequest_sendsEmail() {
            User u = new User();
            u.setId(1L); u.setUsername("alice"); u.setEmail("real@e.com"); u.setNickname("A");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
            when(resetTokenStore.create(1L)).thenReturn("token-abc");

            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setUsername("alice"); req.setEmail("real@e.com");

            authService.forgotPassword(req);

            verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
        }

        @Test
        void resetPassword_shortPassword_throwsBadRequest() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("tok"); req.setNewPassword("short");
            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void resetPassword_invalidToken_throwsBadRequest() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("badtoken"); req.setNewPassword("newpass12");
            when(resetTokenStore.validate("badtoken")).thenReturn(null);
            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void resetPassword_valid_updatesPasswordAndRemovesToken() {
            User u = new User();
            u.setId(7L); u.setUsername("alice"); u.setPassword("$2a$old"); u.setNickname("A");

            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("goodtoken"); req.setNewPassword("newpass12");
            when(resetTokenStore.validate("goodtoken")).thenReturn(7L);
            when(userRepository.findById(7L)).thenReturn(Optional.of(u));
            when(passwordEncoder.encode("newpass12")).thenReturn("$2a$new");

            authService.resetPassword(req);

            assertThat(u.getPassword()).isEqualTo("$2a$new");
            verify(userRepository).save(u);
            verify(resetTokenStore).remove("goodtoken");
        }
    }

    @Nested
    class LogoutAndRefresh {

        @Test
        void logout_nullUser_stillRemovesRefreshToken() {
            authService.logout(null, "rt");
            verify(refreshTokenService).removeRefreshToken("rt");
            verifyNoInteractions(sessionRegistry);
        }

        @Test
        void logout_withUser_removesSession() {
            SessionUser user = new SessionUser(9L, "u", "n", "e", "c", "img", "USER");
            authService.logout(user, null);
            verify(sessionRegistry).remove(9L);
            verify(refreshTokenService, never()).removeRefreshToken(anyString());
        }

        @Test
        void refresh_blankToken_throwsBadRequest() {
            assertThatThrownBy(() -> authService.refresh(""))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void refresh_invalidToken_throwsUnauthorized() {
            when(refreshTokenService.validateRefreshToken("rt")).thenReturn(null);
            assertThatThrownBy(() -> authService.refresh("rt"))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void refresh_valid_returnsNewTokens() {
            User u = new User();
            u.setId(1L); u.setUsername("alice"); u.setNickname("A"); u.setRole("USER");
            when(refreshTokenService.validateRefreshToken("rt")).thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(u));
            when(jwtUtil.generateToken(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn("new-access");
            when(refreshTokenService.createRefreshToken(1L)).thenReturn("new-refresh");

            TokenRefreshResponse response = authService.refresh("rt");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getAccessToken()).isEqualTo("new-access");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        }
    }
}
