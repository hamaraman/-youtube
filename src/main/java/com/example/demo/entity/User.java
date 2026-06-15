package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(unique = true)
    private String email;

    @Column(name = "channel_name")
    private String channelName;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "banner_image", columnDefinition = "TEXT")
    private String bannerImage;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(nullable = false, length = 20)
    private String role = "USER";
}