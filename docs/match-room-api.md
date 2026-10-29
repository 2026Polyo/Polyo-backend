# 매치 룸 API 명세 초안

확정한 5개 HTTP 경로와 제공한 ERD를 기준으로 매치 룸을 구현했다. 별도 회원가입·로그인·JWT 구현은 포함하지 않으며 정식 인증 브랜치와의 연동을 기다리는 상태다. 매치 룸은 PostgreSQL(`match_room`, `match_room_member`)에 저장되며 서버를 재시작해도 유지된다.

## 1. 공통 정책

### 요구사항과 초안에서 정한 정책

- 매치 룸 정원은 **방장 포함 2명**이다.
- 모든 API는 인증된 사용자 정보가 필요하다. 검증된 `MatchRoomUser(id, nickname)` principal을 전달받는 경계만 유지하며, 실제 로그인과 인증 처리는 정식 인증 브랜치에서 연동한다.
- 방 생성자는 방장이 되며, 생성과 동시에 `MATCH_PARTICIPANT`에 등록된다.
- 공개 방은 ID 또는 코드로 입장할 수 있다. 비공개 방의 신규 참가자는 코드로 입장해야 한다.
- 전체 목록에는 대기 중인 공개·비공개 방을 모두 표시한다. 비공개 방도 방 이름과 인원은 보이지만 **입장 코드는 목록에 포함하지 않는다**.
- 방 생성·입장 응답은 참가자에게 반환하므로 입장 코드를 포함한다.
- 같은 사용자의 같은 방 재입장은 `200 OK`이며 참가자를 중복 등록하지 않는다. 기존 참가자는 비공개 방이거나 게임 진행 중이어도 ID로 방 정보를 다시 받을 수 있다.
- 방장만 방을 삭제할 수 있다. 게임 시작 전에는 두 명이 입장한 방도 삭제할 수 있다. 게임 진행 중 삭제는 `409 MATCH_ALREADY_STARTED`로 거절하는 정책을 제안한다.
- 방 생성·입장은 HTTP로 처리하고, 성공한 클라이언트는 WebSocket에 연결한다. 실제 게임 시작은 두 참가자의 연결·수신 준비를 확인한 후 별도로 처리한다.

공개·비공개 방의 목록 노출, 방 이름 길이, 코드 형식, 상태값, 진행 중 삭제 제한은 이번 초안에서 정한 정책이다. ERD만으로 정해지지 않은 값이며 제품 정책에 맞춰 조정할 수 있다.

### 공통 데이터 규칙

| 항목 | 형식·규칙 |
| --- | --- |
| 기본 경로 | `/api/match-room` |
| 본문 형식 | 요청·응답 본문이 있으면 `application/json` |
| `roomId` | 양의 정수 |
| `userId`, `hostUserId` | 인증 연동 경계에서 전달받는 문자열 ID. 예: `"12"` |
| `roomName` | 앞뒤 공백 제거 후 1~30자. 중간 공백 허용. 방 이름 중복 허용. |
| `isPrivate` | Boolean. `true`: 코드 입장 필요, `false`: ID 또는 코드 입장 가능 |
| `entryCode` | 서버가 생성한 8자리 문자열. 허용 문자: `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` |
| 코드 입력 정규화 | 앞뒤 공백 제거 → 영문 대문자 변환 → 길이·허용 문자 검사 |
| `maxPlayers` | 항상 `2` |
| `playerCount` | 해당 방의 `MATCH_PARTICIPANT` 수. 방장을 포함해 `1` 또는 `2` |
| `createdAt` | UTC ISO 8601 문자열. 예: `2026-10-23T00:00:00Z` |
| `members` | 참가자 목록. 방장을 먼저 반환하고 이후 입장 순서로 정렬 |

### 상태값

