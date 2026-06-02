package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.PasswordChangeCodeStore;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final LoginUserResolver loginUserResolver;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final PasswordChangeCodeStore codeStore;

    @Value("${spring.mail.username}")
    private String mailSenderUsername;

    @Value("${app.base-url}")
    private String baseUrl;

    public ProfileController(UserRepository userRepository, VideoRepository videoRepository,
                             LoginUserResolver loginUserResolver, PasswordEncoder passwordEncoder,
                             JavaMailSender mailSender, PasswordChangeCodeStore codeStore) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.loginUserResolver = loginUserResolver;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.codeStore = codeStore;
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
                        safeProfileImage(user),
                        user.getBannerImage() == null ? "" : user.getBannerImage(),
                        user.getBio() == null ? "" : user.getBio()
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
        String bannerImage = value(request.getBannerImage());
        String bio = value(request.getBio());

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
        user.setBannerImage(bannerImage.isBlank() ? null : bannerImage);
        user.setBio(bio.isBlank() ? null : bio);

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
                safeProfileImage(user),
                user.getRole()
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
                        safeProfileImage(user),
                        user.getBannerImage() == null ? "" : user.getBannerImage(),
                        user.getBio() == null ? "" : user.getBio()
                )
        ));
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

        Optional<User> optionalUser = userRepository.findById(sessionUser.getId());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = optionalUser.get();

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "이메일이 등록되어 있지 않아. 먼저 프로필에서 이메일을 등록해줘."));
        }

        String current = request.getCurrentPassword() == null ? "" : request.getCurrentPassword();
        String next = request.getNewPassword() == null ? "" : request.getNewPassword();
        String confirm = request.getConfirmPassword() == null ? "" : request.getConfirmPassword();

        if (current.isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "현재 비밀번호를 입력해줘."));
        }
        if (!passwordEncoder.matches(current, user.getPassword())) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "현재 비밀번호가 틀렸어."));
        }
        if (next.length() < 4) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "새 비밀번호는 4자 이상이어야 해."));
        }
        if (!next.equals(confirm)) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "새 비밀번호 확인이 일치하지 않아."));
        }

        String hashedNew = passwordEncoder.encode(next);
        String code = codeStore.create(user.getId(), hashedNew);

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(mailSenderUsername);
            mail.setTo(user.getEmail());
            mail.setSubject("[MyTube] 비밀번호 변경 인증 코드");
            mail.setText(
                "안녕하세요, " + user.getNickname() + "님!\n\n" +
                "비밀번호 변경 인증 코드: " + code + "\n\n" +
                "이 코드는 5분간 유효합니다.\n" +
                "본인이 요청하지 않은 경우 이 이메일을 무시해주세요."
            );
            mailSender.send(mail);
            log.info("비밀번호 변경 인증 코드 발송: {}", user.getEmail());
        } catch (Exception e) {
            log.error("인증 코드 이메일 발송 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(new SimpleResponse(false, "이메일 발송에 실패했어. 잠시 후 다시 시도해줘."));
        }

        return ResponseEntity.ok(new SimpleResponse(true, "인증 코드를 이메일로 보냈어. 5분 내에 입력해줘."));
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

        String code = request.getVerificationCode() == null ? "" : request.getVerificationCode().trim();
        if (code.isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "인증 코드를 입력해줘."));
        }

        String hashedNew = codeStore.validateAndGetHash(sessionUser.getId(), code);
        if (hashedNew == null) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "인증 코드가 틀렸거나 만료되었어."));
        }

        Optional<User> optionalUser = userRepository.findById(sessionUser.getId());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = optionalUser.get();
        user.setPassword(hashedNew);
        userRepository.save(user);
        codeStore.remove(sessionUser.getId());

        log.info("비밀번호 변경 완료: userId={}", sessionUser.getId());
        return ResponseEntity.ok(new SimpleResponse(true, "비밀번호가 변경되었어."));
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