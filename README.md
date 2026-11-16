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

Java 21 환경에서 Spring Boot를 실행합니다.

```bash
cd polyo
./gradlew bootRun
```

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
