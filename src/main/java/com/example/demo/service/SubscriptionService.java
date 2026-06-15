package com.example.demo.service;

import com.example.demo.controller.AuthController.SessionUser;
import com.example.demo.controller.SubscriptionController.SubscribeResponse;
import com.example.demo.entity.Subscription;
import com.example.demo.repository.SubscriptionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VideoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final NotificationService notificationService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               UserRepository userRepository,
                               VideoRepository videoRepository,
                               NotificationService notificationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.notificationService = notificationService;
    }

    public SubscribeResponse toggleSubscribe(Long targetChannelOwnerId, SessionUser sessionUser) {
        if (sessionUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Long loginUserId = sessionUser.getId();
        if (loginUserId.equals(targetChannelOwnerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신을 구독할 수 없습니다.");
        }

        Optional<Subscription> existing = subscriptionRepository.findBySubscriberIdAndChannelOwnerId(loginUserId, targetChannelOwnerId);
        boolean subscribed;
        if (existing.isPresent()) {
            subscriptionRepository.delete(existing.get());
            subscribed = false;
        } else {
            Subscription sub = new Subscription();
            sub.setSubscriberId(loginUserId);
            sub.setChannelOwnerId(targetChannelOwnerId);
            subscriptionRepository.save(sub);
            subscribed = true;
            String name = sessionUser.getChannelName() != null && !sessionUser.getChannelName().isBlank()
                    ? sessionUser.getChannelName() : sessionUser.getNickname();
            notificationService.send(targetChannelOwnerId, loginUserId, "SUBSCRIBE",
                    name + "님이 구독했어요", null, null);
        }

        long count = subscriptionRepository.countByChannelOwnerId(targetChannelOwnerId);
        return new SubscribeResponse(true, subscribed, count);
    }

    public List<Map<String, Object>> searchChannels(String keyword, Long loginUserId) {
        if (keyword == null || keyword.isBlank()) return List.of();

        return userRepository.searchByKeyword(keyword.trim())
                .stream()
                .map(user -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", user.getId());
                    m.put("channelName", user.getChannelName() != null ? user.getChannelName() : user.getNickname());
                    m.put("username", user.getUsername());
                    m.put("profileImage", user.getProfileImage());
                    m.put("subscriberCount", subscriptionRepository.countByChannelOwnerId(user.getId()));
                    m.put("videoCount", videoRepository.countPublicByOwnerId(user.getId()));
                    m.put("subscribed", loginUserId != null &&
                            subscriptionRepository.existsBySubscriberIdAndChannelOwnerId(loginUserId, user.getId()));
                    return m;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getMySubscriptions(Long loginUserId) {
        return subscriptionRepository.findBySubscriberId(loginUserId)
                .stream()
                .map(sub -> userRepository.findById(sub.getChannelOwnerId()).map(user -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("channelOwnerId", user.getId());
                    m.put("channelName", user.getChannelName() != null ? user.getChannelName() : user.getNickname());
                    m.put("profileImage", user.getProfileImage());
                    return m;
                }).orElse(null))
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }

    public SubscribeResponse getSubscriptionStatus(Long channelOwnerId, Long loginUserId) {
        long count = subscriptionRepository.countByChannelOwnerId(channelOwnerId);
        boolean subscribed = loginUserId != null &&
                subscriptionRepository.existsBySubscriberIdAndChannelOwnerId(loginUserId, channelOwnerId);
        return new SubscribeResponse(true, subscribed, count);
    }
}
