package com.polyo.polyo.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validatePassword(request.password());
        validateNickname(request.nickname());
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new AuthException("DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디입니다.", 409);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new AuthException("DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.", 409);
        }
        try {
            User user = userRepository.save(User.create(
                    request.loginId(), passwordEncoder.encode(request.password()), request.nickname()));
            return new SignupResponse(String.valueOf(user.getId()), user.getLoginId(), user.getNickname());
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException("DUPLICATE_USER_INFO", "이미 사용 중인 아이디 또는 닉네임입니다.", 409);
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthException("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 일치하지 않습니다.", 401);
        }
        return new LoginResponse(tokenProvider.create(user), "Bearer", tokenProvider.expiresInSeconds(),
                new UserResponse(String.valueOf(user.getId()), user.getLoginId(), user.getNickname()));
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
