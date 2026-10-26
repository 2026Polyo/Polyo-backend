package com.polyo.polyo.credit.service;

import com.polyo.polyo.credit.domain.VerifiedHardModeResult;

import java.util.Objects;

/**
 * 초기 밸런스: 50점당 1코인, 소수점 버림, 한 판 최대 200코인. 게임 종료·소유자·하드모드·결과 검증은 호출 전에 서버에서 확인해야 한다. 계산만 수행하며 잔액
 * 저장이나 지급은 하지 않는다.
 */
public final class DefaultHardModeRewardPolicy implements HardModeRewardPolicy {
    public static final long DEFAULT_SCORE_PER_COIN = 50;
    public static final long DEFAULT_MAX_COIN_PER_GAME = 200;

    private final long scorePerCoin;
    private final long maxCoinPerGame;

    public DefaultHardModeRewardPolicy() {
        this(DEFAULT_SCORE_PER_COIN, DEFAULT_MAX_COIN_PER_GAME);
    }

    /** 게임 플레이 결과를 확인한 뒤 서버 설정으로 조정할 수 있는 접점. */
    public DefaultHardModeRewardPolicy(long scorePerCoin, long maxCoinPerGame) {
        if (scorePerCoin <= 0 || maxCoinPerGame < 0) {
            throw new IllegalArgumentException(
                    "Score per coin must be positive and reward cap must not be negative");
        }
        this.scorePerCoin = scorePerCoin;
        this.maxCoinPerGame = maxCoinPerGame;
    }

    @Override
    public long calculateEarnedCoin(VerifiedHardModeResult result) {
        Objects.requireNonNull(result, "Verified hard-mode result is required");
        if (result.score() < 0) {
            throw new IllegalArgumentException("Verified score must not be negative");
        }
        // Divide first: even Long.MAX_VALUE cannot overflow during conversion.
        return Math.min(result.score() / scorePerCoin, maxCoinPerGame);
    }
}
