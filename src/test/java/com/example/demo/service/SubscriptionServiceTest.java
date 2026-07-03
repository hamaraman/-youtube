package com.example.demo.service;

import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.SubscriptionController.SubscribeResponse;
import com.example.demo.entity.Subscription;
import com.example.demo.repository.SubscriptionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private SubscriptionService subscriptionService;

    private SessionUser subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new SessionUser(1L, "user", "닉네임", "u@e.com", "채널", "img", "USER");
    }

    @Test
    void toggleSubscribe_withoutLogin_throwsUnauthorized() {
        assertThatThrownBy(() -> subscriptionService.toggleSubscribe(2L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("로그인");
    }

    @Test
    void toggleSubscribe_selfSubscribe_throwsBadRequest() {
        assertThatThrownBy(() -> subscriptionService.toggleSubscribe(1L, subscriber))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("자기 자신");
    }

    @Test
    void toggleSubscribe_newSubscription_createsAndNotifies() {
        when(subscriptionRepository.findBySubscriberIdAndChannelOwnerId(1L, 2L))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.countByChannelOwnerId(2L)).thenReturn(11L);

        SubscribeResponse response = subscriptionService.toggleSubscribe(2L, subscriber);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isSubscribed()).isTrue();
        assertThat(response.getSubscriberCount()).isEqualTo(11L);
        verify(subscriptionRepository).save(any(Subscription.class));
        verify(subscriptionRepository, never()).delete(any());
        verify(notificationService).send(eq(2L), eq(1L), eq("SUBSCRIBE"),
                anyString(), any(), any());
    }

    @Test
    void toggleSubscribe_existingSubscription_deletesAndDoesNotNotify() {
        Subscription existing = new Subscription();
        existing.setId(100L);
        existing.setSubscriberId(1L);
        existing.setChannelOwnerId(2L);

        when(subscriptionRepository.findBySubscriberIdAndChannelOwnerId(1L, 2L))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.countByChannelOwnerId(2L)).thenReturn(9L);

        SubscribeResponse response = subscriptionService.toggleSubscribe(2L, subscriber);

        assertThat(response.isSubscribed()).isFalse();
        assertThat(response.getSubscriberCount()).isEqualTo(9L);
        verify(subscriptionRepository).delete(existing);
        verify(subscriptionRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void searchChannels_emptyKeyword_returnsEmpty() {
        assertThat(subscriptionService.searchChannels("", 1L)).isEmpty();
        assertThat(subscriptionService.searchChannels(null, 1L)).isEmpty();
        assertThat(subscriptionService.searchChannels("   ", 1L)).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void getSubscriptionStatus_notLoggedIn_returnsSubscribedFalse() {
        when(subscriptionRepository.countByChannelOwnerId(2L)).thenReturn(5L);

        SubscribeResponse response = subscriptionService.getSubscriptionStatus(2L, null);

        assertThat(response.isSubscribed()).isFalse();
        assertThat(response.getSubscriberCount()).isEqualTo(5L);
        verify(subscriptionRepository, never()).existsBySubscriberIdAndChannelOwnerId(anyLong(), anyLong());
    }

    @Test
    void getSubscriptionStatus_loggedIn_checksRelation() {
        when(subscriptionRepository.countByChannelOwnerId(2L)).thenReturn(3L);
        when(subscriptionRepository.existsBySubscriberIdAndChannelOwnerId(1L, 2L)).thenReturn(true);

        SubscribeResponse response = subscriptionService.getSubscriptionStatus(2L, 1L);

        assertThat(response.isSubscribed()).isTrue();
        assertThat(response.getSubscriberCount()).isEqualTo(3L);
    }
}
