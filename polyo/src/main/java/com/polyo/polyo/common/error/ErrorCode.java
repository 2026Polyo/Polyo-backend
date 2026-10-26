package com.polyo.polyo.common.error;

/** 모든 도메인이 공통 실패 응답에 제공하는 오류 정보. */
public interface ErrorCode {
    String code();

    int status();

    String message();
}
