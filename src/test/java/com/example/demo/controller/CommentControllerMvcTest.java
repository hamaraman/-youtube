package com.example.demo.controller;

import com.example.demo.config.JwtFilter;
import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.VideoAccessFilter;
import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.CommentController.CommentItem;
import com.example.demo.entity.Comment;
import com.example.demo.service.CommentService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CommentController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, VideoAccessFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class CommentControllerMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CommentService commentService;
    @MockitoBean private LoginUserResolver loginUserResolver;

    private SessionUser me() {
        return new SessionUser(10L, "user", "닉", "e@e.com", "채널", "img", "USER");
    }

    private CommentItem commentItem(Long id, String text) {
        Comment c = new Comment();
        c.setId(id); c.setVideoId(1L); c.setUserId(10L);
        c.setAuthor("닉"); c.setText(text);
        return CommentItem.from(c, 10L, 0L, false);
    }

    @Test
    void getComments_returnsPayload() throws Exception {
        when(loginUserResolver.getUserId(any())).thenReturn(null);
        when(commentService.getComments(eq(1L), eq(0), eq(10), eq(null)))
                .thenReturn(Map.of(
                        "comments", List.of(),
                        "total", 0L,
                        "page", 0,
                        "hasMore", false));

        mockMvc.perform(get("/api/videos/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void getComments_videoNotFound_returns404() throws Exception {
        when(commentService.getComments(eq(1L), eq(0), eq(10), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/videos/1/comments"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createComment_notLoggedIn_returns401WithMessage() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(null);
        when(commentService.createComment(eq(1L), eq("hi"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        mockMvc.perform(post("/api/videos/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "hi"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createComment_valid_returnsCreated() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(commentService.createComment(eq(1L), eq("hi"), any(SessionUser.class)))
                .thenReturn(commentItem(500L, "hi"));

        mockMvc.perform(post("/api/videos/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "hi"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.comment.id").value(500))
                .andExpect(jsonPath("$.comment.text").value("hi"));
    }

    @Test
    void createReply_valid_returnsCreated() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(commentService.createReply(eq(100L), eq("답글"), any(SessionUser.class)))
                .thenReturn(commentItem(600L, "답글"));

        mockMvc.perform(post("/api/comments/100/replies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "답글"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment.id").value(600));
    }

    @Test
    void toggleCommentLike_success_returnsCounts() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(commentService.toggleCommentLike(eq(500L), any(SessionUser.class)))
                .thenReturn(Map.of("liked", true, "likeCount", 3L));

        mockMvc.perform(post("/api/comments/500/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(3));
    }

    @Test
    void toggleCommentLike_notFound_returns404() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(commentService.toggleCommentLike(eq(500L), any(SessionUser.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));

        mockMvc.perform(post("/api/comments/500/like"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateComment_notOwner_returns403WithMessage() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(commentService.updateComment(eq(500L), eq("new"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 댓글만 수정할 수 있습니다."));

        mockMvc.perform(put("/api/comments/500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "new"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void updateComment_valid_returnsUpdatedItem() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        when(commentService.updateComment(eq(500L), eq("new"), any(SessionUser.class)))
                .thenReturn(commentItem(500L, "new"));

        mockMvc.perform(put("/api/comments/500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "new"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment.text").value("new"));
    }

    @Test
    void deleteComment_success_returnsMessage() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());

        mockMvc.perform(delete("/api/comments/500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(commentService).deleteComment(eq(500L), any(SessionUser.class));
    }

    @Test
    void deleteComment_notFound_returns404() throws Exception {
        when(loginUserResolver.getUser(any())).thenReturn(me());
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."))
                .when(commentService).deleteComment(eq(500L), any(SessionUser.class));

        mockMvc.perform(delete("/api/comments/500"))
                .andExpect(status().isNotFound());
    }
}