| status | 의미 | 인원 |
| --- | --- | --- |
| `WAITING` | 두 번째 참가자를 기다리는 중 | 1명 |
| `FULL` | 정원이 찼으며 실제 게임 시작을 기다리는 중 | 2명 |
| `PLAYING` | 두 참가자의 연결·수신 준비를 확인해 게임을 시작한 상태 | 2명 |

방 생성 시 `WAITING`, 두 번째 사용자 입장 시 `FULL`, 추후 WebSocket 게임 시작 처리 시 `PLAYING`으로 변경한다. 이번 구현은 HTTP API이며 WebSocket 연결과 게임 시작 처리는 포함하지 않는다. `FULL` 상태만으로 게임을 시작하지 않는다. 연결이 끊겨도 HTTP 입장 기록을 자동 삭제하지 않으며, 재접속·퇴장 정책은 별도 명세에서 다룬다.

## 2. API 목록

| 기능 | Method | 경로 | 성공 상태 |
| --- | --- | --- | --- |
| 전체 조회 | `GET` | `/api/match-room` | `200 OK` |
| ID로 입장 | `POST` | `/api/match-room/{roomId}/members` | `200 OK` |
| 방 삭제 | `DELETE` | `/api/match-room/{roomId}` | `204 No Content` |
| 방 생성 | `POST` | `/api/match-room` | `201 Created` |
| 코드로 입장 | `POST` | `/api/match-room/code/members` | `200 OK` |

## 3. 매치 룸 전체 조회

### 기본 정보

- Method: `GET`
- URL: `/api/match-room`
- 인증: 필요
- 설명: 게임 시작 전인 `WAITING`, `FULL` 상태의 방을 페이지 단위로 조회한다. 공개·비공개 방을 모두 포함하며 `PLAYING` 상태의 방은 제외한다.

### 요청

요청 본문은 없다.

| Query parameter | 타입 | 필수 | 기본값 | 검증 |
| --- | --- | --- | --- | --- |
| `page` | Integer | 아니요 | `0` | 0 이상, 첫 페이지는 0 |
| `size` | Integer | 아니요 | `20` | 1~100 |

```http
GET /api/match-room?page=0&size=20
```

정렬은 `createdAt` 내림차순, 동률이면 `roomId` 내림차순으로 고정한다.

### 성공 응답: 200 OK

```json
{
  "success": true,
  "data": {
    "rooms": [
      {
        "roomId": 101,
        "roomName": "같이 플레이해요",
        "hostUserId": "12",
        "hostNickname": "폴리오",
        "isPrivate": false,
        "maxPlayers": 2,
        "playerCount": 1,
        "status": "WAITING",
        "createdAt": "2026-10-23T00:00:00Z"
      },
      {
        "roomId": 100,
        "roomName": "친구 대기방",
        "hostUserId": "56",
        "hostNickname": "블록친구",
        "isPrivate": true,
        "maxPlayers": 2,
        "playerCount": 2,
        "status": "FULL",
        "createdAt": "2026-10-22T23:50:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  },
  "error": null
}
```

| 응답 필드 | 설명 |
| --- | --- |
| `rooms` | 현재 페이지의 방 목록. 입장 코드는 포함하지 않음 |
| `hostNickname` | 방장의 `USERS.nickname` |
| `page`, `size` | 요청한 페이지 번호와 크기 |
| `totalElements` | 조회 대상인 전체 방 수. `PLAYING` 방은 제외 |
| `totalPages` | 전체 페이지 수 |

방이 없으면 `rooms: []`, `totalElements: 0`, `totalPages: 0`이다. 마지막 페이지를 넘겨 조회하면 빈 목록과 실제 전체 건수·페이지 수를 반환한다.

목록의 인원은 조회 시점의 값이다. 입장 요청에서는 정원을 다시 검사한다.

### 오류 응답

| HTTP 상태 | code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 페이지 값이 정수가 아니거나 허용 범위를 벗어남 |
| `401` | `UNAUTHENTICATED` | 로그인 정보가 없거나 유효하지 않음 |

## 4. 매치 룸 ID로 입장

### 기본 정보

