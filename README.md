# Polyo-backend
Polyo 프로젝트의 백엔드 서버

## 로컬 PostgreSQL 실행

Docker Desktop을 실행한 뒤 저장소 루트에서 실행합니다.

```bash
docker compose up -d --wait
```

접속 정보 (로컬 개발용):

| 항목 | 값 |
| --- | --- |
| Host | localhost |
| Port | 5432 |
| Database | polyo |
| Username | polyo |
| Password | polyo_local |
| JDBC URL | jdbc:postgresql://localhost:5432/polyo |

기본 연결 정보는 `polyo/src/main/resources/application.yaml`에 있으며,
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수로 변경할 수 있습니다.
위 계정과 비밀번호는 로컬 개발 전용입니다. PostgreSQL 포트는 로컬 호스트에만 공개됩니다.
JPA는 기존 스키마를 검증하며(`ddl-auto: validate`), 엔티티를 추가할 때는 해당 테이블도 먼저 생성해야 합니다.

연결 확인과 테스트:

```bash
docker compose exec postgres psql -U polyo -d polyo -c 'SELECT current_database(), current_user;'
cd polyo
./gradlew test
```

테스트는 Testcontainers로 임시 PostgreSQL 컨테이너를 따로 띄워 실행하므로 Docker Desktop이 켜져 있어야 하며, 로컬 개발 DB의 데이터는 건드리지 않습니다.

DB를 중지하려면 저장소 루트에서 `docker compose down`을 실행합니다.
데이터는 Docker 볼륨에 보관되어 다음 실행에도 유지됩니다.
`docker compose down -v`는 DB 데이터를 삭제하므로 초기화할 때만 사용합니다.

## 실행 및 테스트

JDK 21을 설치하고 `JAVA_HOME`을 설정한 뒤 프로젝트 루트에서 실행합니다.

```powershell
cd polyo
.\gradlew.bat clean test bootJar
.\gradlew.bat bootRun
```

## API 명세

- [매치 룸 API 명세](docs/match-room-api.md)

## 매치 룸 API

| 기능 | Method | 경로 |
| --- | --- | --- |
| 목록 조회 | GET | `/api/match-room?page=0&size=20` |
| 생성 | POST | `/api/match-room` |
| ID 입장 | POST | `/api/match-room/{roomId}/members` |
| 코드 입장 | POST | `/api/match-room/code/members` |
| 나가기 | DELETE | `/api/match-room/{roomId}/members/me` |
| 삭제 | DELETE | `/api/match-room/{roomId}` |

생성 요청은 `{"roomName":"같이 플레이해요","isPrivate":false}`, 코드 입장 요청은 `{"entryCode":"발급받은코드"}`입니다. 정원은 방장 포함 2명이며 비공개 방의 신규 참가자는 코드로 입장합니다. 방 삭제는 방장만 가능합니다. 게임 시작 전에는 나가기로 방을 떠날 수 있습니다. 참가자가 나가면 자리가 비어 `WAITING`으로 돌아가고, 방장이 나가면 방이 삭제됩니다. 게임 중에는 나가기 대신 `FORFEIT`을 보냅니다.

매치 룸은 PostgreSQL의 `match_room`, `match_room_member` 테이블에 저장되며, 테이블은 앱 시작 시 Flyway(`polyo/src/main/resources/db/migration`)가 생성합니다. 스키마를 바꿀 때는 기존 파일을 수정하지 말고 `V2__설명.sql`처럼 새 마이그레이션을 추가합니다. 같은 방에 대한 입장과 삭제는 방 행 잠금(`SELECT ... FOR UPDATE`)으로 순서대로 처리합니다.

## 대전 (WebSocket)

`game` 패키지가 2인 대전을 처리합니다. 서버가 모든 수를 판정하며, 규칙은 프론트엔드의 `resolvePlacement.ts`와 같고 증강은 적용하지 않습니다.

