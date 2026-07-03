package com.example.demo.integration;

import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.SubscriptionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoLikeRepository;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FullFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VideoRepository videoRepository;
    @Autowired private VideoLikeRepository videoLikeRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private NotificationRepository notificationRepository;

    @BeforeEach
    void cleanup() {
        notificationRepository.deleteAll();
        commentRepository.deleteAll();
        videoLikeRepository.deleteAll();
        subscriptionRepository.deleteAll();
        videoRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getUsername()))
                .forEach(u -> userRepository.deleteById(u.getId()));
    }

    @Test
    void signupThenLogin_persistsUserAndReturnsSessionAndToken() throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice42",
                                "password", "pass1234",
                                "nickname", "앨리스",
                                "email", "alice@e.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(userRepository.findByUsername("alice42")).isPresent();

        MvcResult loginResult = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice42",
                                "password", "pass1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.user.username").value("alice42"))
                .andReturn();

        HttpSession session = loginResult.getRequest().getSession();
        assertThat(session).isNotNull();
        assertThat(session.getAttribute("loginUser")).isInstanceOf(SessionUser.class);
    }

    @Test
    void signup_duplicate_returns400() throws Exception {
        signupAndLogin("dupuser", "dupemail@e.com");

        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "dupuser",
                                "password", "pass1234",
                                "nickname", "another"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_wrongPassword_returns400() throws Exception {
        signupAndLogin("bob42", "bob@e.com");

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "bob42",
                                "password", "WRONGPASSWORD"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void me_afterLogin_returnsUserWithSubscriberCount() throws Exception {
        HttpSession session = signupAndLogin("carol42", "carol@e.com");

        mockMvc.perform(get("/api/me").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.user.username").value("carol42"))
                .andExpect(jsonPath("$.user.subscriberCount").value(0));
    }

    @Test
    void toggleLike_incrementsCountInDatabase() throws Exception {
        HttpSession aliceSession = signupAndLogin("alice42", "alice@e.com");
        User bob = createUser("bob42", "bob@e.com");
        Video video = createPublicVideo(bob.getId(), "Bob의 영상");

        mockMvc.perform(post("/api/videos/" + video.getId() + "/like")
                        .session((org.springframework.mock.web.MockHttpSession) aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        assertThat(videoLikeRepository.countByVideoId(video.getId())).isEqualTo(1L);
        assertThat(notificationRepository.findByReceiverIdOrderByCreatedAtDesc(bob.getId()))
                .extracting(n -> n.getType()).contains("LIKE");
    }

    @Test
    void toggleLike_twice_undoesLike() throws Exception {
        HttpSession session = signupAndLogin("alice42", "alice@e.com");
        User bob = createUser("bob42", "bob@e.com");
        Video video = createPublicVideo(bob.getId(), "Bob의 영상");

        mockMvc.perform(post("/api/videos/" + video.getId() + "/like")
                        .session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(jsonPath("$.liked").value(true));

        mockMvc.perform(post("/api/videos/" + video.getId() + "/like")
                        .session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));

        assertThat(videoLikeRepository.countByVideoId(video.getId())).isZero();
    }

    @Test
    void subscribeThenComment_endToEndFlow() throws Exception {
        HttpSession aliceSession = signupAndLogin("alice42", "alice@e.com");
        User bob = createUser("bob42", "bob@e.com");
        Video video = createPublicVideo(bob.getId(), "Bob의 영상");

        // 구독
        mockMvc.perform(post("/api/users/" + bob.getId() + "/subscribe")
                        .session((org.springframework.mock.web.MockHttpSession) aliceSession))
                .andExpect(jsonPath("$.subscribed").value(true))
                .andExpect(jsonPath("$.subscriberCount").value(1));

        assertThat(subscriptionRepository.existsBySubscriberIdAndChannelOwnerId(
                userRepository.findByUsername("alice42").orElseThrow().getId(), bob.getId())).isTrue();

        // 댓글 작성
        mockMvc.perform(post("/api/videos/" + video.getId() + "/comments")
                        .session((org.springframework.mock.web.MockHttpSession) aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "좋은 영상이에요!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.comment.text").value("좋은 영상이에요!"));

        assertThat(commentRepository.countByVideoIdAndParentIdIsNull(video.getId())).isEqualTo(1L);

        // 댓글 목록 조회
        mockMvc.perform(get("/api/videos/" + video.getId() + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.comments[0].text").value("좋은 영상이에요!"));
    }

    @Test
    void selfSubscribe_isRejected() throws Exception {
        HttpSession session = signupAndLogin("alice42", "alice@e.com");
        User alice = userRepository.findByUsername("alice42").orElseThrow();

        mockMvc.perform(post("/api/users/" + alice.getId() + "/subscribe")
                        .session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isBadRequest());

        assertThat(subscriptionRepository.countByChannelOwnerId(alice.getId())).isZero();
    }

    @Test
    void logout_invalidatesSession() throws Exception {
        HttpSession session = signupAndLogin("alice42", "alice@e.com");

        mockMvc.perform(post("/api/logout").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 새 요청은 미로그인 상태여야 함
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(false));
    }

    @Test
    void privateVideo_cannotBeLikedByOthers_when403IsReturned() throws Exception {
        HttpSession aliceSession = signupAndLogin("alice42", "alice@e.com");
        User bob = createUser("bob42", "bob@e.com");
        Video privateVideo = createPrivateVideo(bob.getId(), "Bob의 비공개");

        // GET 상세는 접근 차단
        mockMvc.perform(get("/api/videos/" + privateVideo.getId())
                        .session((org.springframework.mock.web.MockHttpSession) aliceSession))
                .andExpect(status().isForbidden());
    }

    // ---------- 헬퍼 ----------

    private HttpSession signupAndLogin(String username, String email) throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "pass1234",
                                "nickname", "닉_" + username,
                                "email", email))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "pass1234"))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getRequest().getSession();
    }

    private User createUser(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$dummy");
        u.setNickname("닉_" + username);
        u.setEmail(email);
        u.setChannelName("ch_" + username);
        u.setRole("USER");
        return userRepository.save(u);
    }

    private Video createPublicVideo(Long ownerId, String title) {
        Video v = new Video();
        v.setOwnerId(ownerId); v.setTitle(title); v.setChannel("ch");
        v.setThumbnail("th"); v.setDuration("1:00"); v.setVisibility("공개");
        return videoRepository.save(v);
    }

    private Video createPrivateVideo(Long ownerId, String title) {
        Video v = createPublicVideo(ownerId, title);
        v.setVisibility("비공개");
        return videoRepository.save(v);
    }
}
