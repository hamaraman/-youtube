package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscriptions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"subscriber_id", "channel_owner_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscriber_id", nullable = false)
    private Long subscriberId;

    @Column(name = "channel_owner_id", nullable = false)
    private Long channelOwnerId;
}