- Method: `POST`
- URL: `/api/match-room/{roomId}/members`
- 인증: 필요
- 설명: 목록에서 선택한 공개 방에 인증된 사용자를 참가자로 등록한다. 기존 참가자는 현재 방 정보를 다시 받을 수 있다.

### 요청

| Path variable | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `roomId` | Integer | 예 | 양의 정수 |

요청 본문은 없다. 입장할 사용자 ID를 클라이언트가 전달하지 않는다.

```http
POST /api/match-room/101/members
```

### 처리 규칙

1. 방 ID에 해당하는 방을 조회한다. 없으면 `404 ROOM_NOT_FOUND`.
2. 이미 참가한 사용자면 인원·참가자 정보를 변경하지 않고 현재 방 정보를 `200`으로 반환한다. 이 규칙은 비공개 방과 `PLAYING` 상태에도 적용한다.
3. 신규 사용자가 비공개 방에 ID만으로 입장을 요청하면 `403 ROOM_CODE_REQUIRED`.
4. 신규 사용자가 `PLAYING` 상태의 방에 입장을 요청하면 `409 MATCH_ALREADY_STARTED`.
5. 현재 인원이 2명이면 `409 ROOM_FULL`.
6. 참가자를 등록하고 인원 2명·상태 `FULL`을 반영한 뒤 응답한다.

### 성공 응답: 200 OK

```json
{
  "success": true,
  "data": {
    "roomId": 101,
    "roomName": "같이 플레이해요",
    "hostUserId": "12",
    "isPrivate": false,
    "entryCode": "K7M3PX9Q",
    "maxPlayers": 2,
    "playerCount": 2,
    "status": "FULL",
    "members": [
      {
        "userId": "12",
        "nickname": "폴리오"
      },
      {
        "userId": "34",
        "nickname": "플레이어"
      }
    ],
    "createdAt": "2026-10-23T00:00:00Z"
  },
  "error": null
}
```

방장 여부는 참가자의 `userId`와 `hostUserId`를 비교해 구분한다. `members.nickname`은 `USERS.nickname`을 조회해 반환한다. ERD에 참가 시간 컬럼이 없어 `joinedAt`은 응답에 포함하지 않는다.

이 구조를 방 생성·ID 입장·코드 입장에서 사용하는 **참가자용 방 응답**으로 정의한다. 재입장 시 인원과 상태는 현재 값을 반환한다.

### 오류 응답

| HTTP 상태 | code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 방 ID가 양의 정수가 아님 |
| `401` | `UNAUTHENTICATED` | 로그인 정보가 없거나 유효하지 않음 |
| `403` | `ROOM_CODE_REQUIRED` | 신규 사용자가 비공개 방에 ID만으로 입장 시도 |
| `404` | `ROOM_NOT_FOUND` | 존재하지 않거나 삭제된 방 |
| `409` | `ROOM_FULL` | 신규 사용자가 정원이 찬 방에 입장 시도 |
| `409` | `MATCH_ALREADY_STARTED` | 신규 사용자가 게임 진행 중인 방에 입장 시도 |

## 5. 매치 룸 삭제

### 기본 정보

- Method: `DELETE`
- URL: `/api/match-room/{roomId}`
- 인증: 필요
- 권한: 해당 방의 방장
- 설명: 게임 시작 전인 방과 해당 방의 참가자 데이터를 삭제한다.

### 요청

| Path variable | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `roomId` | Integer | 예 | 양의 정수 |

요청 본문은 없다.

```http
DELETE /api/match-room/101
```

### 처리 규칙

1. 방을 조회한다. 없으면 `404 ROOM_NOT_FOUND`.
2. 로그인 사용자 ID가 `hostUserId`와 다르면 `403 ROOM_HOST_ONLY`.
3. 상태가 `PLAYING`이면 `409 MATCH_ALREADY_STARTED`.
4. DB에서 해당 방과 방에 포함된 모든 참가자 정보를 함께 삭제한다.
5. 삭제된 방의 입장 코드 연결도 무효화한다. 진행 중인 게임 시작 처리와 삭제가 동시에 성공하지 않도록 한다.

