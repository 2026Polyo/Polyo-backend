package com.polyo.polyo.credit.port;

import com.polyo.polyo.credit.domain.RecordedGameResult;

import java.util.Optional;

/** 게임 소유 모듈의 조회 접점. 서비스가 분리돼 있으면 내부 API로 구현한다. */
public interface GameResultSource {
    Optional<RecordedGameResult> findByGameId(String gameId);
}
