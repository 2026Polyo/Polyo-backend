package com.polyo.polyo.credit.domain;

/** 게임 결과 제공자가 소유자, 하드모드, 종료 및 결과 검증 완료를 확인한 뒤 만드는 값. HTTP 요청 DTO나 클라이언트 점수로 직접 구성해서는 안 된다. */
public record VerifiedHardModeResult(String gameId, String userId, long score) {}
