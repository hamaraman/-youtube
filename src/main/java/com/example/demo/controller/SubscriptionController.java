package com.example.demo.controller;

import com.example.demo.config.LoginUserResolver;
import com.example.demo.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final LoginUserResolver loginUserResolver;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  LoginUserResolver loginUserResolver) {
        this.subscriptionService = subscriptionService;
        this.loginUserResolver = loginUserResolver;
    }

    @PostMapping("/{id}/subscribe")
    public ResponseEntity<?> toggleSubscribe(@PathVariable Long id, HttpSession session) {
        AuthController.SessionUser sessionUser = loginUserResolver.getUser(session);
        try {
            SubscribeResponse response = subscriptionService.toggleSubscribe(id, sessionUser);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new SubscribeResponse(false, false, 0));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchChannels(@RequestParam String keyword, HttpSession session) {
        if (keyword == null || keyword.isBlank()) return ResponseEntity.ok(List.of());
        Long loginUserId = loginUserResolver.getUserId(session);
        return ResponseEntity.ok(subscriptionService.searchChannels(keyword, loginUserId));
    }

    @GetMapping("/me/subscriptions")
    public ResponseEntity<?> getMySubscriptions(HttpSession session) {
        Long loginUserId = loginUserResolver.getUserId(session);
        if (loginUserId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(subscriptionService.getMySubscriptions(loginUserId));
    }

    @GetMapping("/{id}/subscription-status")
    public ResponseEntity<?> getSubscriptionStatus(@PathVariable Long id, HttpSession session) {
        Long loginUserId = loginUserResolver.getUserId(session);
        return ResponseEntity.ok(subscriptionService.getSubscriptionStatus(id, loginUserId));
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
