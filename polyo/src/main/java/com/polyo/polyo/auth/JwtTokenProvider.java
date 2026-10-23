package com.polyo.polyo.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long expiresInSeconds;

    public JwtTokenProvider(
            @Value("${app.auth.jwt-secret:polyo-development-secret-key-change-me-please}") String secret,
            @Value("${app.auth.jwt-expiration-seconds:3600}") long expiresInSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiresInSeconds = expiresInSeconds;
    }

    public String create(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.id())
                .claim("loginId", user.loginId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                .signWith(key)
                .compact();
    }

    public long expiresInSeconds() { return expiresInSeconds; }
}
