package com.polyo.polyo.auth;

public record AuthUser(String id, String loginId, String passwordHash, String nickname) {
}