### 성공 응답: 204 No Content

응답 본문은 없다.

삭제 후 같은 방 ID를 사용하는 입장·삭제 요청은 `404`가 된다. 같은 방을 삭제하는 재요청도 `404 ROOM_NOT_FOUND`다.

WebSocket에 연결된 참가자가 있다면 삭제 완료 후 `ROOM_DELETED` 알림을 보내 대기 화면을 종료하도록 연계할 수 있다. 이 알림의 전송 형식과 재접속 처리는 WebSocket 명세에서 정의한다.

### 오류 응답

| HTTP 상태 | code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 방 ID가 양의 정수가 아님 |
| `401` | `UNAUTHENTICATED` | 로그인 정보가 없거나 유효하지 않음 |
| `403` | `ROOM_HOST_ONLY` | 요청자가 해당 방의 방장이 아님 |
| `404` | `ROOM_NOT_FOUND` | 존재하지 않거나 이미 삭제된 방 |
| `409` | `MATCH_ALREADY_STARTED` | 해당 방의 게임이 이미 시작됨 |

## 6. 매치 룸 생성

### 기본 정보

- Method: `POST`
- URL: `/api/match-room`
- 인증: 필요
- 설명: 방을 생성하고 인증된 사용자를 방장 겸 첫 참가자로 등록한다.

### 요청

```http
POST /api/match-room
Content-Type: application/json
```

```json
{
  "roomName": "같이 플레이해요",
  "isPrivate": false
}
```

| Body field | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `roomName` | String | 예 | null 불가. 앞뒤 공백 제거 후 1~30자 |
| `isPrivate` | Boolean | 예 | null 불가. `true` 또는 `false` |

방장 ID, 입장 코드, 정원, 상태는 서버에서 결정한다.

### 처리 규칙

1. 요청을 검증하고 방 이름의 앞뒤 공백을 제거한다.
2. 로그인 사용자를 `host_user_id`로 설정한다.
3. 공개·비공개 여부와 관계없이 중복되지 않는 `entry_code`를 생성한다.
4. 상태 `WAITING`으로 방을 저장하고 방장을 `MATCH_PARTICIPANT`에 등록한다.
5. 방과 방장의 참가 정보를 하나의 불변 객체로 구성해 저장한다. 방장 참가자가 없는 방을 저장하지 않는다.

### 성공 응답: 201 Created

```json
{
  "success": true,
  "data": {
    "roomId": 101,
    "roomName": "같이 플레이해요",
    "hostUserId": "12",
    "isPrivate": false,
    "entryCode": "K7M3PX9Q",
    "maxPlayers": 2,
    "playerCount": 1,
    "status": "WAITING",
    "members": [
      {
        "userId": "12",
        "nickname": "폴리오"
      }
    ],
    "createdAt": "2026-10-23T00:00:00Z"
  },
  "error": null
}
```

### 오류 응답

| HTTP 상태 | code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 잘못된 JSON, 필수 필드 누락, 잘못된 타입, 공백뿐인 이름, 길이 초과 |
| `401` | `UNAUTHENTICATED` | 로그인 정보가 없거나 유효하지 않음 |

## 7. 매치 룸 코드로 입장

### 기본 정보

- Method: `POST`
- URL: `/api/match-room/code/members`
- 인증: 필요
- 설명: 입장 코드에 해당하는 공개·비공개 방에 인증된 사용자를 등록한다. URL의 `code`는 고정 문자열이다.

### 요청

```http
POST /api/match-room/code/members
Content-Type: application/json
```

```json
{
  "entryCode": "H8N4RT6W"
}
```

| Body field | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `entryCode` | String | 예 | null 불가. 정규화 후 허용 문자로 구성된 8자리 |

