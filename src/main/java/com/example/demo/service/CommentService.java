package com.example.demo.service;

import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.CommentController.CommentItem;
import com.example.demo.entity.Comment;
import com.example.demo.entity.CommentLike;
import com.example.demo.entity.Video;
import com.example.demo.repository.CommentLikeRepository;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.VideoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final VideoRepository videoRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final NotificationService notificationService;

    public CommentService(CommentRepository commentRepository,
                          VideoRepository videoRepository,
                          CommentLikeRepository commentLikeRepository,
                          NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.videoRepository = videoRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.notificationService = notificationService;
    }

    public Map<String, Object> getComments(Long id, int page, int size, Long loginUserId) {
        Optional<Video> optionalVideo = videoRepository.findById(id);
        if (optionalVideo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다.");
        }

        int safeSize = Math.min(Math.max(size, 1), 50);

        Page<Comment> pageResult = commentRepository
                .findByVideoIdAndParentIdIsNullOrderByIdDesc(id, PageRequest.of(page, safeSize));
        List<Comment> parentComments = pageResult.getContent();

        if (parentComments.isEmpty()) {
            long total = commentRepository.countAllByVideoId(id);
            return Map.of(
                    "comments", List.of(),
                    "total", total,
                    "page", page,
                    "hasMore", false
            );
        }

        List<Long> parentIds = parentComments.stream().map(Comment::getId).collect(Collectors.toList());
        List<Comment> allReplies = commentRepository.findByParentIdInOrderByIdAsc(parentIds);
        Map<Long, List<Comment>> repliesByParent = allReplies.stream()
                .collect(Collectors.groupingBy(Comment::getParentId));

        List<Long> allCommentIds = new ArrayList<>(parentIds);
        allReplies.stream().map(Comment::getId).forEach(allCommentIds::add);

        Map<Long, Long> likeCounts = toCountMap(commentLikeRepository.countByCommentIdIn(allCommentIds));
        Set<Long> likedCommentIds = loginUserId != null
                ? new HashSet<>(commentLikeRepository.findLikedCommentIdsByUserId(loginUserId, allCommentIds))
                : Collections.emptySet();

        List<CommentItem> comments = parentComments.stream()
                .map(comment -> {
                    CommentItem item = CommentItem.from(comment, loginUserId,
                            likeCounts.getOrDefault(comment.getId(), 0L),
                            likedCommentIds.contains(comment.getId()));
                    item.replies = repliesByParent.getOrDefault(comment.getId(), List.of()).stream()
                            .map(r -> CommentItem.from(r, loginUserId,
                                    likeCounts.getOrDefault(r.getId(), 0L),
                                    likedCommentIds.contains(r.getId())))
                            .collect(Collectors.toList());
                    return item;
                })
                .collect(Collectors.toList());

        long total = commentRepository.countAllByVideoId(id);
        Map<String, Object> response = new HashMap<>();
        response.put("comments", comments);
        response.put("total", total);
        response.put("page", page);
        response.put("hasMore", pageResult.hasNext());
        return response;
    }

    public CommentItem createComment(Long id, String content, SessionUser sessionUser) {
        if (sessionUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        Optional<Video> optionalVideo = videoRepository.findById(id);
        if (optionalVideo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다.");
        }

        String trimmedContent = content == null ? "" : content.trim();
        if (trimmedContent.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해줘.");
        }

        Comment comment = new Comment();
        comment.setVideoId(id);
        comment.setUserId(sessionUser.getId());
        comment.setAuthor(
                sessionUser.getNickname() != null && !sessionUser.getNickname().isBlank()
                        ? sessionUser.getNickname()
                        : sessionUser.getUsername()
        );
        comment.setText(trimmedContent);

        Comment saved = commentRepository.save(comment);

        Video video = optionalVideo.get();
        notificationService.send(video.getOwnerId(), sessionUser.getId(), "COMMENT",
                comment.getAuthor() + "님이 댓글을 달았어요: " + truncate(trimmedContent, 40),
                id, video.getThumbnail());

        return CommentItem.from(saved, sessionUser.getId(), 0L, false);
    }

    public CommentItem createReply(Long commentId, String content, SessionUser sessionUser) {
        if (sessionUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        Optional<Comment> parent = commentRepository.findById(commentId);
        if (parent.isEmpty() || parent.get().getParentId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 댓글입니다.");
        }

        String trimmedContent = content == null ? "" : content.trim();
        if (trimmedContent.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "답글 내용을 입력해줘.");
        }

        Comment reply = new Comment();
        reply.setVideoId(parent.get().getVideoId());
        reply.setUserId(sessionUser.getId());
        reply.setAuthor(sessionUser.getNickname() != null && !sessionUser.getNickname().isBlank()
                ? sessionUser.getNickname() : sessionUser.getUsername());
        reply.setText(trimmedContent);
        reply.setParentId(commentId);

        Comment saved = commentRepository.save(reply);

        String name = reply.getAuthor();
        videoRepository.findById(parent.get().getVideoId()).ifPresent(video ->
                notificationService.send(parent.get().getUserId(), sessionUser.getId(), "COMMENT",
                        name + "님이 답글을 달았어요: " + truncate(trimmedContent, 40),
                        video.getId(), video.getThumbnail())
        );

        return CommentItem.from(saved, sessionUser.getId(), 0L, false);
    }

    public Map<String, Object> toggleCommentLike(Long commentId, SessionUser sessionUser) {
        if (sessionUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        Optional<Comment> optComment = commentRepository.findById(commentId);
        if (optComment.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }

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
        return Map.of("liked", liked, "likeCount", likeCount);
    }

    public CommentItem updateComment(Long commentId, String content, SessionUser sessionUser) {
        if (sessionUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        Optional<Comment> optionalComment = commentRepository.findById(commentId);
        if (optionalComment.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }

        Comment comment = optionalComment.get();

        if (!sessionUser.getId().equals(comment.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 댓글만 수정할 수 있습니다.");
        }

        String trimmedContent = content == null ? "" : content.trim();
        if (trimmedContent.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정할 댓글 내용을 입력해줘.");
        }

        comment.setText(trimmedContent);
        Comment saved = commentRepository.save(comment);
        long likeCount = commentLikeRepository.countByCommentId(commentId);
        boolean isLiked = commentLikeRepository.existsByCommentIdAndUserId(commentId, sessionUser.getId());

        return CommentItem.from(saved, sessionUser.getId(), likeCount, isLiked);
    }

    @Transactional
    public void deleteComment(Long commentId, SessionUser sessionUser) {
        if (sessionUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        Optional<Comment> optionalComment = commentRepository.findById(commentId);
        if (optionalComment.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }

        Comment comment = optionalComment.get();

        if (!sessionUser.getId().equals(comment.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다.");
        }

        if (comment.getParentId() == null) {
            commentRepository.deleteByParentId(commentId);
        }
        commentRepository.delete(comment);
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
