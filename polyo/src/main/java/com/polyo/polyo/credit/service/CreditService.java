package com.polyo.polyo.credit.service;

import com.polyo.polyo.credit.domain.GameRewardResult;

/**
 * 코인 조회·정산 유스케이스. authenticatedUserId는 검증된 인증 컨텍스트에서만 받는다. 구현 시 기존 정산 확인 → 결과 검증 → 지급량 계산 → 원자적 정산
 * 순서와 동시성을 보장한다. DefaultCreditService 구현을 제공하지만 저장소·인증 연결 전에는 Bean을 등록하지 않는다.
 */
public interface CreditService {
    long getCoinBalance(String authenticatedUserId);

    /** 일반 게임은 거부하며, 같은 게임 재요청에는 기존 정산 결과를 반환해야 한다. */
    GameRewardResult settleGameReward(String authenticatedUserId, String gameId);
}
