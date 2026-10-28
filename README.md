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
| 삭제 | DELETE | `/api/match-room/{roomId}` |

생성 요청은 `{"roomName":"같이 플레이해요","isPrivate":false}`, 코드 입장 요청은 `{"entryCode":"발급받은코드"}`입니다. 정원은 방장 포함 2명이며 비공개 방의 신규 참가자는 코드로 입장합니다. 방 삭제는 방장만 가능합니다.

매치 룸은 메모리에 저장하므로 서버 재시작 시 초기화됩니다. WebSocket 연결과 게임 시작 기능은 별도 연동 대상입니다.

## 인증 연동 경계

회원가입·로그인·토큰 발급 및 검증은 정식 인증 브랜치에서 관리합니다. 현재 매치 룸 브랜치는 별도 인증 구현 없이 인증 연동을 기다리는 상태입니다.

- 프로젝트의 기본 Spring Security 설정을 유지합니다.
- 인증 브랜치 반영 후 검증된 사용자 정보를 `MatchRoomUser(id, nickname)` principal로 연결해야 합니다.
- 매치 룸 API는 요청 본문이나 임의의 사용자 ID 헤더를 인증 정보로 사용하지 않습니다.
- 필요한 사용자 정보가 없으면 매치 룸 요청을 거절합니다.
- API 테스트에서는 Spring Security의 테스트 전용 principal을 주입해 매치 룸 동작을 검증합니다. 로그인 API나 JWT 검증 테스트는 포함하지 않습니다.

공통 응답은 `common` 패키지의 `success`, `data`, `error` 구조를 사용합니다. 삭제 성공은 본문 없는 `204`입니다. 사용자 ID는 문자열, 방 ID는 정수입니다. 보안 필터 단계의 오류 응답 형식은 정식 인증 설정과 연동할 때 맞춥니다.
