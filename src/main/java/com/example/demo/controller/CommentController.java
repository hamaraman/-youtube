package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.dto.CommentRequest;
import com.example.demo.entity.Comment;
import com.example.demo.entity.Video;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentRepository commentRepository;
    private final VideoRepository videoRepository;
    private final LoginUserResolver loginUserResolver;

    public CommentController(CommentRepository commentRepository, VideoRepository videoRepository,
                             LoginUserResolver loginUserResolver) {
        this.commentRepository = commentRepository;
        this.videoRepository = videoRepository;
        this.loginUserResolver = loginUserResolver;
    }

    @GetMapping("/videos/{id}/comments")
    public ResponseEntity<?> getComments(@PathVariable Long id, HttpSession session) {
        Optional<Video> optionalVideo = videoRepository.findById(id);

        if (optionalVideo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        final Long finalLoginUserId = loginUserResolver.getUserId(session);

        List<CommentItem> comments = commentRepository.findByVideoIdAndParentIdIsNullOrderByIdDesc(id)
                .stream()
                .map(comment -> {
                    CommentItem item = CommentItem.from(comment, finalLoginUserId);
                    item.replies = commentRepository.findByParentIdOrderByIdAsc(comment.getId())
                            .stream()
                            .map(r -> CommentItem.from(r, finalLoginUserId))
                            .collect(Collectors.toList());
                    return item;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(comments);
    }

    @PostMapping("/videos/{id}/comments")
    public ResponseEntity<?> createComment(
            @PathVariable Long id,
            @RequestBody CommentRequest request,
            HttpSession session
    ) {
        Optional<Video> optionalVideo = videoRepository.findById(id);

        if (optionalVideo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "댓글 내용을 입력해줘."));
        }

        Comment comment = new Comment();
        comment.setVideoId(id);
        comment.setUserId(sessionUser.getId());
        comment.setAuthor(
                sessionUser.getNickname() != null && !sessionUser.getNickname().isBlank()
                        ? sessionUser.getNickname()
                        : sessionUser.getUsername()
        );
        comment.setText(content);
        comment.setTime("방금 전");

        Comment saved = commentRepository.save(comment);

        return ResponseEntity.ok(new CommentCreateResponse(
                true,
                CommentItem.from(saved, sessionUser.getId())
        ));
    }

    @PostMapping("/comments/{commentId}/replies")
    public ResponseEntity<?> createReply(
            @PathVariable Long commentId,
            @RequestBody CommentRequest request,
            HttpSession session
    ) {
        Optional<Comment> parent = commentRepository.findById(commentId);
        if (parent.isEmpty() || parent.get().getParentId() != null) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "유효하지 않은 댓글입니다."));
        }

        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "답글 내용을 입력해줘."));
        }

        Comment reply = new Comment();
        reply.setVideoId(parent.get().getVideoId());
        reply.setUserId(sessionUser.getId());
        reply.setAuthor(sessionUser.getNickname() != null && !sessionUser.getNickname().isBlank()
                ? sessionUser.getNickname() : sessionUser.getUsername());
        reply.setText(content);
        reply.setTime("방금 전");
        reply.setParentId(commentId);

        Comment saved = commentRepository.save(reply);
        return ResponseEntity.ok(new CommentCreateResponse(true, CommentItem.from(saved, sessionUser.getId())));
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<?> updateComment(
            @PathVariable Long commentId,
            @RequestBody CommentRequest request,
            HttpSession session
    ) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        Optional<Comment> optionalComment = commentRepository.findById(commentId);
        if (optionalComment.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Comment comment = optionalComment.get();

        if (!sessionUser.getId().equals(comment.getUserId())) {
            return ResponseEntity.status(403).body(new SimpleResponse(false, "본인 댓글만 수정할 수 있습니다."));
        }

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, "수정할 댓글 내용을 입력해줘."));
        }

        comment.setText(content);
        Comment saved = commentRepository.save(comment);

        return ResponseEntity.ok(new CommentCreateResponse(
                true,
                CommentItem.from(saved, sessionUser.getId())
        ));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(
            @PathVariable Long commentId,
            HttpSession session
    ) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));
        }

        Optional<Comment> optionalComment = commentRepository.findById(commentId);
        if (optionalComment.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Comment comment = optionalComment.get();

        if (!sessionUser.getId().equals(comment.getUserId())) {
            return ResponseEntity.status(403).body(new SimpleResponse(false, "본인 댓글만 삭제할 수 있습니다."));
        }

        if (comment.getParentId() == null) {
            commentRepository.deleteByParentId(commentId);
        }
        commentRepository.delete(comment);

        return ResponseEntity.ok(new SimpleResponse(true, "댓글이 삭제되었습니다."));
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
        public List<CommentItem> replies = new java.util.ArrayList<>();

        public static CommentItem from(Comment comment, Long loginUserId) {
            CommentItem item = new CommentItem();
            item.id = comment.getId();
            item.videoId = comment.getVideoId();
            item.userId = comment.getUserId();
            item.parentId = comment.getParentId();
            item.author = comment.getAuthor();
            item.text = comment.getText();
            item.time = comment.getTime();
            item.isMine = loginUserId != null && loginUserId.equals(comment.getUserId());
            return item;
        }

        public Long getId() {
            return id;
        }

        public Long getVideoId() {
            return videoId;
        }

        public Long getUserId() {
            return userId;
        }

        public Long getParentId() {
            return parentId;
        }

        public List<CommentItem> getReplies() {
            return replies;
        }

        public String getAuthor() {
            return author;
        }

        public String getText() {
            return text;
        }

        public String getTime() {
            return time;
        }

        public boolean isMine() {
            return isMine;
        }
    }

    public static class SimpleResponse {
        private boolean success;
        private String message;

        public SimpleResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class CommentCreateResponse {
        private boolean success;
        private CommentItem comment;

        public CommentCreateResponse(boolean success, CommentItem comment) {
            this.success = success;
            this.comment = comment;
        }

        public boolean isSuccess() {
            return success;
        }

        public CommentItem getComment() {
            return comment;
        }
    }
}