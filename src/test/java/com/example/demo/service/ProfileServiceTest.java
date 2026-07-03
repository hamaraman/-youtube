package com.example.demo.service;

import com.example.demo.config.PasswordChangeCodeStore;
import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.ProfileController.PasswordChangeRequest;
import com.example.demo.controller.ProfileController.ProfileUpdateRequest;
import com.example.demo.controller.ProfileController.ProfileUser;
import com.example.demo.controller.ProfileController.SendCodeRequest;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JavaMailSender mailSender;
    @Mock private PasswordChangeCodeStore codeStore;

    @InjectMocks private ProfileService profileService;

    @BeforeEach
    void injectValues() {
        ReflectionTestUtils.setField(profileService, "mailSenderUsername", "sender@mytube.com");
    }

    private User user(Long id) {
        User u = new User();
        u.setId(id); u.setUsername("alice"); u.setNickname("앨리스");
        u.setEmail("alice@e.com"); u.setChannelName("Alice TV");
        u.setProfileImage("img.png"); u.setBannerImage("banner.png"); u.setBio("bio");
        u.setPassword("$2a$hashed"); u.setRole("USER");
        return u;
    }

    private ProfileUpdateRequest updateReq(String nick, String email, String channel) {
        ProfileUpdateRequest r = new ProfileUpdateRequest();
        r.setNickname(nick); r.setEmail(email); r.setChannelName(channel);
        r.setProfileImage("img.png"); r.setBannerImage(""); r.setBio("");
        return r;
    }

    @Nested
    class GetProfile {

        @Test
        void missing_throwsNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> profileService.getProfile(1L))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void existing_returnsProfileUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));

            ProfileUser p = profileService.getProfile(1L);

            assertThat(p.getUsername()).isEqualTo("alice");
            assertThat(p.getChannelName()).isEqualTo("Alice TV");
            assertThat(p.getBannerImage()).isEqualTo("banner.png");
        }

        @Test
        void missingChannelName_fallsBackToNickname() {
            User u = user(1L);
            u.setChannelName("");
            when(userRepository.findById(1L)).thenReturn(Optional.of(u));

            ProfileUser p = profileService.getProfile(1L);

            assertThat(p.getChannelName()).isEqualTo("앨리스");
        }

        @Test
        void missingChannelAndNickname_fallsBackToUsername() {
            User u = user(1L);
            u.setChannelName(""); u.setNickname("");
            when(userRepository.findById(1L)).thenReturn(Optional.of(u));

            ProfileUser p = profileService.getProfile(1L);

            assertThat(p.getChannelName()).isEqualTo("alice");
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        void missing_throwsNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> profileService.updateProfile(1L, updateReq("n", "e@e.com", "ch")))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void blankNickname_throwsBadRequest() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
            assertThatThrownBy(() -> profileService.updateProfile(1L, updateReq("  ", "e@e.com", "ch")))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void blankChannelName_throwsBadRequest() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
            assertThatThrownBy(() -> profileService.updateProfile(1L, updateReq("nick", "e@e.com", " ")))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void emailUsedByOther_throwsBadRequest() {
            User me = user(1L); me.setEmail("old@e.com");
            User other = user(2L); other.setEmail("taken@e.com");
            when(userRepository.findById(1L)).thenReturn(Optional.of(me));
            when(userRepository.existsByEmail("taken@e.com")).thenReturn(true);
            when(userRepository.findAll()).thenReturn(List.of(me, other));

            assertThatThrownBy(() -> profileService.updateProfile(1L,
                    updateReq("nick", "taken@e.com", "ch")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("이메일");
        }

        @Test
        void sameEmailOfSelf_isAllowed() {
            User me = user(1L); me.setEmail("mine@e.com");
            when(userRepository.findById(1L)).thenReturn(Optional.of(me));
            when(userRepository.existsByEmail("mine@e.com")).thenReturn(true);
            when(userRepository.findAll()).thenReturn(List.of(me));
            when(videoRepository.findAll()).thenReturn(List.of());

            SessionUser result = profileService.updateProfile(1L,
                    updateReq("nick", "mine@e.com", "ch"));

            assertThat(result.getEmail()).isEqualTo("mine@e.com");
        }

        @Test
        void valid_updatesUserAndPropagatesToVideos() {
            User me = user(1L);
            Video ownedVideo = new Video();
            ownedVideo.setId(10L); ownedVideo.setOwnerId(1L); ownedVideo.setChannel("old ch");
            Video othersVideo = new Video();
            othersVideo.setId(20L); othersVideo.setOwnerId(2L); othersVideo.setChannel("other");

            when(userRepository.findById(1L)).thenReturn(Optional.of(me));
            when(videoRepository.findAll()).thenReturn(List.of(ownedVideo, othersVideo));

            profileService.updateProfile(1L, updateReq("새닉", "new@e.com", "새채널"));

            assertThat(me.getNickname()).isEqualTo("새닉");
            assertThat(me.getEmail()).isEqualTo("new@e.com");
            assertThat(me.getChannelName()).isEqualTo("새채널");
            verify(userRepository).save(me);

            ArgumentCaptor<List<Video>> captor = ArgumentCaptor.forClass(List.class);
            verify(videoRepository).saveAll(captor.capture());
            List<Video> updated = captor.getValue();
            assertThat(updated).extracting(Video::getId).containsExactly(10L);
            assertThat(updated.get(0).getChannel()).isEqualTo("새채널");
        }

        @Test
        void emptyEmail_setsNull() {
            User me = user(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(me));
            when(videoRepository.findAll()).thenReturn(List.of());

            profileService.updateProfile(1L, updateReq("nick", "  ", "ch"));

            assertThat(me.getEmail()).isNull();
        }
    }

    @Nested
    class SendPasswordChangeCode {

        private SendCodeRequest req(String cur, String nxt, String conf) {
            SendCodeRequest r = new SendCodeRequest();
            r.setCurrentPassword(cur); r.setNewPassword(nxt); r.setConfirmPassword(conf);
            return r;
        }

        @Test
        void missingUser_throwsNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> profileService.sendPasswordChangeCode(1L,
                    req("cur", "newpass12", "newpass12")))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void noEmail_throwsBadRequest() {
            User u = user(1L); u.setEmail(null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(u));

            assertThatThrownBy(() -> profileService.sendPasswordChangeCode(1L,
                    req("cur", "newpass12", "newpass12")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("이메일");
        }

        @Test
        void emptyCurrentPassword_throwsBadRequest() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));

            assertThatThrownBy(() -> profileService.sendPasswordChangeCode(1L,
                    req("", "newpass12", "newpass12")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("현재 비밀번호");
        }

        @Test
        void wrongCurrentPassword_throwsBadRequest() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
            when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

            assertThatThrownBy(() -> profileService.sendPasswordChangeCode(1L,
                    req("wrong", "newpass12", "newpass12")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("틀렸어");
        }

        @Test
        void shortNewPassword_throwsBadRequest() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
            when(passwordEncoder.matches("cur", "$2a$hashed")).thenReturn(true);

            assertThatThrownBy(() -> profileService.sendPasswordChangeCode(1L,
                    req("cur", "short", "short")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("8자");
        }

        @Test
        void confirmMismatch_throwsBadRequest() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
            when(passwordEncoder.matches("cur", "$2a$hashed")).thenReturn(true);

            assertThatThrownBy(() -> profileService.sendPasswordChangeCode(1L,
                    req("cur", "newpass12", "different")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("일치");
        }

        @Test
        void valid_encodesNewPasswordAndSendsMail() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
            when(passwordEncoder.matches("cur", "$2a$hashed")).thenReturn(true);
            when(passwordEncoder.encode("newpass12")).thenReturn("$2a$new");
            when(codeStore.create(1L, "$2a$new")).thenReturn("123456");

            profileService.sendPasswordChangeCode(1L, req("cur", "newpass12", "newpass12"));

            verify(codeStore).create(1L, "$2a$new");
            verify(mailSender).send(any(SimpleMailMessage.class));
        }
    }

    @Nested
    class ChangePassword {

        private PasswordChangeRequest req(String code) {
            PasswordChangeRequest r = new PasswordChangeRequest();
            r.setVerificationCode(code);
            return r;
        }

        @Test
        void emptyCode_throwsBadRequest() {
            assertThatThrownBy(() -> profileService.changePassword(1L, req("  ")))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void invalidCode_throwsBadRequest() {
            when(codeStore.validateAndGetHash(1L, "wrong")).thenReturn(null);
            assertThatThrownBy(() -> profileService.changePassword(1L, req("wrong")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("만료");
        }

        @Test
        void valid_updatesPasswordAndRemovesCode() {
            User u = user(1L);
            when(codeStore.validateAndGetHash(1L, "123456")).thenReturn("$2a$new");
            when(userRepository.findById(1L)).thenReturn(Optional.of(u));

            profileService.changePassword(1L, req("123456"));

            assertThat(u.getPassword()).isEqualTo("$2a$new");
            verify(userRepository).save(u);
            verify(codeStore).remove(1L);
        }
    }
}
