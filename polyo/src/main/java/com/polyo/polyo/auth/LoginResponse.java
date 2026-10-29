package com.polyo.polyo.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
