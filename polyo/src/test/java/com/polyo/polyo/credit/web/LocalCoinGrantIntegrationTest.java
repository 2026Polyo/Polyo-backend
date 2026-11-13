package com.polyo.polyo.credit.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.polyo.polyo.TestcontainersConfiguration;
import com.polyo.polyo.auth.JwtTokenProvider;
import com.polyo.polyo.auth.User;
import com.polyo.polyo.auth.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("local-coin-grants")
class LocalCoinGrantIntegrationTest {
    private static final String SHOP = "/api/shop/v11";

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;

    @Test
    void grantsOnlyToTheAuthenticatedAccountAndPersistsTheBalance() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        User player = users.saveAndFlush(User.create("grant-" + id, "unused-password", "grant-" + id));
        String authorization = "Bearer " + tokens.create(player);

        mvc.perform(post(SHOP + "/local-coin-grants")).andExpect(status().isUnauthorized());
        mvc.perform(post(SHOP + "/local-coin-grants").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.coin").value(1000));
        mvc.perform(post(SHOP + "/local-coin-grants").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.coin").value(2000));
        mvc.perform(get(SHOP + "/accounts/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.coin").value(2000));
    }
}
