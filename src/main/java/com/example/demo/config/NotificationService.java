package com.example.demo.config;

import com.example.demo.entity.Notification;
import com.example.demo.repository.NotificationRepository;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
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
    }
}
