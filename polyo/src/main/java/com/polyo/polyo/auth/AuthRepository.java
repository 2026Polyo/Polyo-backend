package com.polyo.polyo.auth;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {
    private final AtomicLong sequence = new AtomicLong(0);
    private final ConcurrentHashMap<String, AuthUser> usersByLoginId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> loginIdByNickname = new ConcurrentHashMap<>();

    public boolean existsByLoginId(String loginId) {
        return usersByLoginId.containsKey(loginId);
    }

    public boolean existsByNickname(String nickname) {
        return loginIdByNickname.containsKey(nickname);
    }

    public AuthUser save(String loginId, String passwordHash, String nickname) {
        AuthUser user = new AuthUser(String.valueOf(sequence.incrementAndGet()), loginId, passwordHash, nickname);
        usersByLoginId.put(loginId, user);
        loginIdByNickname.put(nickname, loginId);
        return user;
    }

    public AuthUser findByLoginId(String loginId) {
        return usersByLoginId.get(loginId);
    }
}
