package com.polyo.polyo.common.error;

public enum CommonErrorCode implements ErrorCode {
    INVALID_REQUEST_BODY(400, "요청 본문을 확인해 주세요."),
    INVALID_REQUEST_PARAM(400, "요청 파라미터를 확인해 주세요."),
    AUTH_UNAUTHORIZED(401, "로그인이 필요합니다."),
    ACCESS_DENIED(403, "이 API를 사용할 권한이 없습니다."),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 요청 방식입니다."),
    API_NOT_FOUND(404, "요청한 API를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 오류가 발생했습니다.");

    private final int status;
    private final String message;

    CommonErrorCode(int status, String message) {
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
