package com.polyo.polyo.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(AuthRepository repository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public SignupResponse signup(SignupRequest request) {
        validatePassword(request.password());
        validateNickname(request.nickname());
        if (repository.existsByLoginId(request.loginId())) {
            throw new AuthException("DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디입니다.", 409);
        }
        if (repository.existsByNickname(request.nickname())) {
            throw new AuthException("DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.", 409);
        }
        AuthUser user = repository.save(request.loginId(), passwordEncoder.encode(request.password()), request.nickname());
        return new SignupResponse(user.id(), user.loginId(), user.nickname());
    }

    public LoginResponse login(LoginRequest request) {
        AuthUser user = repository.findByLoginId(request.loginId());
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new AuthException("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 일치하지 않습니다.", 401);
        }
        return new LoginResponse(tokenProvider.create(user), "Bearer", tokenProvider.expiresInSeconds(),
                new UserResponse(user.id(), user.loginId(), user.nickname()));
    }

    private void validatePassword(String password) {
        if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,64}$")) {
            throw new AuthException("INVALID_PASSWORD", "비밀번호 형식이 올바르지 않습니다.", 400);
        }
    }

    private void validateNickname(String nickname) {
        if (!nickname.matches("^[가-힣A-Za-z0-9]{2,20}$")) {
            throw new AuthException("INVALID_NICKNAME", "닉네임 형식이 올바르지 않습니다.", 400);
        }
    }
}
