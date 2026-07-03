package com.example.demo.repository;

import com.example.demo.entity.CommentLike;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class CommentLikeRepositoryTest {

    @Autowired private CommentLikeRepository commentLikeRepository;

    @BeforeEach
    void setUp() {
        like(100L, 1L);
        like(100L, 2L);
        like(100L, 3L);
        like(200L, 1L);
    }

    private void like(Long commentId, Long userId) {
        CommentLike cl = new CommentLike();
        cl.setCommentId(commentId); cl.setUserId(userId);
        commentLikeRepository.save(cl);
    }

    @Test
    void countByCommentId_returnsCount() {
        assertThat(commentLikeRepository.countByCommentId(100L)).isEqualTo(3L);
        assertThat(commentLikeRepository.countByCommentId(200L)).isEqualTo(1L);
        assertThat(commentLikeRepository.countByCommentId(999L)).isZero();
    }

    @Test
    void existsByCommentIdAndUserId_returnsBoolean() {
        assertThat(commentLikeRepository.existsByCommentIdAndUserId(100L, 1L)).isTrue();
        assertThat(commentLikeRepository.existsByCommentIdAndUserId(100L, 999L)).isFalse();
    }

    @Test
    void findByCommentIdAndUserId_returnsOptional() {
        assertThat(commentLikeRepository.findByCommentIdAndUserId(100L, 1L)).isPresent();
        assertThat(commentLikeRepository.findByCommentIdAndUserId(999L, 1L)).isEmpty();
    }

    @Test
    void countByCommentIdIn_groupsCorrectly() {
        List<Object[]> rows = commentLikeRepository.countByCommentIdIn(List.of(100L, 200L, 999L));
        assertThat(rows).hasSize(2);
    }

    @Test
    void findLikedCommentIdsByUserId_returnsOnlyMatching() {
        List<Long> liked = commentLikeRepository.findLikedCommentIdsByUserId(
                1L, List.of(100L, 200L, 999L));
        assertThat(liked).containsExactlyInAnyOrder(100L, 200L);
    }
}
