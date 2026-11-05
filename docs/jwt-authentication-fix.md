# JWT 인증 오류 수정 — 2026-11-05

## 목표와 재현

정상 로그인으로 발급한 JWT가 매치룸에서 401로 거부되는 문제를 수정한다. 기준 코드는 `develop`의 `71950ce`이며, 변경 전 `JwtAuthenticationTests` 3개 중 `loginTokenAuthenticatesAsTheLoggedInUser`가 기대 상태 200 대신 401로 실패하는 것을 재현했다.

JWT 필터는 검증된 사용자 ID 문자열만 인증 principal에 넣고 있었지만, 매치룸 Controller의 `@AuthenticationPrincipal`은 ID와 닉네임을 가진 `MatchRoomUser`를 요구했다. 반대로 점수 Controller는 `Authentication.getName()`을 숫자 사용자 ID로 읽기 때문에, principal 객체의 타입만 바꾸면 점수 API에 회귀가 발생할 수 있었다.

## 입력·출력과 예외

| 입력과 상황 | 기대 결과 |
| --- | --- |
| 로그인 API에서 발급한 정상 Bearer JWT | 매치룸 목록 200, 생성 201. 토큰 사용자의 ID와 닉네임 사용 |
| 동일 JWT로 점수 저장·조회 | 200. 일반·하드 점수가 해당 사용자에게 저장되고 다른 사용자와 분리됨 |
| 만료·위조·잘못된 형식·빈 토큰·지원하지 않는 인증 헤더 | 보호된 매치룸·점수 API에서 401 `UNAUTHENTICATED` |
| 누락·빈 값·공백·숫자가 아닌 값·0·음수·Long 범위 초과 사용자 ID | 보호된 API에서 401 `UNAUTHENTICATED` |
| 누락·빈 값·공백 닉네임 | 보호된 API에서 401 `UNAUTHENTICATED` |
| 잘못된 Bearer 토큰과 함께 공개 로그인 API 호출 | 로그인 로직까지 진행. 잘못된 로그인 정보는 기존 401 `INVALID_CREDENTIALS` |

## 수정 범위와 구현

- `JwtTokenProvider.parse`에서 서명·만료와 함께 양수 숫자 사용자 ID와 공백이 아닌 닉네임을 검증한다. 기존 `getUserId`도 같은 검증 경로를 재사용한다.
- `JwtAuthenticationFilter`는 기존 토큰 파서를 사용해 `MatchRoomUser`를 인증 principal에 전달한다.
- `MatchRoomUser`가 `Principal`을 구현하고 `getName()`으로 기존 사용자 ID를 반환한다. 매치룸이 요구하는 객체 타입과 점수 API의 기존 ID 읽기 방식을 함께 유지한다.
- `JwtAuthenticationTests`에서 정상 로그인→매치룸, 사용자별 일반·하드 점수 분리, 잘못된 사용자 정보와 토큰의 두 API 공통 거부를 검증한다.
- 참고한 기존 구현은 `MatchRoomController`의 principal 타입, `ScoreController`의 `Authentication.getName()`, 기존 `JwtTokenProvider.parse` 및 JWT 통합 테스트다.

Controller·API URL·응답 DTO·DB 스키마·접속 설정은 변경하지 않았다. 공개 인증 엔드포인트와 기존 Security 설정을 유지했다.

## 완료 조건과 검증 결과

- JDK 21에서 `gradlew.bat test --tests com.polyo.polyo.auth.JwtAuthenticationTests --console=plain`: JWT 테스트 13개 통과.
- Docker의 Testcontainers PostgreSQL 17을 사용해 `gradlew.bat test bootJar --console=plain`: 전체 테스트 90개 통과, 실패·오류·건너뜀 0개. 실행 JAR 빌드 성공.
- 이전에 Docker 부재로 확인하지 못했던 앱·인증·매치룸 DB 통합 테스트도 이번 전체 실행에 포함됐다. 테스트 전용 임시 DB를 사용했다.
- 테스트 기대값을 완화하거나 인증·DB 테스트를 비활성화하지 않고 실제 principal 전달 불일치를 수정했다.

향후 인증 사용자 구조를 변경하는 작업에서도 principal 객체 타입과 `Authentication.getName()`을 사용하는 API를 함께 확인하고, 실제 로그인 토큰을 이용한 매치룸·점수 통합 테스트를 유지한다.
