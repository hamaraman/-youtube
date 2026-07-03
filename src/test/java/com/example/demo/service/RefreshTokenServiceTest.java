package com.example.demo.service;

import com.example.demo.entity.RefreshToken;
import com.example.demo.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository repository;
    @InjectMocks private RefreshTokenService service;

    @Test
    void createRefreshToken_removesExistingAndExpiredBeforeInsert() {
        service.createRefreshToken(10L);

        var inOrder = inOrder(repository);
        inOrder.verify(repository).deleteByUserId(10L);
        inOrder.verify(repository).deleteExpired(any(LocalDateTime.class));
        inOrder.verify(repository).save(any(RefreshToken.class));
    }

    @Test
    void createRefreshToken_persistsTokenWithSevenDayExpiry() {
        LocalDateTime before = LocalDateTime.now();
        service.createRefreshToken(10L);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(10L);
        assertThat(saved.getToken()).isNotBlank();
        long expiryDays = ChronoUnit.DAYS.between(before.minusSeconds(1), saved.getExpiresAt());
        assertThat(expiryDays).isBetween(6L, 7L);
        assertThat(saved.getExpiresAt()).isAfter(after);
    }

    @Test
    void createRefreshToken_returnsGeneratedTokenValue() {
        String token = service.createRefreshToken(10L);
        assertThat(token).isNotBlank();
        assertThat(token).hasSize(36);
    }

    @Test
    void createRefreshToken_generatesUniqueTokens() {
        String t1 = service.createRefreshToken(10L);
        String t2 = service.createRefreshToken(10L);
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void validateRefreshToken_valid_returnsUserId() {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(10L);
        rt.setToken("abc");
        rt.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(repository.findByToken("abc")).thenReturn(Optional.of(rt));

        assertThat(service.validateRefreshToken("abc")).isEqualTo(10L);
    }

    @Test
    void validateRefreshToken_expired_returnsNull() {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(10L);
        rt.setToken("abc");
        rt.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByToken("abc")).thenReturn(Optional.of(rt));

        assertThat(service.validateRefreshToken("abc")).isNull();
    }

    @Test
    void validateRefreshToken_missing_returnsNull() {
        when(repository.findByToken("nope")).thenReturn(Optional.empty());
        assertThat(service.validateRefreshToken("nope")).isNull();
    }

    @Test
    void removeRefreshToken_existing_deletes() {
        RefreshToken rt = new RefreshToken();
        rt.setToken("abc");
        when(repository.findByToken("abc")).thenReturn(Optional.of(rt));

        service.removeRefreshToken("abc");

        verify(repository).delete(rt);
    }

    @Test
    void removeRefreshToken_missing_noop() {
        when(repository.findByToken("nope")).thenReturn(Optional.empty());

        service.removeRefreshToken("nope");

        verify(repository, never()).delete(any());
    }
}
