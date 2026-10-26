package com.polyo.polyo.credit.port;

/**
 * Identity 연결 접점. 검증된 인증 컨텍스트에서 불변 사용자 ID를 반환한다. 로그인명과 사용자 ID가 같다고 가정하거나 클라이언트 X-User-Id를 신뢰하지 않는다.
 * 미인증은 AUTH_UNAUTHORIZED, 역할 거부는 ACCESS_DENIED로 처리한다.
 */
public interface AuthenticatedUserProvider {
    String requireUserId();
}
