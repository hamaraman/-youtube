package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.entity.Subscription;
import com.example.demo.repository.SubscriptionRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final LoginUserResolver loginUserResolver;

    public SubscriptionController(SubscriptionRepository subscriptionRepository,
                                  LoginUserResolver loginUserResolver) {
        this.subscriptionRepository = subscriptionRepository;
        this.loginUserResolver = loginUserResolver;
    }

    @PostMapping("/{id}/subscribe")
    public ResponseEntity<?> toggleSubscribe(@PathVariable Long id, HttpSession session) {
        Long loginUserId = loginUserResolver.getUserId(session);
        if (loginUserId == null) {
            return ResponseEntity.status(401).body(new SubscribeResponse(false, false, 0));
        }
        if (loginUserId.equals(id)) {
            return ResponseEntity.badRequest().body(new SubscribeResponse(false, false, 0));
        }

        Optional<Subscription> existing = subscriptionRepository.findBySubscriberIdAndChannelOwnerId(loginUserId, id);
        boolean subscribed;
        if (existing.isPresent()) {
            subscriptionRepository.delete(existing.get());
            subscribed = false;
        } else {
            Subscription sub = new Subscription();
            sub.setSubscriberId(loginUserId);
            sub.setChannelOwnerId(id);
            subscriptionRepository.save(sub);
            subscribed = true;
        }

        long count = subscriptionRepository.countByChannelOwnerId(id);
        return ResponseEntity.ok(new SubscribeResponse(true, subscribed, count));
    }

    @GetMapping("/{id}/subscription-status")
    public ResponseEntity<?> getSubscriptionStatus(@PathVariable Long id, HttpSession session) {
        Long loginUserId = loginUserResolver.getUserId(session);
        long count = subscriptionRepository.countByChannelOwnerId(id);
        boolean subscribed = loginUserId != null &&
                subscriptionRepository.existsBySubscriberIdAndChannelOwnerId(loginUserId, id);
        return ResponseEntity.ok(new SubscribeResponse(true, subscribed, count));
    }

    public static class SubscribeResponse {
        private boolean success;
        private boolean subscribed;
        private long subscriberCount;

        public SubscribeResponse(boolean success, boolean subscribed, long subscriberCount) {
            this.success = success;
            this.subscribed = subscribed;
            this.subscriberCount = subscriberCount;
        }

        public boolean isSuccess() { return success; }
        public boolean isSubscribed() { return subscribed; }
        public long getSubscriberCount() { return subscriberCount; }
    }
}
