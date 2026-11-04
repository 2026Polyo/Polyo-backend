package com.polyo.polyo.credit.exception;

import com.polyo.polyo.common.error.ErrorCode;

/** HTTP 계층은 이 코드·상태를 팀 공통 실패 응답으로 변환한다. */
public enum CreditErrorCode implements ErrorCode {
    CREDIT_SERVICE_UNAVAILABLE(503, "코인 정산 서비스를 사용할 수 없습니다."),
    IDENTITY_SERVICE_UNAVAILABLE(503, "인증 연동을 사용할 수 없습니다."),
    CREDIT_GAME_NOT_FOUND(404, "게임 기록을 찾을 수 없습니다."),
    CREDIT_GAME_ACCESS_DENIED(403, "이 게임에 접근할 권한이 없습니다."),
    CREDIT_GAME_NOT_ELIGIBLE(409, "일반 게임은 코인 보상 대상이 아닙니다."),
    CREDIT_GAME_NOT_FINISHED(409, "게임이 아직 종료되지 않았습니다."),
    CREDIT_GAME_RESULT_NOT_VERIFIED(409, "게임 결과 검증이 완료되지 않았습니다."),
    CREDIT_INVALID_GAME_RESULT(500, "게임 결과를 확인하지 못했습니다."),
    CREDIT_INVALID_REWARD_AMOUNT(500, "코인 보상량을 확인하지 못했습니다."),
    CREDIT_INVALID_SETTLEMENT(500, "코인 정산 결과를 확인하지 못했습니다."),
    CREDIT_INVALID_BALANCE(500, "코인 잔액을 확인하지 못했습니다.");

    private final int status;
    private final String message;

    CreditErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
