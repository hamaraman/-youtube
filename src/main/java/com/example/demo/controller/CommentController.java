package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.dto.CommentRequest;
import com.example.demo.entity.Comment;
import com.example.demo.service.CommentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService commentService;
    private final LoginUserResolver loginUserResolver;

    public CommentController(CommentService commentService, LoginUserResolver loginUserResolver) {
        this.commentService = commentService;
        this.loginUserResolver = loginUserResolver;
    }

    @GetMapping("/videos/{id}/comments")
    public ResponseEntity<?> getComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpSession session) {
        try {
            Long loginUserId = loginUserResolver.getUserId(session);
            Map<String, Object> result = commentService.getComments(id, page, size, loginUserId);
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    @PostMapping("/videos/{id}/comments")
    public ResponseEntity<?> createComment(
            @PathVariable Long id,
            @RequestBody CommentRequest request,
            HttpSession session
    ) {
        try {
            AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
            CommentItem item = commentService.createComment(id, request.getContent(), sessionUser);
            return ResponseEntity.ok(new CommentCreateResponse(true, item));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    @PostMapping("/comments/{commentId}/replies")
    public ResponseEntity<?> createReply(
            @PathVariable Long commentId,
            @RequestBody CommentRequest request,
            HttpSession session
    ) {
        try {
            AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
            CommentItem item = commentService.createReply(commentId, request.getContent(), sessionUser);
            return ResponseEntity.ok(new CommentCreateResponse(true, item));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<?> toggleCommentLike(@PathVariable Long commentId, HttpSession session) {
        try {
            AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
            return ResponseEntity.ok(commentService.toggleCommentLike(commentId, sessionUser));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<?> updateComment(
            @PathVariable Long commentId,
            @RequestBody CommentRequest request,
            HttpSession session
    ) {
        try {
            AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
            CommentItem item = commentService.updateComment(commentId, request.getContent(), sessionUser);
            return ResponseEntity.ok(new CommentCreateResponse(true, item));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(
            @PathVariable Long commentId,
            HttpSession session
    ) {
        try {
            AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
            commentService.deleteComment(commentId, sessionUser);
            return ResponseEntity.ok(new SimpleResponse(true, "댓글이 삭제되었습니다."));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(e.getStatusCode()).body(new SimpleResponse(false, e.getReason()));
        }
    }

    public static class CommentItem {
        private Long id;
        private Long videoId;
        private Long userId;
        private Long parentId;
        private String author;
        private String text;
        private String time;
        private boolean isMine;
        private long likeCount;
        private boolean isLiked;
        public List<CommentItem> replies = new ArrayList<>();

        public static CommentItem from(Comment comment, Long loginUserId, long likeCount, boolean isLiked) {
            CommentItem item = new CommentItem();
            item.id = comment.getId();
            item.videoId = comment.getVideoId();
            item.userId = comment.getUserId();
            item.parentId = comment.getParentId();
            item.author = comment.getAuthor();
            item.text = comment.getText();
            item.time = comment.getTime();
            item.isMine = loginUserId != null && loginUserId.equals(comment.getUserId());
            item.likeCount = likeCount;
            item.isLiked = isLiked;
            return item;
        }

        public Long getId() { return id; }
        public Long getVideoId() { return videoId; }
        public Long getUserId() { return userId; }
        public Long getParentId() { return parentId; }
        public List<CommentItem> getReplies() { return replies; }
        public String getAuthor() { return author; }
        public String getText() { return text; }
        public String getTime() { return time; }
        public boolean isMine() { return isMine; }
        public long getLikeCount() { return likeCount; }
        public boolean isLiked() { return isLiked; }
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

    public static class CommentCreateResponse {
        private boolean success;
        private CommentItem comment;

        public CommentCreateResponse(boolean success, CommentItem comment) {
            this.success = success;
            this.comment = comment;
        }

        public boolean isSuccess() { return success; }
        public CommentItem getComment() { return comment; }
    }
}
