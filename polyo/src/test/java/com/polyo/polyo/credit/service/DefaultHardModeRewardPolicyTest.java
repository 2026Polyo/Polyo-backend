package com.polyo.polyo.credit.service;

import static org.junit.jupiter.api.Assertions.*;

import com.polyo.polyo.credit.domain.VerifiedHardModeResult;

import org.junit.jupiter.api.Test;

class DefaultHardModeRewardPolicyTest {
    private final HardModeRewardPolicy policy = new DefaultHardModeRewardPolicy();

    private VerifiedHardModeResult result(long score) {
        return new VerifiedHardModeResult("game_test", "user_test", score);
    }

    @Test
    void rewardsFollowInitialEconomyExamples() {
        assertEquals(20, policy.calculateEarnedCoin(result(1000)));
        assertEquals(60, policy.calculateEarnedCoin(result(3000)));
        assertEquals(100, policy.calculateEarnedCoin(result(5000)));
        assertEquals(200, policy.calculateEarnedCoin(result(10000)));
    }

    @Test
    void partialCoinsAreRoundedDown() {
        assertEquals(0, policy.calculateEarnedCoin(result(0)));
        assertEquals(0, policy.calculateEarnedCoin(result(49)));
        assertEquals(1, policy.calculateEarnedCoin(result(50)));
        assertEquals(1, policy.calculateEarnedCoin(result(99)));
        assertEquals(199, policy.calculateEarnedCoin(result(9999)));
    }

    @Test
    void veryLargeScoresStayCappedWithoutOverflow() {
        assertEquals(200, policy.calculateEarnedCoin(result(10001)));
        assertEquals(200, policy.calculateEarnedCoin(result(Long.MAX_VALUE)));
    }

    @Test
    void invalidInputsAreRejectedInsteadOfMintingCoins() {
        assertThrows(IllegalArgumentException.class, () -> policy.calculateEarnedCoin(result(-1)));
        assertThrows(NullPointerException.class, () -> policy.calculateEarnedCoin(null));
    }

    @Test
    void policyCanBeTunedWithoutChangingTheSettlementFlow() {
        var tuned = new DefaultHardModeRewardPolicy(100, 30);
        assertEquals(10, tuned.calculateEarnedCoin(result(1000)));
        assertEquals(30, tuned.calculateEarnedCoin(result(4000)));
        assertEquals(0, new DefaultHardModeRewardPolicy(50, 0).calculateEarnedCoin(result(5000)));
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultHardModeRewardPolicy(0, 200));
        assertThrows(
                IllegalArgumentException.class, () -> new DefaultHardModeRewardPolicy(-1, 200));
        assertThrows(IllegalArgumentException.class, () -> new DefaultHardModeRewardPolicy(50, -1));
    }

    @Test
    void increasingScoresNeverDecreaseRewardsOrExceedTheCap() {
        long previous = 0;
        for (long score = 0; score <= 20000; score += 37) {
            long coins = policy.calculateEarnedCoin(result(score));
            assertTrue(coins >= previous);
            assertTrue(coins <= 200);
            previous = coins;
        }
    }
}
