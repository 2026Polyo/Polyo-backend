package com.polyo.polyo.credit.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

/** 실제 앱 컨텍스트에서 문서 생성과 기존 보안 유지 확인. 인증 구현을 대체하지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
class CreditOpenApiTest {
    @Autowired MockMvc mvc;

    @Test
    void generatesCreditPathsRequestsAndResponsesFromControllerAnnotations() throws Exception {
        var result = mvc.perform(get("/v3/api-docs/credit").with(user("docs-tester"))).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        var document =
                JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        var paths = document.path("paths");
        assertTrue(paths.has("/api/credit/v11/balances/me"));
        var reward = paths.path("/api/credit/v11/game-rewards").path("post");
        assertTrue(reward.has("requestBody"));
        for (String status :
                new String[] {"200", "201", "400", "401", "403", "404", "409", "503", "500"}) {
            assertTrue(
                    reward.path("responses").has(status),
                    "Missing response documentation: " + status);
        }
        assertTrue(reward.path("description").asString().contains("인증 필수"));
    }

    @Test
    void unknownPathsRemain404InsteadOfBeingConvertedTo500() throws Exception {
        var result =
                mvc.perform(get("/api/credit/v11/missing-resource").with(user("docs-tester")))
                        .andReturn();
        assertEquals(404, result.getResponse().getStatus());
        var error =
                JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        assertEquals("API_NOT_FOUND", error.path("error").path("code").asString());
    }

    @Test
    void unauthenticatedRequestsRemainBlockedByExistingSecurity() throws Exception {
        var result = mvc.perform(get("/api/credit/v11/balances/me")).andReturn();
        assertEquals(401, result.getResponse().getStatus());
    }
}