예를 들어 `" h8n4rt6w "`는 `"H8N4RT6W"`로 정규화한다. 코드 형식이 잘못됐으면 `400`, 형식은 맞지만 일치하는 방이 없으면 `404`다.

### 처리 규칙

1. 요청을 검증하고 코드를 정규화한다.
2. `entry_code`로 방을 조회한다. 없으면 `404 ROOM_NOT_FOUND`.
3. 이미 참가한 사용자면 현재 방 정보를 `200`으로 반환한다.
4. 신규 사용자이고 게임 진행 중이면 `409 MATCH_ALREADY_STARTED`.
5. 신규 사용자이고 정원이 찼으면 `409 ROOM_FULL`.
6. 참가자를 등록하고 상태를 `FULL`로 변경해 응답한다.

### 성공 응답: 200 OK

```json
{
  "success": true,
  "data": {
    "roomId": 102,
    "roomName": "친구와 한 판",
    "hostUserId": "56",
    "isPrivate": true,
    "entryCode": "H8N4RT6W",
    "maxPlayers": 2,
    "playerCount": 2,
    "status": "FULL",
    "members": [
      {
        "userId": "56",
        "nickname": "블록친구"
      },
      {
        "userId": "34",
        "nickname": "플레이어"
      }
    ],
    "createdAt": "2026-10-23T00:05:00Z"
  },
  "error": null
}
```

ID 입장과 같은 참가자용 방 응답 구조를 사용한다. 공개 방에 코드를 사용해 입장했다면 `isPrivate`은 `false`다.

### 오류 응답

| HTTP 상태 | code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 코드 누락, null, 잘못된 타입, 빈 문자열, 길이·문자 형식 오류 |
| `401` | `UNAUTHENTICATED` | 로그인 정보가 없거나 유효하지 않음 |
| `404` | `ROOM_NOT_FOUND` | 코드에 해당하는 방이 없음 |
| `409` | `ROOM_FULL` | 신규 사용자가 정원이 찬 방에 입장 시도 |
| `409` | `MATCH_ALREADY_STARTED` | 신규 사용자가 게임 진행 중인 방에 입장 시도 |

## 8. 공통 응답 형식

