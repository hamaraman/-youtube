package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.service.ProfileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profileService;
    private final LoginUserResolver loginUserResolver;

    public ProfileController(ProfileService profileService, LoginUserResolver loginUserResolver) {
        this.profileService = profileService;
        this.loginUserResolver = loginUserResolver;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(HttpSession session) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        try {
            ProfileUser profile = profileService.getProfile(sessionUser.getId());
            return ResponseEntity.ok(new ProfileResponse(true, profile));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestBody ProfileUpdateRequest request,
            HttpSession session
    ) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        try {
            AuthController.SessionUser updatedSessionUser = profileService.updateProfile(sessionUser.getId(), request);
            session.setAttribute("loginUser", updatedSessionUser);

            ProfileUser updatedProfile = profileService.getProfile(sessionUser.getId());
            return ResponseEntity.ok(new ProfileResponse(true, updatedProfile));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    @PostMapping("/profile/password/send-code")
    public ResponseEntity<?> sendPasswordChangeCode(
            @RequestBody SendCodeRequest request,
            HttpSession session
    ) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        try {
            profileService.sendPasswordChangeCode(sessionUser.getId(), request);
            return ResponseEntity.ok(new SimpleResponse(true, "인증 코드를 이메일로 보냈어. 5분 내에 입력해줘."));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    @PutMapping("/profile/password")
    public ResponseEntity<?> changePassword(
            @RequestBody PasswordChangeRequest request,
            HttpSession session
    ) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        try {
            profileService.changePassword(sessionUser.getId(), request);
            return ResponseEntity.ok(new SimpleResponse(true, "비밀번호가 변경되었어."));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    public static class ProfileUpdateRequest {
        private String nickname;
        private String email;
        private String channelName;
        private String profileImage;
        private String bannerImage;
        private String bio;

        public String getNickname() { return nickname; }
        public String getEmail() { return email; }
        public String getChannelName() { return channelName; }
        public String getProfileImage() { return profileImage; }
        public String getBannerImage() { return bannerImage; }
        public String getBio() { return bio; }

        public void setNickname(String nickname) { this.nickname = nickname; }
        public void setEmail(String email) { this.email = email; }
        public void setChannelName(String channelName) { this.channelName = channelName; }
        public void setProfileImage(String profileImage) { this.profileImage = profileImage; }
        public void setBannerImage(String bannerImage) { this.bannerImage = bannerImage; }
        public void setBio(String bio) { this.bio = bio; }
    }

    public static class ProfileUser {
        private Long id;
        private String username;
        private String nickname;
        private String email;
        private String channelName;
        private String profileImage;
        private String bannerImage;
        private String bio;

        public ProfileUser(Long id, String username, String nickname, String email, String channelName, String profileImage, String bannerImage, String bio) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
            this.email = email;
            this.channelName = channelName;
            this.profileImage = profileImage;
            this.bannerImage = bannerImage;
            this.bio = bio;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getNickname() { return nickname; }
        public String getEmail() { return email; }
        public String getChannelName() { return channelName; }
        public String getProfileImage() { return profileImage; }
        public String getBannerImage() { return bannerImage; }
        public String getBio() { return bio; }
    }

    public static class ProfileResponse {
        private boolean success;
        private ProfileUser user;

        public ProfileResponse(boolean success, ProfileUser user) {
            this.success = success;
            this.user = user;
        }

        public boolean isSuccess() { return success; }
        public ProfileUser getUser() { return user; }
    }

    public static class SimpleResponse {
        private boolean success;
        private String message;

        public SimpleResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class SendCodeRequest {
        private String currentPassword;
        private String newPassword;
        private String confirmPassword;

        public String getCurrentPassword() { return currentPassword; }
        public String getNewPassword() { return newPassword; }
        public String getConfirmPassword() { return confirmPassword; }

        public void setCurrentPassword(String v) { this.currentPassword = v; }
        public void setNewPassword(String v) { this.newPassword = v; }
        public void setConfirmPassword(String v) { this.confirmPassword = v; }
    }

    public static class PasswordChangeRequest {
        private String verificationCode;

        public String getVerificationCode() { return verificationCode; }
        public void setVerificationCode(String v) { this.verificationCode = v; }
    }
}