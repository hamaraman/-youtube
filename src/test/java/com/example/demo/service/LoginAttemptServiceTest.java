package com.example.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void newKey_isNotBlocked() {
        assertThat(service.isBlocked("192.168.0.1")).isFalse();
        assertThat(service.blockRemainingSeconds("192.168.0.1")).isZero();
    }

    @Test
    void fewFailures_doNotBlock() {
        String key = "1.2.3.4";
        for (int i = 0; i < 4; i++) service.recordFailure(key);
        assertThat(service.isBlocked(key)).isFalse();
    }

    @Test
    void fiveFailures_triggerBlock() {
        String key = "1.2.3.4";
        for (int i = 0; i < 5; i++) service.recordFailure(key);
        assertThat(service.isBlocked(key)).isTrue();
        assertThat(service.blockRemainingSeconds(key)).isGreaterThan(0);
    }

    @Test
    void recordSuccess_clearsAttempts() {
        String key = "1.2.3.4";
        for (int i = 0; i < 5; i++) service.recordFailure(key);
        assertThat(service.isBlocked(key)).isTrue();

        service.recordSuccess(key);

        assertThat(service.isBlocked(key)).isFalse();
        assertThat(service.blockRemainingSeconds(key)).isZero();
    }

    @Test
    void differentKeys_areIndependent() {
        String a = "1.1.1.1";
        String b = "2.2.2.2";
        for (int i = 0; i < 5; i++) service.recordFailure(a);

        assertThat(service.isBlocked(a)).isTrue();
        assertThat(service.isBlocked(b)).isFalse();
    }

    @Test
    void blockRemainingSeconds_isCloseTo15Minutes() {
        String key = "1.2.3.4";
        for (int i = 0; i < 5; i++) service.recordFailure(key);
        long remaining = service.blockRemainingSeconds(key);
        assertThat(remaining).isBetween(15L * 60 - 5, 15L * 60);
    }
}
