package com.polyo.polyo.credit.service;

import static com.polyo.polyo.common.error.CommonErrorCode.AUTH_UNAUTHORIZED;
import static com.polyo.polyo.common.error.CommonErrorCode.INVALID_REQUEST_BODY;
import static com.polyo.polyo.credit.exception.CreditErrorCode.*;

import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.credit.domain.GameRewardResult;
import com.polyo.polyo.credit.domain.GameRewardSettlement;
import com.polyo.polyo.credit.domain.VerifiedHardModeResult;
import com.polyo.polyo.credit.exception.CreditException;
import com.polyo.polyo.credit.port.CreditSettlementStore;
import com.polyo.polyo.credit.port.HardModeGameResultReader;

import java.util.Objects;
import java.util.Optional;

/** 게임 결과·보상 정책·저장소를 조합한다. 실제 연결 전에는 Bean으로 등록하지 않는다. */
public final class DefaultCreditService implements CreditService {
    private static final long MAX_SAFE_COIN = 9_007_199_254_740_991L;

    private final HardModeGameResultReader gameResults;
    private final HardModeRewardPolicy rewardPolicy;
    private final CreditSettlementStore store;

    public DefaultCreditService(
            HardModeGameResultReader gameResults,
            HardModeRewardPolicy rewardPolicy,
            CreditSettlementStore store) {
        this.gameResults = Objects.requireNonNull(gameResults);
        this.rewardPolicy = Objects.requireNonNull(rewardPolicy);
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public long getCoinBalance(String authenticatedUserId) {
        requireUser(authenticatedUserId);
        long balance = store.getCoinBalance(authenticatedUserId);
        if (!isValidCoin(balance)) {
            throw new CreditException(CREDIT_INVALID_BALANCE);
        }
        return balance;
    }

    @Override
    public GameRewardResult settleGameReward(String authenticatedUserId, String gameId) {
        requireUser(authenticatedUserId);
        if (gameId == null || gameId.isBlank()) {
            throw new ApiException(INVALID_REQUEST_BODY);
        }

        Optional<GameRewardSettlement> existing = findSettlement(authenticatedUserId, gameId);
        if (existing.isPresent()) {
            return new GameRewardResult(existing.get(), false);
        }

        VerifiedHardModeResult game = readVerifiedResult(authenticatedUserId, gameId);
        long earnedCoin = rewardPolicy.calculateEarnedCoin(game);
        if (!isValidCoin(earnedCoin)) {
            throw new CreditException(CREDIT_INVALID_REWARD_AMOUNT);
        }

        // 사전 조회와 별개로 저장소가 중복 요청·동시 정산의 원자성을 보장해야 한다.
        GameRewardResult result = store.settleOnce(authenticatedUserId, gameId, earnedCoin);
        if (result == null) {
            throw new CreditException(CREDIT_INVALID_SETTLEMENT);
        }
        validateSettlement(result.settlement(), gameId);
        // 경합 중 먼저 저장된 결과가 있다면 그 지급량과 잔액을 그대로 반환한다.
        return result;
    }

    private Optional<GameRewardSettlement> findSettlement(String userId, String gameId) {
        Optional<GameRewardSettlement> existing = store.findSettlement(userId, gameId);
        if (existing == null) {
            throw new CreditException(CREDIT_INVALID_SETTLEMENT);
        }
        existing.ifPresent(settlement -> validateSettlement(settlement, gameId));
        return existing;
    }

    private VerifiedHardModeResult readVerifiedResult(String userId, String gameId) {
        VerifiedHardModeResult result = gameResults.readVerifiedResult(userId, gameId);
        if (result == null || !gameId.equals(result.gameId()) || result.score() < 0) {
            throw new CreditException(CREDIT_INVALID_GAME_RESULT);
        }
        if (!userId.equals(result.userId())) {
            throw new CreditException(CREDIT_GAME_ACCESS_DENIED);
        }
        return result;
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ApiException(AUTH_UNAUTHORIZED);
        }
    }

    private static boolean isValidCoin(long value) {
        return value >= 0 && value <= MAX_SAFE_COIN;
    }

    private static void validateSettlement(GameRewardSettlement settlement, String gameId) {
        if (settlement == null
                || settlement.rewardId() == null
                || settlement.rewardId().isBlank()
                || !gameId.equals(settlement.gameId())
                || !isValidCoin(settlement.earnedCoin())
                || !isValidCoin(settlement.coinAfterReward())) {
            throw new CreditException(CREDIT_INVALID_SETTLEMENT);
        }
    }
}
