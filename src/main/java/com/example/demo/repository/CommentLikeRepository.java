package com.example.demo.repository;

import com.example.demo.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    Optional<CommentLike> findByCommentIdAndUserId(Long commentId, Long userId);
    long countByCommentId(Long commentId);
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    @Query("SELECT cl.commentId, COUNT(cl) FROM CommentLike cl WHERE cl.commentId IN :commentIds GROUP BY cl.commentId")
    List<Object[]> countByCommentIdIn(@Param("commentIds") List<Long> commentIds);

    @Query("SELECT cl.commentId FROM CommentLike cl WHERE cl.userId = :userId AND cl.commentId IN :commentIds")
    List<Long> findLikedCommentIdsByUserId(@Param("userId") Long userId, @Param("commentIds") List<Long> commentIds);
}
