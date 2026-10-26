# CREDIT 코인 정산

코인 조회·하드모드 보상 계산·게임 결과 검증·정산 API를 구현했다.
인증, 실제 게임 기록, MySQL 저장소는 아직 연결하지 않았다.

## 구조와 흐름

```text
common/
  api/        성공·실패 응답, MVC 예외 처리
  error/      공통 오류 코드·인터페이스·예외
credit/
  web/        Controller, 요청 추적 로그, OpenAPI 설정
  dto/        HTTP 요청·응답
  service/    정산 흐름, 게임 결과 검증, 보상 계산
  domain/     서버 게임 기록·검증 결과·정산 결과
  exception/  CREDIT 오류 코드·예외
  port/       인증·게임 기록·저장소 연결 인터페이스
```

정산 흐름은 다음과 같다.

1. 인증된 사용자와 게임 ID를 확인한다.
2. 기존 정산이 있으면 저장된 결과를 그대로 반환한다.
3. 서버 게임 기록의 소유자·하드모드·종료·검증 상태를 확인한다.
4. `DefaultHardModeRewardPolicy`로 지급량을 계산한다.
5. `CreditSettlementStore.settleOnce`로 정산 기록과 잔액을 함께 반영한다.

초기 공식은 `min(floor(score / 50), 200)`이다. 일반 모드는 지급 대상이 아니다.
0코인도 정산 완료로 기록하고, 정책 변경 후 재요청에도 이전 지급 결과를 재사용한다.
동시 요청의 최초·기존 정산 여부는 저장소가 확정한 `GameRewardResult.created`를 사용한다.

## API

| 기능 | 요청 | 반환 data |
| --- | --- | --- |
| 잔액 조회 | `GET /api/credit/v11/balances/me` | `{ coin }` |
| 보상 정산 | `POST /api/credit/v11/game-rewards` | `{ rewardId, gameId, earnedCoin, coinAfterReward }` |

- 정산 본문은 `{ "gameId": "서버 게임 ID" }`이다. 사용자·모드·점수·지급량은 입력받지 않는다.
- 최초 정산은 201, 기존 정산은 200이다. `coinAfterReward`는 지급 당시 잔액이다.
- 응답은 camelCase의 공통 성공·실패 형식이다. 오류 timestamp는 UTC ISO-8601 문자열이다.
- `ApiExceptionHandler`는 공통 `ErrorCode`를 처리한다. 공통 계층은 CREDIT를 참조하지 않는다.
- 요청 추적은 Trace-Id를 전달하거나 생성하며, 본문·토큰·비밀번호는 로그에 남기지 않는다.
- Controller 어노테이션으로 생성한 OpenAPI JSON은 `/v3/api-docs/credit`이다. 기존 보안으로 보호된다.

주요 오류는 잘못된 본문 400, 미인증 401, 역할·소유권 403, 게임 없음 404,
일반·미종료·미검증 게임 409, 미연결 서비스 503이다. 내부 오류는 500이며 원문 예외를 노출하지 않는다.
없는 API 경로의 `API_NOT_FOUND`(404)는 팀 공통 코드 추가 제안이다.

## 나중에 연결할 부분

| 인터페이스 | 구현 담당 내용 |
| --- | --- |
| `AuthenticatedUserProvider` | 검증된 인증 컨텍스트에서 불변 사용자 ID 조회 |
| `GameResultSource` | 서버가 저장·검증한 게임 기록 조회 |
| `CreditSettlementStore` | MySQL 잔액·정산 조회 및 원자적 지급 |

`ValidatingHardModeGameResultReader`는 `GameResultSource`의 결과를 검증한다.
위 구현체를 조합해 `DefaultCreditService`를 Bean으로 등록하면 Controller와 연결된다.
클라이언트의 `X-User-Id`나 로그인명을 사용자 ID로 그대로 신뢰하지 않는다.
다른 서비스 소유 데이터는 해당 API로 연결한다.

Controller는 등록했지만 실제 서비스와 인증 공급자 Bean은 아직 등록하지 않았다.
MVC까지 도달한 미연결 요청은 503을 반환하며, 기존 Security 필터는 미인증 요청을 계속 차단한다.
Security 필터의 공통 401/403 JSON 응답과 OpenAPI 인증 scheme은 인증 담당자와 연결해야 한다.

## MySQL 저장소의 필수 조건

현재 MySQL 드라이버·Entity·Repository·DDL·접속 설정은 추가하지 않았다.
DB 담당자가 `CreditSettlementStore`를 구현할 때 다음 조건을 보장해야 한다.

- 사용자·게임 조합에 유일성 제약을 두고 재시작·여러 서버에서도 중복 지급을 막는다.
- 정산 기록과 잔액 갱신을 하나의 트랜잭션으로 처리하고 실패 시 함께 롤백한다.
- 같은 사용자의 서로 다른 게임도 동시 갱신이 유실되지 않게 처리한다.
- 지급량과 지급 당시 잔액을 저장하고 기존 정산 요청에는 같은 기록을 반환한다.
- 커밋 전에 소유권과 금액을 검증한다. 코인은 0 이상, JavaScript 안전 정수 범위 이하여야 한다.

서비스의 반환값 검증만으로 이미 커밋된 DB 변경이 롤백되지는 않는다.
잔액이 다른 서비스 소유라면 직접 DB를 쓰지 않고 API와 복구 전략을 설계한다.

## 검증과 작업 기록 — 2026-10-23

- Java 21에서 Gradle `build` 성공. 전체 49개 테스트 통과, 실패·건너뜀 없음.
- 정책 7개, 결과 검증 8개, 정산 서비스 13개, Controller 17개, OpenAPI 3개, 기존 컨텍스트 1개.
- 일반 모드 거부, 소유권, 상태 검증, 재요청, 동시 정산, 0코인, 저장 실패·잔액 초과를 확인했다.
- `AtomicTestCreditStore`는 `src/test`의 대체 저장소다. 테스트 통과는 실제 MySQL 동시성·트랜잭션 검증을 의미하지 않는다.
- 로컬 JDK 21이 없어 임시 JDK를 사용했고 시스템 Java와 프로젝트 toolchain은 유지했다.
- 가독성 정리: 공통·도메인 오류 분리, 정산 검증 함수 분리, Java 포맷·import·주석 통일.
- 기존 API 경로·응답·보상 공식·보안 요구는 유지했다. 실제 인증·게임 기록·MySQL 통합 검증은 남아 있다.
- 소스와 `src/test`는 커밋 대상이다. `build`, `.gradle`, `tmp`의 도구·생성물은 제외한다.
- 이번 작업에서는 커밋하지 않았다.

## 커밋 전 최종 검증 — 2026-10-26

- Java 21에서 `gradlew.bat build --rerun-tasks --no-daemon --console=plain`을 다시 실행해 전체 빌드와 테스트를 확인했다.
- 전체 49개 테스트 통과. 실패·오류·건너뜀은 0개다.
- 50점당 1코인·200코인 상한, 일반 모드 제외, 게임 소유권·종료·검증 상태, 정산 재사용, 동시 요청, 0코인과 저장 실패 후 재시도를 검증했다.
- Controller 응답·공통 오류·입력 검증·추적 로그, OpenAPI 생성과 기존 미인증 접근 차단도 통과했다.
- 실제 인증·하드모드 API·MySQL 저장소는 미연결이다. 테스트용 메모리 저장소가 실제 DB의 동시성·트랜잭션 보장을 대체하지 않는다.
- 기능 소스·테스트·연결 문서만 커밋하며 빌드 결과와 임시 도구는 제외한다.
