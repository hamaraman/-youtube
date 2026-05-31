package com.example.demo.config;

import com.example.demo.entity.PasswordResetToken;
import com.example.demo.repository.PasswordResetTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class PasswordResetTokenStore {

    private static final int EXPIRY_MINUTES = 10;

    private final PasswordResetTokenRepository repository;

    public PasswordResetTokenStore(PasswordResetTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String create(Long userId) {
        // 기존 토큰 제거 (유저당 하나만 유지)
        repository.deleteByUserId(userId);
        // 만료된 토큰도 정리
        repository.deleteExpired(LocalDateTime.now());

        PasswordResetToken prt = new PasswordResetToken();
        prt.setUserId(userId);
        prt.setToken(UUID.randomUUID().toString());
        prt.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
        repository.save(prt);
        return prt.getToken();
    }

    public Long validate(String token) {
        return repository.findByToken(token)
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(PasswordResetToken::getUserId)
                .orElse(null);
    }

    @Transactional
    public void remove(String token) {
        repository.findByToken(token).ifPresent(repository::delete);
    }
}
