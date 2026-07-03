package com.example.demo.service;

import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.CommentController.CommentItem;
import com.example.demo.entity.Comment;
import com.example.demo.entity.CommentLike;
import com.example.demo.entity.Video;
import com.example.demo.repository.CommentLikeRepository;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private CommentLikeRepository commentLikeRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private CommentService commentService;

    private SessionUser me;
    private Video video;

    @BeforeEach
    void setUp() {
        me = new SessionUser(10L, "user10", "닉네임", "u@e.com", "채널", "img", "USER");
        video = new Video();
        video.setId(1L);
        video.setOwnerId(99L);
        video.setTitle("hello");
        video.setThumbnail("thumb.png");
    }

    private Comment comment(Long id, Long videoId, Long userId, Long parentId, String text) {
        Comment c = new Comment();
        c.setId(id); c.setVideoId(videoId); c.setUserId(userId);
        c.setParentId(parentId); c.setText(text); c.setAuthor("author");
        return c;
    }

    @Nested
    class GetComments {

        @Test
        void videoMissing_throwsNotFound() {
            when(videoRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> commentService.getComments(1L, 0, 10, null))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void noComments_returnsEmptyListWithTotalZero() {
            when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
            when(commentRepository.findByVideoIdAndParentIdIsNullOrderByIdDesc(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(commentRepository.countAllByVideoId(1L)).thenReturn(0L);

            Map<String, Object> result = commentService.getComments(1L, 0, 10, null);

            assertThat(result.get("total")).isEqualTo(0L);
            assertThat(result.get("hasMore")).isEqualTo(false);
            assertThat((List<?>) result.get("comments")).isEmpty();
        }

        @Test
        void withComments_populatesRepliesAndLikeCounts() {
            Comment top = comment(100L, 1L, 5L, null, "top comment");
            Comment reply = comment(200L, 1L, 6L, 100L, "reply");

            Page<Comment> page = new PageImpl<>(List.of(top), PageRequest.of(0, 10), 1);
            when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
            when(commentRepository.findByVideoIdAndParentIdIsNullOrderByIdDesc(eq(1L), any(PageRequest.class)))
                    .thenReturn(page);
            when(commentRepository.findByParentIdInOrderByIdAsc(List.of(100L)))
                    .thenReturn(List.of(reply));
            when(commentLikeRepository.countByCommentIdIn(anyList()))
                    .thenReturn(List.of(new Object[]{100L, 3L}, new Object[]{200L, 1L}));
            when(commentLikeRepository.findLikedCommentIdsByUserId(eq(10L), anyList()))
                    .thenReturn(List.of(100L));
            when(commentRepository.countAllByVideoId(1L)).thenReturn(2L);

            Map<String, Object> result = commentService.getComments(1L, 0, 10, 10L);

            assertThat(result.get("total")).isEqualTo(2L);
            @SuppressWarnings("unchecked")
            List<CommentItem> comments = (List<CommentItem>) result.get("comments");
            assertThat(comments).hasSize(1);
            CommentItem topItem = comments.get(0);
            assertThat(topItem.getLikeCount()).isEqualTo(3L);
            assertThat(topItem.isLiked()).isTrue();
            assertThat(topItem.getReplies()).hasSize(1);
            assertThat(topItem.getReplies().get(0).getLikeCount()).isEqualTo(1L);
            assertThat(topItem.getReplies().get(0).isLiked()).isFalse();
        }

        @Test
        void sizeClampedTo50() {
            when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
            when(commentRepository.findByVideoIdAndParentIdIsNullOrderByIdDesc(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            commentService.getComments(1L, 0, 9999, null);

            ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
            verify(commentRepository).findByVideoIdAndParentIdIsNullOrderByIdDesc(eq(1L), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        }
    }

    @Nested
    class CreateComment {

        @Test
        void notLoggedIn_throwsUnauthorized() {
            assertThatThrownBy(() -> commentService.createComment(1L, "hi", null))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void videoMissing_throwsNotFound() {
            when(videoRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> commentService.createComment(1L, "hi", me))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void blankContent_throwsBadRequest() {
            when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
            assertThatThrownBy(() -> commentService.createComment(1L, "   ", me))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> commentService.createComment(1L, null, me))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void valid_persistsAndNotifiesOwner() {
            when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
            when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
                Comment c = inv.getArgument(0);
                c.setId(500L);
                return c;
            });

            CommentItem item = commentService.createComment(1L, "  안녕  ", me);

            assertThat(item.getText()).isEqualTo("안녕");
            assertThat(item.getAuthor()).isEqualTo("닉네임");

            ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
            verify(commentRepository).save(captor.capture());
            Comment saved = captor.getValue();
            assertThat(saved.getVideoId()).isEqualTo(1L);
            assertThat(saved.getUserId()).isEqualTo(10L);
            assertThat(saved.getParentId()).isNull();

            verify(notificationService).send(eq(99L), eq(10L), eq("COMMENT"),
                    anyString(), eq(1L), eq("thumb.png"));
        }

        @Test
        void blankNickname_fallsBackToUsername() {
            SessionUser noNick = new SessionUser(10L, "user10", " ", "e", "c", "img", "USER");
            when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
            when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

            CommentItem item = commentService.createComment(1L, "hi", noNick);

            assertThat(item.getAuthor()).isEqualTo("user10");
        }
    }

    @Nested
    class CreateReply {

        @Test
        void parentMissing_throwsBadRequest() {
            when(commentRepository.findById(500L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> commentService.createReply(500L, "hi", me))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void parentIsAlreadyReply_throwsBadRequest() {
            Comment child = comment(500L, 1L, 5L, 100L, "already a reply");
            when(commentRepository.findById(500L)).thenReturn(Optional.of(child));
            assertThatThrownBy(() -> commentService.createReply(500L, "hi", me))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void blankContent_throwsBadRequest() {
            Comment parent = comment(500L, 1L, 5L, null, "top");
            when(commentRepository.findById(500L)).thenReturn(Optional.of(parent));
            assertThatThrownBy(() -> commentService.createReply(500L, "   ", me))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void valid_notifiesParentAuthorAndSetsParentId() {
            Comment parent = comment(500L, 1L, 5L, null, "top");
            when(commentRepository.findById(500L)).thenReturn(Optional.of(parent));
            when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
                Comment c = inv.getArgument(0);
                c.setId(600L);
                return c;
            });
            when(videoRepository.findById(1L)).thenReturn(Optional.of(video));

            CommentItem item = commentService.createReply(500L, "답글이야", me);

            assertThat(item.getParentId()).isEqualTo(500L);
            assertThat(item.getVideoId()).isEqualTo(1L);
            verify(notificationService).send(eq(5L), eq(10L), eq("COMMENT"),
                    anyString(), eq(1L), eq("thumb.png"));
        }

        @Test
        void notLoggedIn_throwsUnauthorized() {
            assertThatThrownBy(() -> commentService.createReply(500L, "hi", null))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    @Nested
    class ToggleLike {

        @Test
        void notLoggedIn_throwsUnauthorized() {
            assertThatThrownBy(() -> commentService.toggleCommentLike(500L, null))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void commentMissing_throwsNotFound() {
            when(commentRepository.findById(500L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> commentService.toggleCommentLike(500L, me))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void firstLike_savesLikeAndNotifiesAuthor() {
            Comment c = comment(500L, 1L, 5L, null, "글");
            when(commentRepository.findById(500L)).thenReturn(Optional.of(c));
            when(commentLikeRepository.findByCommentIdAndUserId(500L, 10L)).thenReturn(Optional.empty());
            when(commentLikeRepository.countByCommentId(500L)).thenReturn(1L);
            when(videoRepository.findById(1L)).thenReturn(Optional.of(video));

            Map<String, Object> result = commentService.toggleCommentLike(500L, me);

            assertThat(result.get("liked")).isEqualTo(true);
            assertThat(result.get("likeCount")).isEqualTo(1L);
            verify(commentLikeRepository).save(any(CommentLike.class));
            verify(commentLikeRepository, never()).delete(any());
            verify(notificationService).send(eq(5L), eq(10L), eq("COMMENT_LIKE"),
                    anyString(), eq(1L), eq("thumb.png"));
        }

        @Test
        void existingLike_deletesAndDoesNotNotify() {
            Comment c = comment(500L, 1L, 5L, null, "글");
            CommentLike existing = new CommentLike();
            existing.setId(999L); existing.setCommentId(500L); existing.setUserId(10L);

            when(commentRepository.findById(500L)).thenReturn(Optional.of(c));
            when(commentLikeRepository.findByCommentIdAndUserId(500L, 10L)).thenReturn(Optional.of(existing));
            when(commentLikeRepository.countByCommentId(500L)).thenReturn(0L);

            Map<String, Object> result = commentService.toggleCommentLike(500L, me);

            assertThat(result.get("liked")).isEqualTo(false);
            assertThat(result.get("likeCount")).isEqualTo(0L);
            verify(commentLikeRepository).delete(existing);
            verify(commentLikeRepository, never()).save(any());
            verifyNoInteractions(notificationService);
        }
    }

    @Nested
    class UpdateComment {

        @Test
        void notLoggedIn_throwsUnauthorized() {
            assertThatThrownBy(() -> commentService.updateComment(500L, "new", null))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void commentMissing_throwsNotFound() {
            when(commentRepository.findById(500L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> commentService.updateComment(500L, "new", me))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void notOwner_throwsForbidden() {
            Comment c = comment(500L, 1L, 999L, null, "글");
            when(commentRepository.findById(500L)).thenReturn(Optional.of(c));
            assertThatThrownBy(() -> commentService.updateComment(500L, "new", me))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("본인");
        }

        @Test
        void blankContent_throwsBadRequest() {
            Comment c = comment(500L, 1L, 10L, null, "old");
            when(commentRepository.findById(500L)).thenReturn(Optional.of(c));
            assertThatThrownBy(() -> commentService.updateComment(500L, "  ", me))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void valid_updatesTextAndReturnsItem() {
            Comment c = comment(500L, 1L, 10L, null, "old");
            when(commentRepository.findById(500L)).thenReturn(Optional.of(c));
            when(commentRepository.save(c)).thenReturn(c);
            when(commentLikeRepository.countByCommentId(500L)).thenReturn(5L);
            when(commentLikeRepository.existsByCommentIdAndUserId(500L, 10L)).thenReturn(true);

            CommentItem item = commentService.updateComment(500L, "  new text  ", me);

            assertThat(c.getText()).isEqualTo("new text");
            assertThat(item.getText()).isEqualTo("new text");
            assertThat(item.getLikeCount()).isEqualTo(5L);
            assertThat(item.isLiked()).isTrue();
        }
    }

    @Nested
    class DeleteComment {

        @Test
        void notLoggedIn_throwsUnauthorized() {
            assertThatThrownBy(() -> commentService.deleteComment(500L, null))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void commentMissing_throwsNotFound() {
            when(commentRepository.findById(500L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> commentService.deleteComment(500L, me))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void notOwner_throwsForbidden() {
            Comment c = comment(500L, 1L, 999L, null, "글");
            when(commentRepository.findById(500L)).thenReturn(Optional.of(c));
            assertThatThrownBy(() -> commentService.deleteComment(500L, me))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("본인");
        }

        @Test
        void topLevelDelete_cascadesReplies() {
            Comment c = comment(500L, 1L, 10L, null, "top");
            when(commentRepository.findById(500L)).thenReturn(Optional.of(c));

            commentService.deleteComment(500L, me);

            verify(commentRepository).deleteByParentId(500L);
            verify(commentRepository).delete(c);
        }

        @Test
        void replyDelete_doesNotCascade() {
            Comment reply = comment(600L, 1L, 10L, 500L, "reply");
            when(commentRepository.findById(600L)).thenReturn(Optional.of(reply));

            commentService.deleteComment(600L, me);

            verify(commentRepository, never()).deleteByParentId(anyLong());
            verify(commentRepository).delete(reply);
        }
    }
}
