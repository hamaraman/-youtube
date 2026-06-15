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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final PasswordChangeCodeStore codeStore;

    @Value("${spring.mail.username}")
    private String mailSenderUsername;

    public ProfileService(UserRepository userRepository, VideoRepository videoRepository,
                          PasswordEncoder passwordEncoder, JavaMailSender mailSender,
                          PasswordChangeCodeStore codeStore) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.codeStore = codeStore;
    }

    public ProfileUser getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        return new ProfileUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                safeChannelName(user),
                safeProfileImage(user),
                user.getBannerImage() == null ? "" : user.getBannerImage(),
                user.getBio() == null ? "" : user.getBio()
        );
    }

    public SessionUser updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        String nickname = value(request.getNickname());
        String email = value(request.getEmail());
        String channelName = value(request.getChannelName());
        String profileImage = value(request.getProfileImage());
        String bannerImage = value(request.getBannerImage());
        String bio = value(request.getBio());

        if (nickname.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "닉네임을 입력해줘.");
        }

        if (channelName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "채널명을 입력해줘.");
        }

        if (!email.isBlank()) {
            boolean emailUsedByOther = userRepository.existsByEmail(email)
                    && userRepository.findAll().stream()
                    .anyMatch(u -> email.equals(u.getEmail()) && !u.getId().equals(user.getId()));

            if (emailUsedByOther) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용 중인 이메일입니다.");
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

        return new SessionUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                safeChannelName(user),
                safeProfileImage(user),
                user.getRole()
        );
    }

    public void sendPasswordChangeCode(Long userId, SendCodeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이메일이 등록되어 있지 않아. 먼저 프로필에서 이메일을 등록해줘.");
        }

        String current = request.getCurrentPassword() == null ? "" : request.getCurrentPassword();
        String next = request.getNewPassword() == null ? "" : request.getNewPassword();
        String confirm = request.getConfirmPassword() == null ? "" : request.getConfirmPassword();

        if (current.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호를 입력해줘.");
        }
        if (!passwordEncoder.matches(current, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 틀렸어.");
        }
        if (next.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호는 8자 이상이어야 해.");
        }
        if (!next.equals(confirm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호 확인이 일치하지 않아.");
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이메일 발송에 실패했어. 잠시 후 다시 시도해줘.");
        }
    }

    public void changePassword(Long userId, PasswordChangeRequest request) {
        String code = request.getVerificationCode() == null ? "" : request.getVerificationCode().trim();
        if (code.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 코드를 입력해줘.");
        }

        String hashedNew = codeStore.validateAndGetHash(userId, code);
        if (hashedNew == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 코드가 틀렸거나 만료되었어.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        user.setPassword(hashedNew);
        userRepository.save(user);
        codeStore.remove(userId);

        log.info("비밀번호 변경 완료: userId={}", userId);
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
}
