package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final LoginUserResolver loginUserResolver;

    public ProfileController(UserRepository userRepository, VideoRepository videoRepository,
                             LoginUserResolver loginUserResolver) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.loginUserResolver = loginUserResolver;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(HttpSession session) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        Optional<User> optionalUser = userRepository.findById(sessionUser.getId());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = optionalUser.get();

        return ResponseEntity.ok(new ProfileResponse(
                true,
                new ProfileUser(
                        user.getId(),
                        user.getUsername(),
                        user.getNickname(),
                        user.getEmail(),
                        safeChannelName(user),
                        safeProfileImage(user)
                )
        ));
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

        Optional<User> optionalUser = userRepository.findById(sessionUser.getId());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = optionalUser.get();

        String nickname = value(request.getNickname());
        String email = value(request.getEmail());
        String channelName = value(request.getChannelName());
        String profileImage = value(request.getProfileImage());

        if (nickname.isBlank()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "닉네임을 입력해줘."));
        }

        if (channelName.isBlank()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "채널명을 입력해줘."));
        }

        if (!email.isBlank()) {
            boolean emailUsedByOther = userRepository.existsByEmail(email)
                    && userRepository.findAll().stream()
                    .anyMatch(u -> email.equals(u.getEmail()) && !u.getId().equals(user.getId()));

            if (emailUsedByOther) {
                return ResponseEntity.badRequest().body(new SimpleResponse(false, "이미 사용 중인 이메일입니다."));
            }
        }

        user.setNickname(nickname);
        user.setEmail(email.isBlank() ? null : email);
        user.setChannelName(channelName);
        user.setProfileImage(profileImage.isBlank() ? null : profileImage);

        userRepository.save(user);

        List<Video> myVideos = videoRepository.findAll().stream()
                .filter(video -> user.getId().equals(video.getOwnerId()))
                .toList();

        for (Video video : myVideos) {
            video.setChannel(channelName);
            video.setAvatar(profileImage.isBlank() ? "" : profileImage);
        }
        videoRepository.saveAll(myVideos);

        AuthController.SessionUser updatedSessionUser = new AuthController.SessionUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                safeChannelName(user),
                safeProfileImage(user)
        );
        session.setAttribute("loginUser", updatedSessionUser);

        return ResponseEntity.ok(new ProfileResponse(
                true,
                new ProfileUser(
                        user.getId(),
                        user.getUsername(),
                        user.getNickname(),
                        user.getEmail(),
                        safeChannelName(user),
                        safeProfileImage(user)
                )
        ));
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeChannelName(User user) {
        if (user.getChannelName() != null && !user.getChannelName().isBlank()) {
            return user.getChannelName();
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private String safeProfileImage(User user) {
        return user.getProfileImage() == null ? "" : user.getProfileImage();
    }

    public static class ProfileUpdateRequest {
        private String nickname;
        private String email;
        private String channelName;
        private String profileImage;

        public String getNickname() { return nickname; }
        public String getEmail() { return email; }
        public String getChannelName() { return channelName; }
        public String getProfileImage() { return profileImage; }

        public void setNickname(String nickname) { this.nickname = nickname; }
        public void setEmail(String email) { this.email = email; }
        public void setChannelName(String channelName) { this.channelName = channelName; }
        public void setProfileImage(String profileImage) { this.profileImage = profileImage; }
    }

    public static class ProfileUser {
        private Long id;
        private String username;
        private String nickname;
        private String email;
        private String channelName;
        private String profileImage;

        public ProfileUser(Long id, String username, String nickname, String email, String channelName, String profileImage) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
            this.email = email;
            this.channelName = channelName;
            this.profileImage = profileImage;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getNickname() { return nickname; }
        public String getEmail() { return email; }
        public String getChannelName() { return channelName; }
        public String getProfileImage() { return profileImage; }
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
}