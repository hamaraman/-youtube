package com.example.demo.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-must-be-32-bytes-or-more-please";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET);
    }

    @Test
    void generateToken_returnsNonEmptyString() {
        String token = jwtUtil.generateToken(1L, "user", "nick", "u@e.com", "ch", "img.png", "USER");
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void parseToken_returnsAllClaims() {
        String token = jwtUtil.generateToken(42L, "alice", "앨리스", "alice@e.com", "Alice TV", "avatar.png", "ADMIN");

        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("username", String.class)).isEqualTo("alice");
        assertThat(claims.get("nickname", String.class)).isEqualTo("앨리스");
        assertThat(claims.get("email", String.class)).isEqualTo("alice@e.com");
        assertThat(claims.get("channelName", String.class)).isEqualTo("Alice TV");
        assertThat(claims.get("profileImage", String.class)).isEqualTo("avatar.png");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void generateToken_nullRole_defaultsToUser() {
        String token = jwtUtil.generateToken(1L, "u", "n", "e", "c", "i", null);
        Claims claims = jwtUtil.parseToken(token);
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void isValid_returnsTrueForFreshToken() {
        String token = jwtUtil.generateToken(1L, "u", "n", "e", "c", "i", "USER");
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void isValid_returnsFalseForGarbage() {
        assertThat(jwtUtil.isValid("this-is-not-a-jwt")).isFalse();
        assertThat(jwtUtil.isValid("")).isFalse();
    }

    @Test
    void isValid_returnsFalseWhenSecretDiffers() {
        String token = jwtUtil.generateToken(1L, "u", "n", "e", "c", "i", "USER");
        JwtUtil otherSecretUtil = new JwtUtil("another-secret-that-is-also-32-bytes-or-more-different!!");
        assertThat(otherSecretUtil.isValid(token)).isFalse();
    }

    @Test
    void token_expirationIsOneHourFromNow() {
        long before = System.currentTimeMillis();
        String token = jwtUtil.generateToken(1L, "u", "n", "e", "c", "i", "USER");
        long after = System.currentTimeMillis();

        Claims claims = jwtUtil.parseToken(token);
        long expiration = claims.getExpiration().getTime();

        assertThat(expiration).isBetween(before + 3_600_000L - 1000, after + 3_600_000L + 1000);
    }
}
