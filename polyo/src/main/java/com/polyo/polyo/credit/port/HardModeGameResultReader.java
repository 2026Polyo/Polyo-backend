package com.polyo.polyo.credit.port;

import com.polyo.polyo.credit.domain.VerifiedHardModeResult;

/**
 * 게임 결과 소유 모듈·서비스와 연결하는 접점. 일반 모드, 다른 사용자 게임, 미종료, 검증 미완료 결과를 반환하지 않아야 한다. 실제 서비스가 분리되어 있다면 해당 서비스
 * API를 사용하며 다른 서비스 DB를 조회하지 않는다. 구현 시 각 거부 사유를 도메인 예외로 구분하고 HTTP 오류 매핑을 별도로 정의한다.
 */
public interface HardModeGameResultReader {
    VerifiedHardModeResult readVerifiedResult(String authenticatedUserId, String gameId);
}
