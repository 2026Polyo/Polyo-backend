package com.polyo.polyo.credit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.polyo.polyo.TestcontainersConfiguration;
import com.polyo.polyo.auth.JwtTokenProvider;
import com.polyo.polyo.auth.User;
import com.polyo.polyo.auth.UserRepository;
import com.polyo.polyo.credit.service.ThemeShop;
import com.polyo.polyo.game.HardModeGames;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CreditShopIntegrationTest {
    private static final String CREDIT = "/api/credit/v11";
    private static final String SHOP = "/api/shop/v11";

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    @Autowired ThemeShop shop;

    @Test
    void onlyFinishedServerGamePaysAndRepeatingARequestDoesNotPayAgain() throws Exception {
        User player = createUser(0);
        String token = auth(player);
        String body = mvc.perform(post(CREDIT + "/hard-games").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tray.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        String game = json.readTree(body).path("data").path("gameId").asText();
        String reward = "{\"gameId\":\"" + game + "\"}";
        claim(token, reward).andExpect(status().isConflict());
        claim(auth(createUser(0)), reward).andExpect(status().isForbidden());

        // Set up a near-complete line. The move endpoint still validates and scores the placement.
        String[][] board = new String[8][8];
        Arrays.fill(board[0], 0, 3, "cyan");
        var state = new HardModeGames.State(board, List.of("line-5-horizontal", "square-2x2", "monomino-single"),
                0, 0, false, 0, System.currentTimeMillis() + 10_000, 10_000, null, false, null);
        jdbc.sql("UPDATE hard_mode_games SET state = :state WHERE game_id = :game")
                .param("state", json.writeValueAsString(state)).param("game", game).update();
        String move = "{\"moveNumber\":0,\"slot\":0,\"row\":0,\"col\":3}";
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(CREDIT + "/hard-games/{game}/moves", game)
                            .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(move))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(50))
                    .andExpect(jsonPath("$.data.moveNumber").value(1));
        }
        jdbc.sql("""
                UPDATE hard_mode_games SET state = jsonb_set(state::jsonb, '{deadline}', '0')::text WHERE game_id = :game
                """).param("game", game).update();
        mvc.perform(put(CREDIT + "/hard-games/{game}/completion", game).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.finished").value(true));
        claim(token, reward).andExpect(status().isCreated()).andExpect(jsonPath("$.data.earnedCoin").value(1));
        claim(token, reward).andExpect(status().isOk()).andExpect(jsonPath("$.data.coinAfterReward").value(1));
        mvc.perform(get(CREDIT + "/balances/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.coin").value(1));
    }

    @Test
    void shopRejectsUnownedThemesAndDeductsOnlyOnceForConcurrentPurchases() throws Exception {
        User player = createUser(1_300);
        String token = auth(player);
        String theme = "{\"themeId\":\"theme_neon\"}";
        mvc.perform(get(SHOP + "/themes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4));
        mvc.perform(get(SHOP + "/accounts/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
        mvc.perform(put(SHOP + "/equipment/me").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(theme))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SHOP_THEME_NOT_OWNED"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> shop.purchase(player.getId(), "theme_neon"));
            var second = executor.submit(() -> shop.purchase(player.getId(), "theme_neon"));
            assertThat(first.get(5, TimeUnit.SECONDS).coin()).isEqualTo(100);
            assertThat(second.get(5, TimeUnit.SECONDS).coin()).isEqualTo(100);
        }
        mvc.perform(post(SHOP + "/purchases").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"themeId\":\"theme_jelly\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SHOP_INSUFFICIENT_COIN"));
        mvc.perform(put(SHOP + "/equipment/me").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(theme))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.equippedThemeId").value("theme_neon"));
        mvc.perform(get(SHOP + "/accounts/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.coin").value(100))
                .andExpect(jsonPath("$.data.ownedThemeIds[0]").value("theme_neon"));
    }

    private ResultActions claim(String token, String body) throws Exception {
        return mvc.perform(post(CREDIT + "/game-rewards").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private User createUser(int coin) {
        String key = UUID.randomUUID().toString().substring(0, 12);
        User user = users.saveAndFlush(User.create("credit-" + key, "test-password-hash", "credit-" + key));
        jdbc.sql("UPDATE users SET coin = :coin WHERE id = :id").param("coin", coin).param("id", user.getId()).update();
        return user;
    }

    private String auth(User user) {
        return "Bearer " + tokens.create(user);
    }
}
