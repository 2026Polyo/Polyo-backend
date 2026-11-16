package com.polyo.polyo.auth;

import com.polyo.polyo.matchroom.MatchRoomUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long expiresInSeconds;

    public JwtTokenProvider(
            @Value("${app.auth.jwt-secret}") String secret,
            @Value("${app.auth.jwt-expiration-seconds}") long expiresInSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiresInSeconds = expiresInSeconds;
    }

    public String create(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("loginId", user.getLoginId())
                .claim("nickname", user.getNickname())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                .signWith(key)
                .compact();
    }

    /** Returns a verified user with a positive database ID and nonblank nickname. */
    public Optional<MatchRoomUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String nickname = claims.get("nickname", String.class);
            if (nickname == null || nickname.isBlank()) {
                return Optional.empty();
            }
            long userId = Long.parseLong(claims.getSubject());
            return userId > 0
                    ? Optional.of(new MatchRoomUser(String.valueOf(userId), nickname))
                    : Optional.empty();
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public long expiresInSeconds() { return expiresInSeconds; }

    public Long getUserId(String token) {
        return parse(token).map(user -> Long.valueOf(user.id())).orElse(null);
    }
}
