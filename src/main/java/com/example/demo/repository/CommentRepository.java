package com.example.demo.repository;

import com.example.demo.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByVideoIdOrderByIdDesc(Long videoId);
    List<Comment> findByVideoIdAndParentIdIsNullOrderByIdDesc(Long videoId);
    Page<Comment> findByVideoIdAndParentIdIsNullOrderByIdDesc(Long videoId, Pageable pageable);
    List<Comment> findByParentIdOrderByIdAsc(Long parentId);
    long countByVideoIdAndParentIdIsNull(Long videoId);
    void deleteByVideoId(Long videoId);
    void deleteByParentId(Long parentId);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.videoId = :videoId")
    long countAllByVideoId(@Param("videoId") Long videoId);

    @Query("SELECT c.videoId, COUNT(c) FROM Comment c WHERE c.videoId IN :videoIds AND c.parentId IS NULL GROUP BY c.videoId")
    List<Object[]> countByVideoIdIn(@Param("videoIds") List<Long> videoIds);

    @Query("SELECT c FROM Comment c WHERE c.parentId IN :parentIds ORDER BY c.id ASC")
    List<Comment> findByParentIdInOrderByIdAsc(@Param("parentIds") List<Long> parentIds);
}
