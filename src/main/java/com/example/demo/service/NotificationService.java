package com.example.demo.service;

import com.example.demo.entity.Notification;
import com.example.demo.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {

    private static final long EMITTER_TIMEOUT = 60 * 60 * 1000L; // 1시간

    private final NotificationRepository notificationRepository;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }
        return emitter;
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) emitters.remove(userId);
    }

    public void send(Long receiverId, Long actorId, String type, String message, Long relatedVideoId, String thumbnail) {
        if (receiverId == null || actorId == null) return;
        if (receiverId.equals(actorId)) return; // 자신에게 알림 안 보냄

        Notification n = new Notification();
        n.setReceiverId(receiverId);
        n.setType(type);
        n.setMessage(message);
        n.setRelatedVideoId(relatedVideoId);
        n.setThumbnail(thumbnail);
        notificationRepository.save(n);

        long unreadCount = notificationRepository.countByReceiverIdAndReadFalse(receiverId);
        pushToUser(receiverId, n, unreadCount);
    }

    private void pushToUser(Long userId, Notification notification, long unreadCount) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("notification")
                        .data(Map.of("notification", notification, "unreadCount", unreadCount)));
            } catch (IOException e) {
                emitter.complete();
                removeEmitter(userId, emitter);
            }
        }
    }

    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException e) {
                    emitter.complete();
                    removeEmitter(userId, emitter);
                }
            }
        });
    }

    // 데이터베이스 조작 기능 추가
    public Map<String, Object> getNotifications(Long userId) {
        List<Notification> notifications = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(userId);
        long unreadCount = notificationRepository.countByReceiverIdAndReadFalse(userId);
        return Map.of(
                "notifications", notifications,
                "unreadCount", unreadCount
        );
    }

    public void markRead(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getReceiverId().equals(userId)) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    public void markAllRead(Long userId) {
        List<Notification> unread = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(n -> !n.isRead())
                .toList();

        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    public void deleteNotification(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getReceiverId().equals(userId)) {
                notificationRepository.delete(n);
            }
        });
    }
}
