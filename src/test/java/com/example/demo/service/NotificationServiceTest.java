package com.example.demo.service;

import com.example.demo.entity.Notification;
import com.example.demo.repository.NotificationRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @InjectMocks private NotificationService service;

    private Notification notification(Long id, Long receiverId, boolean read) {
        Notification n = new Notification();
        n.setId(id); n.setReceiverId(receiverId); n.setRead(read);
        n.setMessage("msg"); n.setType("LIKE");
        return n;
    }

    @Nested
    class Send {

        @Test
        void nullIds_returnsWithoutSaving() {
            service.send(null, 10L, "LIKE", "msg", 1L, "th");
            service.send(5L, null, "LIKE", "msg", 1L, "th");
            verifyNoInteractions(notificationRepository);
        }

        @Test
        void selfNotification_ignored() {
            service.send(10L, 10L, "LIKE", "msg", 1L, "th");
            verifyNoInteractions(notificationRepository);
        }

        @Test
        void valid_persistsNotification() {
            when(notificationRepository.countByReceiverIdAndReadFalse(5L)).thenReturn(3L);

            service.send(5L, 10L, "LIKE", "안녕", 42L, "thumb.png");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            Notification saved = captor.getValue();
            assertThat(saved.getReceiverId()).isEqualTo(5L);
            assertThat(saved.getType()).isEqualTo("LIKE");
            assertThat(saved.getMessage()).isEqualTo("안녕");
            assertThat(saved.getRelatedVideoId()).isEqualTo(42L);
            assertThat(saved.getThumbnail()).isEqualTo("thumb.png");
        }
    }

    @Nested
    class GetNotifications {

        @Test
        void returnsListAndUnreadCount() {
            when(notificationRepository.findByReceiverIdOrderByCreatedAtDesc(5L))
                    .thenReturn(List.of(notification(1L, 5L, false), notification(2L, 5L, true)));
            when(notificationRepository.countByReceiverIdAndReadFalse(5L)).thenReturn(1L);

            Map<String, Object> result = service.getNotifications(5L);

            assertThat(result.get("unreadCount")).isEqualTo(1L);
            assertThat((List<?>) result.get("notifications")).hasSize(2);
        }
    }

    @Nested
    class MarkRead {

        @Test
        void missing_isNoop() {
            when(notificationRepository.findById(99L)).thenReturn(Optional.empty());
            service.markRead(99L, 5L);
            verify(notificationRepository, never()).save(any());
        }

        @Test
        void wrongUser_isNoop() {
            Notification n = notification(1L, 5L, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            service.markRead(1L, 999L);

            assertThat(n.isRead()).isFalse();
            verify(notificationRepository, never()).save(any());
        }

        @Test
        void correctUser_marksReadAndSaves() {
            Notification n = notification(1L, 5L, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            service.markRead(1L, 5L);

            assertThat(n.isRead()).isTrue();
            verify(notificationRepository).save(n);
        }
    }

    @Nested
    class MarkAllRead {

        @Test
        void marksOnlyUnread() {
            Notification n1 = notification(1L, 5L, false);
            Notification n2 = notification(2L, 5L, true);
            Notification n3 = notification(3L, 5L, false);
            when(notificationRepository.findByReceiverIdOrderByCreatedAtDesc(5L))
                    .thenReturn(List.of(n1, n2, n3));

            service.markAllRead(5L);

            assertThat(n1.isRead()).isTrue();
            assertThat(n3.isRead()).isTrue();

            ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
            verify(notificationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).extracting(Notification::getId).containsExactly(1L, 3L);
        }
    }

    @Nested
    class DeleteNotification {

        @Test
        void wrongUser_isNoop() {
            Notification n = notification(1L, 5L, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            service.deleteNotification(1L, 999L);

            verify(notificationRepository, never()).delete(any());
        }

        @Test
        void correctUser_deletes() {
            Notification n = notification(1L, 5L, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            service.deleteNotification(1L, 5L);

            verify(notificationRepository).delete(n);
        }
    }

    @Nested
    class Subscribe {

        @Test
        void returnsEmitter() {
            SseEmitter emitter = service.subscribe(5L);
            assertThat(emitter).isNotNull();
        }

        @Test
        void multipleSubscribers_areTracked() {
            SseEmitter a = service.subscribe(5L);
            SseEmitter b = service.subscribe(5L);
            SseEmitter c = service.subscribe(6L);
            assertThat(a).isNotSameAs(b);
            assertThat(c).isNotNull();
        }
    }
}