- 흐름: 로비에서 HTTP로 방을 만들거나 입장한 뒤 그 방의 WebSocket에 연결합니다. HTTP 입장을 하지 않은 사용자는 연결할 수 없습니다.
- 접속: `ws://{host}/ws/match/{roomId}`. 브라우저 WebSocket에는 헤더를 넣을 수 없으므로 첫 메시지로 `{"type":"AUTH","token":"<JWT>"}`를 보냅니다. 10초 안에 AUTH가 오지 않으면 `ERROR`(`AUTH_TIMEOUT`)를 보내고 연결을 닫습니다(`app.game.auth-timeout-ms`로 변경).
- 시작: 두 명이 접속해도 바로 시작하지 않습니다. 참가자가 `READY`로 준비하고, 방장이 `START`를 보내면 방을 `PLAYING`으로 바꾸고 게임을 시작합니다. 선공은 무작위입니다. 참가자의 연결이 끊기거나 참가자가 나가면 준비가 풀립니다. 거절 코드: `NOT_HOST`(참가자가 START), `OPPONENT_NOT_CONNECTED`, `OPPONENT_NOT_READY`, `HOST_CANNOT_READY`, `GAME_ALREADY_STARTED`, `ROOM_CHANGED`(확인 중 참가자가 바뀜).
- 감정표현: `EMOTE`를 보내면 방의 모두(보낸 사람 포함)에게 `{"type":"EMOTE","userId":"...","emote":"..."}`로 전달합니다. `emote`는 `smile`, `laugh`, `wow`, `sad`, `angry`, `cool` 중 하나이고, 그 외 값은 `ERROR`(`INVALID_EMOTE`)입니다. 한 사람이 500ms 안에 다시 보낸 감정표현은 오류 없이 버립니다.
- 규칙: 공용 블록 6개를 번갈아 하나씩 자기 보드에 놓고, 6개를 다 쓰면 새로 6개를 받습니다. 블록마다 15% 확률로 특수 블록(고스트·라인·에코·황금·폭탄·채움·강화)이 나옵니다.
- 시간: 한 수 제한시간은 10초에서 시작하고 자기 점수에 따라 하드모드처럼 줄어 최소 2.8초가 됩니다.
- 패배: 내 차례에 놓을 블록이 없음(`NO_MOVES`), 시간 초과(`TIMEOUT`), 기권(`FORFEIT`).
- 클라이언트 메시지: `{"type":"READY","ready":true}`, `{"type":"START"}`, `{"type":"EMOTE","emote":"laugh"}`, `{"type":"PLACE","pieceUid":"...","row":0,"col":0}`, `{"type":"FORFEIT"}`
- 서버 메시지: 시작 전 `WAITING`(`connectedUserIds`, `readyUserIds`, `hostUserId`, `members`), 감정표현 `EMOTE`, 시작 후 변경마다 전체 상태 `STATE`, 잘못된 요청에는 `ERROR`(`code`, `message`). 대기 중 방이 삭제되면(방장의 삭제·나가기) 접속한 모두에게, 참가자가 나가면 그 참가자에게 `ROOM_CLOSED`(`reason`: `ROOM_DELETED` 또는 `LEFT_ROOM`)를 보내고 연결을 닫습니다. 남은 사람은 갱신된 `WAITING`을 받습니다. `STATE`의 `turnRemainingMs`는 남은 시간이라 서버와 클라이언트의 시계 차이와 무관합니다.
- 재접속: 같은 사용자가 다시 접속하면 이전 연결을 닫고 현재 상태를 보냅니다. 끊긴 동안에도 시간은 흐릅니다.
- 종료: 결과를 보낸 뒤 방을 삭제합니다. 결과는 아직 저장하지 않습니다.

게임 상태는 서버 메모리에만 있습니다. 서버가 재시작되면 진행 중이던 게임은 사라지고, 두 참가자가 다시 접속해 준비하고 방장이 시작하면 새 게임이 시작됩니다.

## 인증 연동 경계

`Authorization: Bearer` 토큰은 `JwtAuthenticationFilter`가 검증하고, 검증된 사용자 정보를 `MatchRoomUser(id, nickname)` principal로 넣습니다.

- 매치 룸 API는 요청 본문이나 임의의 사용자 ID 헤더를 인증 정보로 사용하지 않습니다.
- 필요한 사용자 정보가 없으면 매치 룸 요청을 거절합니다.
- API 테스트에서는 Spring Security의 테스트 전용 principal을 주입해 매치 룸 동작을 검증합니다. 로그인 API나 JWT 검증 테스트는 포함하지 않습니다.

공통 응답은 `common` 패키지의 `success`, `data`, `error` 구조를 사용합니다. 삭제 성공은 본문 없는 `204`입니다. 사용자 ID는 문자열, 방 ID는 정수입니다. 보안 필터 단계의 오류 응답 형식은 정식 인증 설정과 연동할 때 맞춥니다.
