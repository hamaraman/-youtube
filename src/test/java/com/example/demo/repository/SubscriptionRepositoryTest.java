package com.example.demo.repository;

import com.example.demo.entity.Subscription;
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
class SubscriptionRepositoryTest {

    @Autowired private SubscriptionRepository subscriptionRepository;

    @BeforeEach
    void setUp() {
        sub(1L, 100L);
        sub(2L, 100L);
        sub(3L, 100L);
        sub(1L, 200L);
    }

    private Subscription sub(Long subscriberId, Long channelOwnerId) {
        Subscription s = new Subscription();
        s.setSubscriberId(subscriberId); s.setChannelOwnerId(channelOwnerId);
        return subscriptionRepository.save(s);
    }

    @Test
    void countByChannelOwnerId_countsSubscribers() {
        assertThat(subscriptionRepository.countByChannelOwnerId(100L)).isEqualTo(3L);
        assertThat(subscriptionRepository.countByChannelOwnerId(200L)).isEqualTo(1L);
        assertThat(subscriptionRepository.countByChannelOwnerId(999L)).isZero();
    }

    @Test
    void existsBySubscriberIdAndChannelOwnerId_returnsBoolean() {
        assertThat(subscriptionRepository.existsBySubscriberIdAndChannelOwnerId(1L, 100L)).isTrue();
        assertThat(subscriptionRepository.existsBySubscriberIdAndChannelOwnerId(999L, 100L)).isFalse();
    }

    @Test
    void findBySubscriberIdAndChannelOwnerId_returnsOptional() {
        assertThat(subscriptionRepository.findBySubscriberIdAndChannelOwnerId(1L, 100L)).isPresent();
        assertThat(subscriptionRepository.findBySubscriberIdAndChannelOwnerId(1L, 999L)).isEmpty();
    }

    @Test
    void findByChannelOwnerId_returnsAllSubscribersOfChannel() {
        List<Subscription> subs = subscriptionRepository.findByChannelOwnerId(100L);
        assertThat(subs).extracting(Subscription::getSubscriberId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void findBySubscriberId_returnsAllChannelsUserSubscribedTo() {
        List<Subscription> subs = subscriptionRepository.findBySubscriberId(1L);
        assertThat(subs).extracting(Subscription::getChannelOwnerId)
                .containsExactlyInAnyOrder(100L, 200L);
    }
}