`common` 패키지의 공통 응답을 사용한다. 성공 응답은 `{"success":true,"data":응답데이터,"error":null}`, 실패 응답은 `{"success":false,"data":null,"error":{"code":"오류코드","message":"설명"}}`를 사용한다. 삭제 성공은 예외로 본문 없는 `204`다. 위 필드 설명의 방·목록 데이터는 모두 `data` 안에 위치한다.

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ROOM_FULL",
    "message": "방 정원이 가득 찼습니다."
  }
}
```

| code | HTTP 상태 | message 예시 |
| --- | --- | --- |
| `INVALID_REQUEST` | `400` | 요청 값이 올바르지 않습니다. |
| `UNAUTHENTICATED` | `401` | 로그인이 필요합니다. |
| `ROOM_CODE_REQUIRED` | `403` | 비공개 방은 입장 코드가 필요합니다. |
| `ROOM_HOST_ONLY` | `403` | 방장만 방을 삭제할 수 있습니다. |
| `ROOM_NOT_FOUND` | `404` | 방을 찾을 수 없습니다. |
| `ROOM_FULL` | `409` | 방 정원이 가득 찼습니다. |
| `MATCH_ALREADY_STARTED` | `409` | 이미 게임이 시작된 방입니다. |
| `INTERNAL_SERVER_ERROR` | `500` | 일시적인 서버 오류가 발생했습니다. |

컨트롤러에 필요한 사용자 정보가 없으면 같은 오류 구조로 `401`을 반환한다. 보안 필터 단계의 오류 응답 형식은 정식 인증 설정과 연동할 때 통일한다. 클라이언트는 `message`가 아닌 `code`를 기준으로 분기한다. 예측하지 못한 서버 오류는 모든 API에서 `500 INTERNAL_SERVER_ERROR`로 처리하며 내부 예외 내용을 노출하지 않는다.

## 9. ERD 매핑과 현재 구현

| API 필드 | ERD 또는 계산 기준 |
| --- | --- |
| `roomId` | `MATCH_ROOM.id` |
| `roomName` | `MATCH_ROOM.room_name` |
| `hostUserId` | `MATCH_ROOM.host_user_id` |
| `entryCode` | `MATCH_ROOM.entry_code` |
| `isPrivate` | `MATCH_ROOM.is_private` |
| `status` | `MATCH_ROOM.status` |
| `createdAt` | `MATCH_ROOM.created_at` |
| `members` | `MATCH_PARTICIPANT.room_id`로 조회한 참가자와 `USERS`를 연결 |
| `nickname`, `hostNickname` | `USERS.nickname` |
| `maxPlayers` | 서버 상수 `2`. 추가 DB 컬럼 불필요 |
| `playerCount` | 해당 방의 참가자 수. 추가 DB 컬럼 불필요 |

- 매치 룸은 `ConcurrentHashMap` 기반 저장소를 사용한다. 현재는 DB, JPA 엔티티, 외래 키를 추가하지 않는다.
- 방 데이터는 참가자 목록을 포함한 불변 객체이며, ID 입장·코드 입장·삭제는 같은 방 ID의 `compute` 연산으로 순서를 보장한다. 두 요청이 마지막 자리에 동시에 입장해도 한 명만 추가된다.
- 코드 인덱스의 `putIfAbsent`로 코드 유일성을 보장하고 충돌 시 새 코드를 생성한다. 삭제 시 코드 인덱스도 제거한다.
- 중복 참가 여부는 방에 대한 원자적 연산 안에서 검사한다. 같은 사용자의 동시 재요청도 참가자를 중복 생성하지 않는다.
- 컨트롤러는 요청·응답 변환을, 서비스는 목록 정렬·페이지네이션과 저장소 연결을, 방 도메인은 권한·정원·상태 정책을 처리한다.
- 매치 룸은 `MatchRoomUser`로 전달된 사용자 정보만 사용한다. 회원가입·로그인·토큰 검증 코드는 이 브랜치에 없으며, 실제 인증 결과를 principal에 연결하는 작업은 정식 인증 브랜치 반영 후 수행한다.
- DB 도입 시 `MATCH_ROOM.entry_code` 유일 제약, 참가자의 `UNIQUE(room_id, user_id)`, ERD의 외래 키를 적용하고 방 단위 DB 잠금 또는 동등한 원자적 처리를 사용해야 한다. 현재 구현은 단일 서버 프로세스를 기준으로 한다.

## 10. 구현 후 검증 항목

- 방 생성 시 방장 참가자 등록과 `WAITING` 상태가 함께 저장되는지 확인한다.
- 공개 방을 ID와 코드 두 방식으로 입장할 수 있는지 확인한다.
- 비공개 방에 신규 사용자가 ID로 입장하면 거절되고, 올바른 코드로는 입장되는지 확인한다.
- 목록에 공개·비공개 대기방이 표시되고 코드와 진행 중인 방은 제외되는지 확인한다.
- 두 번째 사용자 입장 시 `FULL`이 되고 세 번째 사용자는 저장되지 않는지 확인한다.
- 기존 사용자의 재입장이 정원이나 상태와 관계없이 참가자를 중복 생성하지 않는지 확인한다.
- ID 입장과 코드 입장을 동시에 요청해도 전체 인원이 2명을 넘지 않는지 확인한다.
- 방장 외 사용자의 삭제, 진행 중 삭제, 삭제된 방의 재요청이 정의한 오류로 반환되는지 확인한다.
- 입장과 삭제가 동시에 발생해도 방과 참가자 상태가 일치하는지 확인한다. 게임 시작과의 경합 검증은 WebSocket 연동을 추가할 때 수행한다.
- 잘못된 생성 본문, 코드 형식, 방 ID, 페이지 값과 미인증 요청을 검증한다.


