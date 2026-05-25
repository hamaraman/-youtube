package com.example.demo.repository;

import com.example.demo.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    long countByChannelOwnerId(Long channelOwnerId);
    boolean existsBySubscriberIdAndChannelOwnerId(Long subscriberId, Long channelOwnerId);
    Optional<Subscription> findBySubscriberIdAndChannelOwnerId(Long subscriberId, Long channelOwnerId);
}
