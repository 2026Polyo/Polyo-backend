package com.polyo.polyo.credit.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.polyo.polyo.auth.JwtTokenProvider;
import com.polyo.polyo.common.GlobalExceptionHandler;
import com.polyo.polyo.common.api.ApiExceptionHandler;
import com.polyo.polyo.score.ScoreController;
import com.polyo.polyo.score.ScoreService;
import com.polyo.polyo.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** 실제 웹/보안 설정을 사용한다. DB 통합은 팀의 Testcontainers 테스트에서 별도 검증한다. */
@SpringBootTest(
    classes = CreditOpenApiTest.WebTestApplication.class,
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
    })
@AutoConfigureMockMvc
class CreditOpenApiTest {
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @Import({
    CreditController.class,
    CreditOpenApiConfiguration.class,
    ApiExceptionHandler.class,
    CreditRoutingExceptionHandler.class,
    GlobalExceptionHandler.class,
    SecurityConfig.class,
    JwtTokenProvider.class,
    ScoreController.class
  })
  static class WebTestApplication {}

  @MockitoBean ScoreService scoreService;

  @Test
  void creditAdviceDoesNotReplaceExistingDomainValidationContract() throws Exception {
    var result =
        mvc.perform(
                post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"gameMode\":\"normal\",\"score\":-1}")
                    .with(user("1")))
            .andReturn();
    assertEquals(400, result.getResponse().getStatus());
    var response = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
    assertEquals("INVALID_REQUEST", response.path("error").path("code").asString());
  }

  @Autowired MockMvc mvc;

  @Test
  void generatesCreditPathsRequestsAndResponsesFromControllerAnnotations() throws Exception {
    var result = mvc.perform(get("/v3/api-docs/credit").with(user("docs-tester"))).andReturn();
    assertEquals(200, result.getResponse().getStatus());
    var document = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
    var paths = document.path("paths");
    assertTrue(paths.has("/api/credit/v11/balances/me"));
    var reward = paths.path("/api/credit/v11/game-rewards").path("post");
    assertTrue(reward.has("requestBody"));
    for (String status :
        new String[] {"200", "201", "400", "401", "403", "404", "409", "503", "500"}) {
      assertTrue(reward.path("responses").has(status), "Missing response documentation: " + status);
    }
    assertTrue(reward.path("description").asString().contains("인증 필수"));
  }

  @Test
  void unknownPathsRemain404InsteadOfBeingConvertedTo500() throws Exception {
    var result =
        mvc.perform(get("/api/credit/v11/missing-resource").with(user("docs-tester"))).andReturn();
    assertEquals(404, result.getResponse().getStatus());
    var error = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
    assertEquals("API_NOT_FOUND", error.path("error").path("code").asString());
  }

  @Test
  void unauthenticatedRequestsRemainBlockedByExistingSecurity() throws Exception {
    var result = mvc.perform(get("/api/credit/v11/balances/me")).andReturn();
    assertEquals(401, result.getResponse().getStatus());
  }
}
