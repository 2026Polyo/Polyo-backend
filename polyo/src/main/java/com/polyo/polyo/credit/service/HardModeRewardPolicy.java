package com.polyo.polyo.credit.service;

import com.polyo.polyo.credit.domain.VerifiedHardModeResult;

/**
 * 하드모드 점수 → 코인 정책의 접점. 기본 구현은 50점당 1코인, 소수점 버림, 한 판 최대 200코인의 초기 정책을 사용한다. 구현은 0 이상의 코인을 반환해야 한다.
 */
public interface HardModeRewardPolicy {
    long calculateEarnedCoin(VerifiedHardModeResult result);
}
