package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "comment_likes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "user_id"}),
       indexes = {
               @Index(name = "idx_comment_likes_comment_id", columnList = "comment_id"),
               @Index(name = "idx_comment_likes_user_id", columnList = "user_id")
       })
public class CommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public Long getId() { return id; }
    public Long getCommentId() { return commentId; }
    public Long getUserId() { return userId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
