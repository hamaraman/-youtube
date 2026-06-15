package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "comment_likes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "user_id"}),
       indexes = {
               @Index(name = "idx_comment_likes_comment_id", columnList = "comment_id"),
               @Index(name = "idx_comment_likes_user_id", columnList = "user_id")
       })
@Getter
@Setter
@NoArgsConstructor
public class CommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
