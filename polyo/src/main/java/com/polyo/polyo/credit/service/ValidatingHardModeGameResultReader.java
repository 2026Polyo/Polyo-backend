package com.polyo.polyo.credit.service;

import static com.polyo.polyo.common.error.CommonErrorCode.AUTH_UNAUTHORIZED;
import static com.polyo.polyo.common.error.CommonErrorCode.INVALID_REQUEST_BODY;
import static com.polyo.polyo.credit.exception.CreditErrorCode.*;

import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.credit.domain.RecordedGameResult;
import com.polyo.polyo.credit.domain.VerifiedHardModeResult;
import com.polyo.polyo.credit.exception.CreditException;
import com.polyo.polyo.credit.port.GameResultSource;
import com.polyo.polyo.credit.port.HardModeGameResultReader;

import java.util.Objects;

/** 신뢰 가능한 게임 기록을 검증한 뒤에만 보상 계산용 결과를 만든다. */
public final class ValidatingHardModeGameResultReader implements HardModeGameResultReader {
    private final GameResultSource source;

    public ValidatingHardModeGameResultReader(GameResultSource source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public VerifiedHardModeResult readVerifiedResult(String authenticatedUserId, String gameId) {
        if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
            throw new ApiException(AUTH_UNAUTHORIZED);
        }
        if (gameId == null || gameId.isBlank()) {
            throw new ApiException(INVALID_REQUEST_BODY);
        }
        var found = source.findByGameId(gameId);
        if (found == null) {
            throw new CreditException(CREDIT_INVALID_GAME_RESULT);
        }
        var game = found.orElseThrow(() -> new CreditException(CREDIT_GAME_NOT_FOUND));
        if (!gameId.equals(game.gameId())) {
            throw new CreditException(CREDIT_INVALID_GAME_RESULT);
        }
        if (!authenticatedUserId.equals(game.userId())) {
            throw new CreditException(CREDIT_GAME_ACCESS_DENIED);
        }
        if (game.mode() == null) {
            throw new CreditException(CREDIT_INVALID_GAME_RESULT);
        }
        if (game.mode() != RecordedGameResult.Mode.HARD) {
            throw new CreditException(CREDIT_GAME_NOT_ELIGIBLE);
        }
        if (!game.finished()) {
            throw new CreditException(CREDIT_GAME_NOT_FINISHED);
        }
        if (!game.verified()) {
            throw new CreditException(CREDIT_GAME_RESULT_NOT_VERIFIED);
        }
        if (game.score() < 0) {
            throw new CreditException(CREDIT_INVALID_GAME_RESULT);
        }
        return new VerifiedHardModeResult(game.gameId(), game.userId(), game.score());
    }
}
