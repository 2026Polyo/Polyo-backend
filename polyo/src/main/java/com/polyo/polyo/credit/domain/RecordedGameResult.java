package com.polyo.polyo.credit.domain;

/** 서버 게임 기록의 코인 정산용 투영. 클라이언트 요청 DTO가 아니다. */
public record RecordedGameResult(
        String gameId, String userId, Mode mode, boolean finished, boolean verified, long score) {
    public enum Mode {
        NORMAL,
        HARD
    }
}
