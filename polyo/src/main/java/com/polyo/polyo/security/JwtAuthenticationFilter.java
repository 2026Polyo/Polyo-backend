package com.polyo.polyo.security;

import com.polyo.polyo.auth.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// Not a bean: Spring Boot would also register it as a servlet filter outside the security chain.
class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            // A bad token only leaves the request anonymous, so public endpoints such as login still work.
            tokenProvider.parse(header.substring(BEARER_PREFIX.length())).ifPresent(user -> {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
