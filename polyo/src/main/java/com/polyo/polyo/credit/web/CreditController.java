package com.polyo.polyo.credit.web;

import static com.polyo.polyo.common.error.CommonErrorCode.AUTH_UNAUTHORIZED;
import static com.polyo.polyo.credit.exception.CreditErrorCode.*;

import com.polyo.polyo.common.api.ApiFailure;
import com.polyo.polyo.common.api.ApiSuccess;
import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.credit.dto.CoinBalanceResponse;
import com.polyo.polyo.credit.dto.GameRewardRequest;
import com.polyo.polyo.credit.dto.GameRewardResponse;
import com.polyo.polyo.credit.exception.CreditException;
import com.polyo.polyo.credit.port.AuthenticatedUserProvider;
import com.polyo.polyo.credit.service.CreditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping(value = "/api/credit/v11", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "CREDIT", description = "인증 필수: 코인 조회·하드모드 정산")
@ApiResponses({
    @ApiResponse(
            responseCode = "401",
            description = "AUTH_UNAUTHORIZED",
            content = @Content(schema = @Schema(implementation = ApiFailure.class))),
    @ApiResponse(
            responseCode = "403",
            description = "ACCESS_DENIED / CREDIT_GAME_ACCESS_DENIED",
            content = @Content(schema = @Schema(implementation = ApiFailure.class))),
    @ApiResponse(
            responseCode = "405",
            description = "METHOD_NOT_ALLOWED",
            content = @Content(schema = @Schema(implementation = ApiFailure.class))),
    @ApiResponse(
            responseCode = "503",
            description = "CREDIT_SERVICE_UNAVAILABLE / IDENTITY_SERVICE_UNAVAILABLE",
            content = @Content(schema = @Schema(implementation = ApiFailure.class))),
    @ApiResponse(
            responseCode = "500",
            description = "INTERNAL_SERVER_ERROR 또는 CREDIT 내부 오류",
            content = @Content(schema = @Schema(implementation = ApiFailure.class)))
})
public class CreditController {
    private final Optional<CreditService> creditService;
    private final Optional<AuthenticatedUserProvider> users;

    /** 미연결 상태에서 임시 성공 Bean을 만들지 않고 요청에 503을 반환한다. */
    public CreditController(
            Optional<CreditService> creditService, Optional<AuthenticatedUserProvider> users) {
        this.creditService = creditService;
        this.users = users;
    }

    @GetMapping("/balances/me")
    @Operation(
            summary = "보유 코인 조회",
            description = "인증 필수. 검증된 현재 사용자의 코인만 조회한다. 인증 전달 방식은 Identity 공통 설정과 연결한다.")
    @Parameter(
            name = "Trace-Id",
            in = ParameterIn.HEADER,
            description = "요청 추적 ID. Gateway 미연결 시 없으면 서버가 생성한다.")
    @ApiResponse(
            responseCode = "200",
            description = "잔액 조회 성공",
            content =
                    @Content(
                            examples =
                                    @ExampleObject(
                                            value = "{\"success\":true,\"data\":{\"coin\":100}}")))
    public ResponseEntity<ApiSuccess<CoinBalanceResponse>> balance(HttpServletRequest request) {
        String userId = requireUser(request);
        return ResponseEntity.ok(
                ApiSuccess.of(new CoinBalanceResponse(service().getCoinBalance(userId))));
    }

    @PostMapping(value = "/game-rewards", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "하드모드 코인 보상 정산",
            description = "인증 필수. 서버 게임 ID로 정산한다. 점수·사용자 ID·지급량은 입력받지 않는다. 같은 게임 재요청은 기존 정산을 반환한다.")
    @Parameter(name = "Trace-Id", in = ParameterIn.HEADER, description = "요청 추적 ID")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "최초 정산",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"success\":true,\"data\":{\"rewardId\":\"reward_example\",\"gameId\":\"game_example\",\"earnedCoin\":100,\"coinAfterReward\":200}}"))),
        @ApiResponse(responseCode = "200", description = "기존 정산 반환"),
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST_BODY",
                content = @Content(schema = @Schema(implementation = ApiFailure.class))),
        @ApiResponse(
                responseCode = "404",
                description = "CREDIT_GAME_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiFailure.class))),
        @ApiResponse(
                responseCode = "409",
                description =
                        "CREDIT_GAME_NOT_ELIGIBLE / CREDIT_GAME_NOT_FINISHED / CREDIT_GAME_RESULT_NOT_VERIFIED",
                content = @Content(schema = @Schema(implementation = ApiFailure.class)))
    })
    public ResponseEntity<ApiSuccess<GameRewardResponse>> reward(
            @Valid @RequestBody GameRewardRequest body, HttpServletRequest request) {
        String userId = requireUser(request);
        var result = service().settleGameReward(userId, body.gameId());
        return ResponseEntity.status(result.created() ? 201 : 200)
                .body(ApiSuccess.of(GameRewardResponse.from(result.settlement())));
    }

    private String requireUser(HttpServletRequest request) {
        var provider = users.orElseThrow(() -> new CreditException(IDENTITY_SERVICE_UNAVAILABLE));
        String userId = provider.requireUserId();
        if (userId == null || userId.isBlank()) {
            throw new ApiException(AUTH_UNAUTHORIZED);
        }
        request.setAttribute(CreditRequestLoggingFilter.USER_ATTRIBUTE, userId);
        return userId;
    }

    private CreditService service() {
        return creditService.orElseThrow(() -> new CreditException(CREDIT_SERVICE_UNAVAILABLE));
    }
}
