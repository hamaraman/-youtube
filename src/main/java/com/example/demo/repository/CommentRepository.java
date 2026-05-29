package com.example.demo.repository;

import com.example.demo.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByVideoIdOrderByIdDesc(Long videoId);
    List<Comment> findByVideoIdAndParentIdIsNullOrderByIdDesc(Long videoId);
    List<Comment> findByParentIdOrderByIdAsc(Long parentId);
    void deleteByVideoId(Long videoId);
    void deleteByParentId(Long parentId);
}