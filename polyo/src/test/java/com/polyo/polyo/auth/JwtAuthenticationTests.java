package com.polyo.polyo.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.polyo.polyo.TestcontainersConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JwtAuthenticationTests {
    private static final String ROOT = "/api/match-room";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${app.auth.jwt-secret}") String secret;

    @Test
    void loginTokenAuthenticatesAsTheLoggedInUser() throws Exception {
        JsonNode login = signupAndLogin();
        String token = login.path("accessToken").asText();
        String userId = login.path("user").path("id").asText();
        String nickname = login.path("user").path("nickname").asText();

        mvc.perform(get(ROOT).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(post(ROOT).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("roomName", "토큰 방", "isPrivate", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hostUserId").value(userId))
                .andExpect(jsonPath("$.data.members[0].nickname").value(nickname));
    }

    @Test
    void rejectsInvalidTokens() throws Exception {
        String valid = signupAndLogin().path("accessToken").asText();
        String expired = token("1", "만료", -60);
        String withoutNickname = token("1", null, 60);
        String forged = Jwts.builder().subject("1").claim("nickname", "위조")
                .signWith(Keys.hmacShaKeyFor("another-secret-key-another-secret-key-1234".getBytes(StandardCharsets.UTF_8)))
                .compact();

        for (String header : new String[] {"Bearer " + expired, "Bearer " + withoutNickname, "Bearer " + forged,
                "Bearer not-a-token", "Bearer ", "Basic " + valid}) {
            for (String path : new String[] {ROOT, "/api/v1/scores/me"}) {
                mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, header))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
            }
        }
    }

    @Test
    void loginTokenKeepsScoreApisBoundToTheLoggedInUser() throws Exception {
        String token = signupAndLogin().path("accessToken").asText();
        String otherToken = signupAndLogin().path("accessToken").asText();

        mvc.perform(post("/api/v1/scores").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("gameMode", "normal", "score", 1234))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bestScore").value(1234));
        mvc.perform(post("/api/v1/scores").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("gameMode", "hard", "score", 4321))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bestScore").value(4321));
        mvc.perform(get("/api/v1/scores/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.normalBestScore").value(1234))
                .andExpect(jsonPath("$.data.hardBestScore").value(4321));
        mvc.perform(get("/api/v1/scores/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.normalBestScore").value(0))
                .andExpect(jsonPath("$.data.hardBestScore").value(0));
    }

    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void rejectsInvalidIdentityClaimsAcrossProtectedApis(String subject, String nickname) throws Exception {
        String header = "Bearer " + token(subject, nickname, 60);
        for (String path : new String[] {ROOT, "/api/v1/scores/me"}) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, header))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        }
    }

    private static Stream<Arguments> invalidIdentities() {
        return Stream.of(
                Arguments.of(null, "정상"),
                Arguments.of("", "정상"),
                Arguments.of(" ", "정상"),
                Arguments.of("user_1", "정상"),
                Arguments.of("0", "정상"),
                Arguments.of("-1", "정상"),
                Arguments.of("9223372036854775808", "정상"),
                Arguments.of("1", ""),
                Arguments.of("1", " "));
    }

    @Test
    void invalidTokenDoesNotBlockPublicEndpoints() throws Exception {
        mvc.perform(post("/api/v1/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("loginId", "nobody", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    private JsonNode signupAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String loginId = "jwt" + suffix;
        String password = "Test1234!" + suffix;
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("loginId", loginId, "password", password, "nickname", "n" + suffix))))
                .andExpect(status().isOk());
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("loginId", loginId, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response).path("data");
    }

    private String token(String subject, String nickname, long expiresInSeconds) {
        var builder = Jwts.builder().subject(subject)
                .expiration(new java.util.Date(System.currentTimeMillis() + expiresInSeconds * 1000));
        if (nickname != null) {
            builder.claim("nickname", nickname);
        }
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
