
package com.polyo.polyo.security;

import com.polyo.polyo.auth.JwtTokenProvider;
import com.polyo.polyo.common.ApiResponse;
import com.polyo.polyo.common.api.ApiFailure;
import com.polyo.polyo.common.error.CommonErrorCode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonMapper jsonMapper,
            JwtTokenProvider tokenProvider
    ) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // The game socket authenticates with its first message, not with an HTTP header.
        .authorizeHttpRequests(auth -> auth
                // 회원가입 / 로그인은 비로그인 허용
                .requestMatchers("/api/v1/auth/signup", "/api/v1/auth/login").permitAll()
                // 게임 매치용 웹소켓 핸드셰이크는 허용
                .requestMatchers("/ws/match/*").permitAll()
                // 테마 목록 조회는 비로그인 허용
                .requestMatchers(HttpMethod.GET, "/api/shop/v11/themes").permitAll()
                // 나머지는 모두 인증 필요 (예: /api/v1/auth/me 등)
                .anyRequest().authenticated()
        )
        .addFilterBefore(
                new JwtAuthenticationFilter(tokenProvider),
                UsernamePasswordAuthenticationFilter.class
        )
        .exceptionHandling(exceptions ->
                exceptions.authenticationEntryPoint(unauthenticated(jsonMapper)))
        .build();

    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private AuthenticationEntryPoint unauthenticated(JsonMapper jsonMapper) {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.startsWith("/api/credit/v11/") || path.startsWith("/api/shop/v11/")) {
                jsonMapper.writeValue(response.getOutputStream(), ApiFailure.of(CommonErrorCode.AUTH_UNAUTHORIZED));
            } else {
                jsonMapper.writeValue(response.getOutputStream(),
                        ApiResponse.failure("UNAUTHENTICATED", "로그인이 필요합니다."));
            }
        };
    }
}
