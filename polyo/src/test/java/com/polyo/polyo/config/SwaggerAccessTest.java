package com.polyo.polyo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.polyo.polyo.auth.AuthController;
import com.polyo.polyo.auth.AuthService;
import com.polyo.polyo.auth.JwtTokenProvider;
import com.polyo.polyo.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    classes = SwaggerAccessTest.WebTestApplication.class,
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
    })
@AutoConfigureMockMvc
class SwaggerAccessTest {
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @Import({OpenApiConfig.class, SecurityConfig.class, JwtTokenProvider.class, AuthController.class})
  static class WebTestApplication {}

  @MockitoBean AuthService authService;

  @Autowired MockMvc mvc;

  @Test
  void swaggerUiIsReachableWithoutLogin() throws Exception {
    var result = mvc.perform(get("/swagger-ui/index.html")).andReturn();
    assertEquals(200, result.getResponse().getStatus());
  }

  @Test
  void allGroupDocumentsApiWithBearerScheme() throws Exception {
    var result = mvc.perform(get("/v3/api-docs/all")).andReturn();
    assertEquals(200, result.getResponse().getStatus());
    var document = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
    assertTrue(document.path("paths").has("/api/v1/auth/login"));
    assertEquals(
        "bearer",
        document.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asString());
  }
}
