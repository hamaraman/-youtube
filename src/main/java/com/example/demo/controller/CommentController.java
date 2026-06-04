package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.config.NotificationService;
import com.example.demo.dto.CommentRequest;
import com.example.demo.entity.Comment;
import com.example.demo.entity.CommentLike;
import com.example.demo.entity.Video;
import com.example.demo.repository.CommentLikeRepository;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.VideoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentRepository commentRepository;
    private final VideoRepository videoRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final LoginUserResolver loginUserResolver;
    private final NotificationService notificationService;

    public CommentController(CommentRepository commentRepository, VideoRepository videoRepository,
                             CommentLikeRepository commentLikeRepository,
                             LoginUserResolver loginUserResolver, NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.videoRepository = videoRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.loginUserResolver = loginUserResolver;
        this.notificationService = notificationService;
    }

    @GetMapping("/videos/{id}/comments")
    public ResponseEntity<?> getComments(@PathVariable Long id, HttpSession session) {
        Optional<Video> optionalVideo = videoRepository.findById(id);
        if (optionalVideo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        final Long finalLoginUserId = loginUserResolver.getUserId(session);

        List<Comment> parentComments = commentRepository.findByVideoIdAndParentIdIsNullOrderByIdDesc(id);
        if (parentComments.isEmpty()) return ResponseEntity.ok(List.of());

        List<Long> parentIds = parentComments.stream().map(Comment::getId).collect(Collectors.toList());
        List<Comment> allReplies = commentRepository.findByParentIdInOrderByIdAsc(parentIds);
        Map<Long, List<Comment>> repliesByParent = allReplies.stream()
                .collect(Collectors.groupingBy(Comment::getParentId));

        List<Long> allCommentIds = new ArrayList<>(parentIds);
        allReplies.stream().map(Comment::getId).forEach(allCommentIds::add);

        Map<Long, Long> likeCounts = toCountMap(commentLikeRepository.countByCommentIdIn(allCommentIds));
        Set<Long> likedCommentIds = finalLoginUserId != null
                ? new HashSet<>(commentLikeRepository.findLikedCommentIdsByUserId(finalLoginUserId, allCommentIds))
                : Collections.emptySet();

        List<CommentItem> comments = parentComments.stream()
                .map(comment -> {
                    CommentItem item = CommentItem.from(comment, finalLoginUserId,
                            likeCounts.getOrDefault(comment.getId(), 0L),
                            likedCommentIds.contains(comment.getId()));
                    item.replies = repliesByParent.getOrDefault(comment.getId(), List.of()).stream()
                            .map(r -> CommentItem.from(r, finalLoginUserId,
                                    likeCounts.getOrDefault(r.getId(), 0L),
                                    likedCommentIds.contains(r.getId())))
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

        Comment saved = commentRepository.save(comment);

        Video video = optionalVideo.get();
        notificationService.send(video.getOwnerId(), sessionUser.getId(), "COMMENT",
                comment.getAuthor() + "님이 댓글을 달았어요: " + truncate(content, 40),
                id, video.getThumbnail());

        return ResponseEntity.ok(new CommentCreateResponse(
                true,
                CommentItem.from(saved, sessionUser.getId(), 0L, false)
        ));
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
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
        reply.setParentId(commentId);

        Comment saved = commentRepository.save(reply);

        String name = reply.getAuthor();
        videoRepository.findById(parent.get().getVideoId()).ifPresent(video ->
            notificationService.send(parent.get().getUserId(), sessionUser.getId(), "COMMENT",
                    name + "님이 답글을 달았어요: " + truncate(content, 40),
                    video.getId(), video.getThumbnail())
        );

        return ResponseEntity.ok(new CommentCreateResponse(true, CommentItem.from(saved, sessionUser.getId(), 0L, false)));
    }

    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<?> toggleCommentLike(@PathVariable Long commentId, HttpSession session) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        if (sessionUser == null) return ResponseEntity.status(401).body(new SimpleResponse(false, "로그인이 필요합니다."));

        Optional<Comment> optComment = commentRepository.findById(commentId);
        if (optComment.isEmpty()) return ResponseEntity.notFound().build();

        Comment comment = optComment.get();
        Optional<CommentLike> existing = commentLikeRepository.findByCommentIdAndUserId(commentId, sessionUser.getId());

        boolean liked;
        if (existing.isPresent()) {
            commentLikeRepository.delete(existing.get());
            liked = false;
        } else {
            CommentLike cl = new CommentLike();
            cl.setCommentId(commentId);
            cl.setUserId(sessionUser.getId());
            commentLikeRepository.save(cl);
            liked = true;

            String name = sessionUser.getChannelName() != null && !sessionUser.getChannelName().isBlank()
                    ? sessionUser.getChannelName() : sessionUser.getNickname();
            videoRepository.findById(comment.getVideoId()).ifPresent(video ->
                notificationService.send(comment.getUserId(), sessionUser.getId(), "COMMENT_LIKE",
                        name + "님이 댓글에 좋아요를 눌렀어요: " + truncate(comment.getText(), 30),
                        video.getId(), video.getThumbnail())
            );
        }

        long likeCount = commentLikeRepository.countByCommentId(commentId);
        return ResponseEntity.ok(java.util.Map.of("liked", liked, "likeCount", likeCount));
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
        long likeCount = commentLikeRepository.countByCommentId(commentId);
        boolean isLiked = commentLikeRepository.existsByCommentIdAndUserId(commentId, sessionUser.getId());

        return ResponseEntity.ok(new CommentCreateResponse(
                true,
                CommentItem.from(saved, sessionUser.getId(), likeCount, isLiked)
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

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
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
