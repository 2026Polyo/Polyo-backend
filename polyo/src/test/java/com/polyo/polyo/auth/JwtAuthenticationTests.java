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
import org.junit.jupiter.api.Test;
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
            mvc.perform(get(ROOT).header(HttpHeaders.AUTHORIZATION, header))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        }
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
