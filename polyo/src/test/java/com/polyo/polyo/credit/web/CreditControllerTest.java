package com.polyo.polyo.credit.web;

import static com.polyo.polyo.common.error.CommonErrorCode.*;
import static com.polyo.polyo.credit.domain.RecordedGameResult.Mode.*;
import static com.polyo.polyo.credit.exception.CreditErrorCode.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.polyo.polyo.common.api.ApiExceptionHandler;
import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.credit.domain.GameRewardResult;
import com.polyo.polyo.credit.domain.RecordedGameResult;
import com.polyo.polyo.credit.port.AuthenticatedUserProvider;
import com.polyo.polyo.credit.service.CreditService;
import com.polyo.polyo.credit.service.DefaultCreditService;
import com.polyo.polyo.credit.service.DefaultHardModeRewardPolicy;
import com.polyo.polyo.credit.service.ValidatingHardModeGameResultReader;
import com.polyo.polyo.credit.support.AtomicTestCreditStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** HTTP 계약 + 실제 정산 코어, 인증/게임 기록/DB는 테스트 대체물. Security 필터 통합은 별도 테스트. */
class CreditControllerTest {
    private static final String BALANCE = "/api/credit/v11/balances/me";
    private static final String REWARDS = "/api/credit/v11/game-rewards";
    private final JsonMapper json = JsonMapper.builder().build();
    private AtomicTestCreditStore store;
    private Map<String, RecordedGameResult> games;
    private CreditService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        store = new AtomicTestCreditStore();
        store.setBalance("user_1", 100);
        games = new HashMap<>();
        games.put("game_1", new RecordedGameResult("game_1", "user_1", HARD, true, true, 5000));
        service =
                new DefaultCreditService(
                        new ValidatingHardModeGameResultReader(
                                id -> Optional.ofNullable(games.get(id))),
                        new DefaultHardModeRewardPolicy(),
                        store);
        mvc = create(Optional.of(service), Optional.of(() -> "user_1"));
    }

    private MockMvc create(
            Optional<CreditService> credit, Optional<AuthenticatedUserProvider> users) {
        return MockMvcBuilders.standaloneSetup(new CreditController(credit, users))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new CreditRequestLoggingFilter())
                .build();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private void error(MvcResult result, int status, String code) throws Exception {
        assertEquals(status, result.getResponse().getStatus());
        var root = body(result);
        assertFalse(root.path("success").asBoolean());
        assertEquals(code, root.path("error").path("code").asString());
        assertEquals(status, root.path("error").path("status").asInt());
        assertNotNull(Instant.parse(root.path("timestamp").asString()));
        assertFalse(root.has("data"));
    }

    private MvcResult claim(String id) throws Exception {
        return mvc.perform(
                        post(REWARDS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"gameId\":\"" + id + "\"}"))
                .andReturn();
    }

    @Test
    void readsOnlyTheAuthenticatedUsersBalanceAndIgnoresForgedIdentityHeaders() throws Exception {
        store.setBalance("attacker", 9999);
        var result = mvc.perform(get(BALANCE).header("X-User-Id", "attacker")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(body(result).path("success").asBoolean());
        assertEquals(100, body(result).path("data").path("coin").asLong());
    }

    @Test
    void firstClaimIs201AndRetryIs200WithTheSameReceipt() throws Exception {
        var first = claim("game_1");
        var repeated = claim("game_1");
        assertEquals(201, first.getResponse().getStatus());
        assertEquals(200, repeated.getResponse().getStatus());
        assertEquals(body(first).path("data"), body(repeated).path("data"));
        var data = body(first).path("data");
        assertEquals("game_1", data.path("gameId").asString());
        assertEquals(100, data.path("earnedCoin").asLong());
        assertEquals(200, data.path("coinAfterReward").asLong());
        assertFalse(data.has("userId"));
        assertEquals(1, store.settlementCount());
        assertEquals(200, store.getCoinBalance("user_1"));
    }

    @Test
    void missingNullOrBlankGameIdsReturn400WithoutPaying() throws Exception {
        for (String payload :
                new String[] {"{}", "{\"gameId\":null}", "{\"gameId\":\"   \"}", ""}) {
            error(
                    mvc.perform(
                                    post(REWARDS)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(payload))
                            .andReturn(),
                    400,
                    "INVALID_REQUEST_BODY");
        }
        assertEquals(0, store.settlementCount());
    }

    @Test
    void malformedJsonIsACommon400Response() throws Exception {
        error(
                mvc.perform(
                                post(REWARDS)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"gameId\":"))
                        .andReturn(),
                400,
                "INVALID_REQUEST_BODY");
    }

    @Test
    void unsupportedContentTypeIsARequestBodyError() throws Exception {
        error(
                mvc.perform(post(REWARDS).contentType(MediaType.TEXT_PLAIN).content("game_1"))
                        .andReturn(),
                400,
                "INVALID_REQUEST_BODY");
    }

    @Test
    void unsupportedHttpMethodIsACommon405Response() throws Exception {
        var result = mvc.perform(put(BALANCE)).andReturn();
        error(result, 405, "METHOD_NOT_ALLOWED");
        assertTrue(result.getResponse().getHeader("Allow").contains("GET"));
    }

    @Test
    void normalModeNeverPaysAndReturns409() throws Exception {
        games.put("game_1", new RecordedGameResult("game_1", "user_1", NORMAL, true, true, 100000));
        error(claim("game_1"), 409, "CREDIT_GAME_NOT_ELIGIBLE");
        assertEquals(100, store.getCoinBalance("user_1"));
        assertEquals(0, store.settlementCount());
    }

    @Test
    void anotherUsersGameIs403() throws Exception {
        games.put("game_1", new RecordedGameResult("game_1", "user_2", HARD, true, true, 5000));
        error(claim("game_1"), 403, "CREDIT_GAME_ACCESS_DENIED");
        assertEquals(0, store.settlementCount());
    }

    @Test
    void missingGameIs404() throws Exception {
        error(claim("missing"), 404, "CREDIT_GAME_NOT_FOUND");
    }

    @Test
    void unfinishedAndUnverifiedGamesReturnDistinct409Errors() throws Exception {
        games.put("game_1", new RecordedGameResult("game_1", "user_1", HARD, false, true, 5000));
        error(claim("game_1"), 409, "CREDIT_GAME_NOT_FINISHED");
        games.put("game_1", new RecordedGameResult("game_1", "user_1", HARD, true, false, 5000));
        error(claim("game_1"), 409, "CREDIT_GAME_RESULT_NOT_VERIFIED");
        assertEquals(0, store.settlementCount());
    }

    @Test
    void identityProviderRejectionIs401() throws Exception {
        mvc =
                create(
                        Optional.of(service),
                        Optional.of(
                                () -> {
                                    throw new ApiException(AUTH_UNAUTHORIZED);
                                }));
        error(claim("game_1"), 401, "AUTH_UNAUTHORIZED");
        assertEquals(0, store.settlementCount());
    }

    @Test
    void emptyIdentityIsNotTreatedAsAnAuthenticatedUser() throws Exception {
        mvc = create(Optional.of(service), Optional.of(() -> " "));
        error(claim("game_1"), 401, "AUTH_UNAUTHORIZED");
    }

    @Test
    void roleRejectionUsesTheCommonAccessDeniedCode() throws Exception {
        mvc =
                create(
                        Optional.of(service),
                        Optional.of(
                                () -> {
                                    throw new ApiException(ACCESS_DENIED);
                                }));
        error(claim("game_1"), 403, "ACCESS_DENIED");
    }

    @Test
    void missingIdentityIntegrationDoesNotTrustClientHeaders() throws Exception {
        mvc = create(Optional.of(service), Optional.empty());
        error(
                mvc.perform(get(BALANCE).header("X-User-Id", "user_1")).andReturn(),
                503,
                "IDENTITY_SERVICE_UNAVAILABLE");
    }

    @Test
    void missingServiceReturns503InsteadOfAFakeBalanceOrReward() throws Exception {
        mvc = create(Optional.empty(), Optional.of(() -> "user_1"));
        error(mvc.perform(get(BALANCE)).andReturn(), 503, "CREDIT_SERVICE_UNAVAILABLE");
        error(claim("game_1"), 503, "CREDIT_SERVICE_UNAVAILABLE");
    }

    @Test
    void unexpectedErrorsDoNotLeakInternalMessages() throws Exception {
        CreditService broken =
                new CreditService() {
                    public long getCoinBalance(String user) {
                        throw new IllegalStateException("private-token-do-not-expose");
                    }

                    public GameRewardResult settleGameReward(String user, String game) {
                        throw new IllegalStateException("private-token-do-not-expose");
                    }
                };
        mvc = create(Optional.of(broken), Optional.of(() -> "user_1"));
        var result = claim("game_1");
        error(result, 500, "INTERNAL_SERVER_ERROR");
        assertFalse(result.getResponse().getContentAsString().contains("private-token"));
    }

    @Test
    void tracesArePreservedOrGeneratedAndUnsafeValuesAreNotReflected() throws Exception {
        var existing = mvc.perform(get(BALANCE).header("Trace-Id", "trace-test-1")).andReturn();
        assertEquals("trace-test-1", existing.getResponse().getHeader("Trace-Id"));
        var generated = mvc.perform(get(BALANCE)).andReturn();
        assertNotNull(UUID.fromString(generated.getResponse().getHeader("Trace-Id")));
        var unsafe = mvc.perform(get(BALANCE).header("Trace-Id", "x".repeat(129))).andReturn();
        assertNotNull(UUID.fromString(unsafe.getResponse().getHeader("Trace-Id")));
    }
}
