package com.polyo.polyo.credit.domain;

/** 저장된 정산 결과. JPA Entity가 아니며 HTTP 응답에는 DTO로 변환한다. */
public record GameRewardSettlement(
        String rewardId, String gameId, long earnedCoin, long coinAfterReward) {}
