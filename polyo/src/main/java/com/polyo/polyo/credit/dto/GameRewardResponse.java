package com.polyo.polyo.credit.dto;

import com.polyo.polyo.credit.domain.GameRewardSettlement;

/** coinAfterReward는 정산 당시 잔액이며 현재 잔액과 다를 수 있다. */
public record GameRewardResponse(
        String rewardId, String gameId, long earnedCoin, long coinAfterReward) {
    public static GameRewardResponse from(GameRewardSettlement settlement) {
        return new GameRewardResponse(
                settlement.rewardId(), settlement.gameId(),
                settlement.earnedCoin(), settlement.coinAfterReward());
    }
}
