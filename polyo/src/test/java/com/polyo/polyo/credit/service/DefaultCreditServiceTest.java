package com.polyo.polyo.credit.service;

import static com.polyo.polyo.common.error.CommonErrorCode.*;
import static com.polyo.polyo.credit.domain.RecordedGameResult.Mode.*;
import static com.polyo.polyo.credit.exception.CreditErrorCode.*;

import static org.junit.jupiter.api.Assertions.*;

import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.credit.domain.GameRewardResult;
import com.polyo.polyo.credit.domain.RecordedGameResult;
import com.polyo.polyo.credit.domain.VerifiedHardModeResult;
import com.polyo.polyo.credit.exception.CreditException;
import com.polyo.polyo.credit.port.HardModeGameResultReader;
import com.polyo.polyo.credit.support.AtomicTestCreditStore;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class DefaultCreditServiceTest {
    private final AtomicTestCreditStore store = new AtomicTestCreditStore();

    private HardModeGameResultReader reader(long score) {
        return new ValidatingHardModeGameResultReader(
                id -> Optional.of(new RecordedGameResult(id, "user_1", HARD, true, true, score)));
    }

    private DefaultCreditService service(long score) {
        return new DefaultCreditService(reader(score), new DefaultHardModeRewardPolicy(), store);
    }

    @Test
    void firstSettlementCreditsTheCalculatedAmountAndReturnsCreated() {
        store.setBalance("user_1", 100);
        var result = service(3000).settleGameReward("user_1", "game_1");
        assertTrue(result.created());
        assertEquals(60, result.settlement().earnedCoin());
        assertEquals(160, result.settlement().coinAfterReward());
        assertEquals(160, store.getCoinBalance("user_1"));
        assertEquals(1, store.settlementCount());
    }

    @Test
    void repeatedSettlementReturnsTheOriginalReceiptWithoutRecheckingOrRecalculating() {
        var first = service(5000).settleGameReward("user_1", "game_1");
        var retry =
                new DefaultCreditService(
                        (user, game) -> {
                            throw new AssertionError("Must reuse receipt");
                        },
                        result -> {
                            throw new AssertionError("Must not recalculate using a new policy");
                        },
                        store);
        var repeated = retry.settleGameReward("user_1", "game_1");
        assertFalse(repeated.created());
        assertEquals(first.settlement(), repeated.settlement());
        assertEquals(100, store.getCoinBalance("user_1"));
        assertEquals(1, store.settlementCount());
    }

    @Test
    void simultaneousRequestsAcrossServiceInstancesCommitOnlyOnce() throws Exception {
        store.setBalance("user_1", 100);
        var barrier = new CyclicBarrier(2);
        HardModeGameResultReader simultaneousReader =
                (user, game) -> {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                    return new VerifiedHardModeResult(game, user, 5000);
                };
        var firstService =
                new DefaultCreditService(
                        simultaneousReader, new DefaultHardModeRewardPolicy(), store);
        var secondService =
                new DefaultCreditService(
                        simultaneousReader, new DefaultHardModeRewardPolicy(), store);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> firstService.settleGameReward("user_1", "game_1"));
            var second = executor.submit(() -> secondService.settleGameReward("user_1", "game_1"));
            var a = first.get(10, TimeUnit.SECONDS);
            var b = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(a.created(), b.created());
            assertEquals(a.settlement(), b.settlement());
        }
        assertEquals(200, store.getCoinBalance("user_1"));
        assertEquals(1, store.settlementCount());
    }

    @Test
    void differentConcurrentGamesDoNotLoseBalanceUpdates() throws Exception {
        store.setBalance("user_1", 100);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service(5000).settleGameReward("user_1", "game_1"));
            var second = executor.submit(() -> service(5000).settleGameReward("user_1", "game_2"));
            GameRewardResult a = first.get(10, TimeUnit.SECONDS);
            GameRewardResult b = second.get(10, TimeUnit.SECONDS);
            assertTrue(a.created() && b.created());
            assertEquals(
                    Set.of(200L, 300L),
                    Set.of(a.settlement().coinAfterReward(), b.settlement().coinAfterReward()));
        }
        assertEquals(300, store.getCoinBalance("user_1"));
        assertEquals(2, store.settlementCount());
    }

    @Test
    void zeroCoinRewardsAreAlsoRecordedOnlyOnce() {
        var first = service(49).settleGameReward("user_1", "game_1");
        var repeated = service(5000).settleGameReward("user_1", "game_1");
        assertTrue(first.created());
        assertFalse(repeated.created());
        assertEquals(0, repeated.settlement().earnedCoin());
        assertEquals(0, store.getCoinBalance("user_1"));
        assertEquals(1, store.settlementCount());
    }

    @Test
    void normalModeRejectionDoesNotChangeBalanceOrLedger() {
        store.setBalance("user_1", 100);
        var normalReader =
                new ValidatingHardModeGameResultReader(
                        id ->
                                Optional.of(
                                        new RecordedGameResult(
                                                id, "user_1", NORMAL, true, true, 100000)));
        var service =
                new DefaultCreditService(normalReader, new DefaultHardModeRewardPolicy(), store);
        assertEquals(
                CREDIT_GAME_NOT_ELIGIBLE,
                assertThrows(
                                CreditException.class,
                                () -> service.settleGameReward("user_1", "game_1"))
                        .errorCode());
        assertEquals(100, store.getCoinBalance("user_1"));
        assertEquals(0, store.settlementCount());
    }

    @Test
    void anotherUsersGameDoesNotPay() {
        var service = service(5000);
        assertEquals(
                CREDIT_GAME_ACCESS_DENIED,
                assertThrows(
                                CreditException.class,
                                () -> service.settleGameReward("user_2", "game_1"))
                        .errorCode());
        assertEquals(0, store.getCoinBalance("user_2"));
        assertEquals(0, store.settlementCount());
    }

    @Test
    void invalidRequestsDoNotReadGameResults() {
        var service =
                new DefaultCreditService(
                        (user, game) -> {
                            throw new AssertionError("Unexpected game lookup");
                        },
                        new DefaultHardModeRewardPolicy(),
                        store);
        assertEquals(
                AUTH_UNAUTHORIZED,
                assertThrows(ApiException.class, () -> service.settleGameReward(" ", "game_1"))
                        .errorCode());
        assertEquals(
                INVALID_REQUEST_BODY,
                assertThrows(ApiException.class, () -> service.settleGameReward("user_1", null))
                        .errorCode());
        assertEquals(
                AUTH_UNAUTHORIZED,
                assertThrows(ApiException.class, () -> service.getCoinBalance(null)).errorCode());
        assertEquals(0, store.settlementCount());
    }

    @Test
    void negativeOrUnsafePolicyAmountsAreRejectedBeforeStorage() {
        for (long bad : new long[] {-1, Long.MAX_VALUE}) {
            var service = new DefaultCreditService(reader(5000), result -> bad, store);
            assertEquals(
                    CREDIT_INVALID_REWARD_AMOUNT,
                    assertThrows(
                                    CreditException.class,
                                    () -> service.settleGameReward("user_1", "game_1"))
                            .errorCode());
        }
        assertEquals(0, store.settlementCount());
    }

    @Test
    void unexpectedGameIdentityIsRejectedDefensively() {
        var service =
                new DefaultCreditService(
                        (user, game) -> new VerifiedHardModeResult("wrong_game", user, 5000),
                        new DefaultHardModeRewardPolicy(),
                        store);
        assertEquals(
                CREDIT_INVALID_GAME_RESULT,
                assertThrows(
                                CreditException.class,
                                () -> service.settleGameReward("user_1", "game_1"))
                        .errorCode());
        assertEquals(0, store.settlementCount());
    }

    @Test
    void failedAtomicWriteCanBeRetriedWithoutLosingOrDuplicatingCoins() {
        store.setBalance("user_1", 100);
        store.failNextSettlement();
        var service = service(5000);
        assertThrows(
                IllegalStateException.class, () -> service.settleGameReward("user_1", "game_1"));
        assertEquals(100, store.getCoinBalance("user_1"));
        assertEquals(0, store.settlementCount());
        assertTrue(service.settleGameReward("user_1", "game_1").created());
        assertEquals(200, store.getCoinBalance("user_1"));
        assertEquals(1, store.settlementCount());
    }

    @Test
    void overflowIsRejectedByTheStoreBeforeCommit() {
        store.setBalance("user_1", 9_007_199_254_740_981L);
        assertThrows(
                ArithmeticException.class,
                () -> service(5000).settleGameReward("user_1", "game_1"));
        assertEquals(9_007_199_254_740_981L, store.getCoinBalance("user_1"));
        assertEquals(0, store.settlementCount());
    }

    @Test
    void balanceQueriesRemainUserScopedAndValidateAmounts() {
        store.setBalance("user_1", 100);
        store.setBalance("user_2", 200);
        assertEquals(100, service(0).getCoinBalance("user_1"));
        assertEquals(200, service(0).getCoinBalance("user_2"));
        store.setBalance("user_1", -1);
        assertEquals(
                CREDIT_INVALID_BALANCE,
                assertThrows(CreditException.class, () -> service(0).getCoinBalance("user_1"))
                        .errorCode());
    }
}
