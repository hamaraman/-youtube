package com.example.demo.service;

import com.example.demo.entity.RefreshToken;
import com.example.demo.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final int EXPIRY_DAYS = 7; // 7일

    private final RefreshTokenRepository repository;

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String createRefreshToken(Long userId) {
        repository.deleteByUserId(userId);
        repository.deleteExpired(LocalDateTime.now());

        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setToken(UUID.randomUUID().toString());
        rt.setExpiresAt(LocalDateTime.now().plusDays(EXPIRY_DAYS));
        repository.save(rt);

        return rt.getToken();
    }

    public Long validateRefreshToken(String token) {
        return repository.findByToken(token)
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(RefreshToken::getUserId)
                .orElse(null);
    }

    @Transactional
    public void removeRefreshToken(String token) {
        repository.findByToken(token).ifPresent(repository::delete);
    }
}
