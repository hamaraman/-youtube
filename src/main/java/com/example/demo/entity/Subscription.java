package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "subscriptions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"subscriber_id", "channel_owner_id"}))
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscriber_id", nullable = false)
    private Long subscriberId;

    @Column(name = "channel_owner_id", nullable = false)
    private Long channelOwnerId;

    public Long getId() { return id; }
    public Long getSubscriberId() { return subscriberId; }
    public Long getChannelOwnerId() { return channelOwnerId; }
    public void setSubscriberId(Long subscriberId) { this.subscriberId = subscriberId; }
    public void setChannelOwnerId(Long channelOwnerId) { this.channelOwnerId = channelOwnerId; }
}
