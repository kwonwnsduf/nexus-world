# NEXUS WORLD

경제·산업 데이터를 근거와 함께 저장하고, 고정된 세계 버전에서 공급망 충격 시나리오를 비교하는 플랫폼입니다. 수치 변화는 시드와 규칙을 사용하는 시뮬레이션 코드가 계산하며, 자연어 모델은 시나리오 해석을 보조합니다.

현재 기준선은 [Phase 1 / Day 21](docs/days/DAY-21.md)입니다. 실제 외부 데이터 수집과 AI 기능은 해당 공급자의 설정과 데이터 준비 상태에 따라 사용 가능 여부가 달라집니다.

## 구성

| 경로 | 역할 |
| --- | --- |
| `apps/web` | Next.js 화면과 공개 HTTP API를 호출하는 BFF |
| `services/core-api` | Spring Boot 기반 세계·시나리오·작업·승인·감사 상태 관리 |
| `services/ai-service` | FastAPI 기반 검색, 에이전트 오케스트레이션, 시뮬레이션 계산 |
| `contracts` | 서비스 사이의 버전별 OpenAPI·JSON Schema 계약 |

Web은 Core API와 AI Service의 공개 HTTP 계약을 사용하며 데이터베이스에 직접 연결하지 않습니다. PostgreSQL이 권위 데이터와 그래프의 저장소입니다. Neo4j, Redis, Redpanda는 선택적 `platform` 프로필에 포함됩니다.

## 로컬 실행

Git, Docker Desktop(Linux 컨테이너), Node.js/Corepack, Java 17이 필요합니다. Python은 AI Service를 Docker에서 빌드할 경우 로컬 설치가 필요하지 않습니다.

```powershell
Copy-Item .env.example .env
powershell -ExecutionPolicy Bypass -File .\scripts\doctor.ps1
docker compose up --build -d
powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1
```

앱은 <http://localhost:3000>에서 열립니다. `.env.example`의 기본 관리자 계정은 로컬 개발용 `admin` / `nexus-world-local-admin`입니다. 공유 또는 배포 환경에서는 `.env`의 비밀번호와 JWT 비밀값을 교체하세요. `.env`는 커밋하지 않습니다.

```powershell
docker compose down
```

선택적 플랫폼 서비스를 함께 실행하려면 다음 명령을 사용합니다.

```powershell
$env:NEO4J_ENABLED = "true"
docker compose --profile platform up --build -d
```

OpenAI 키와 외부 데이터 공급자 키는 기본 실행과 헬스 체크에 필요하지 않습니다. 실제 검색·수집·자연어 시나리오 기능을 사용하려면 `.env`에 해당 설정을 추가하고 필요한 데이터를 준비하세요. 설정 항목은 [.env.example](.env.example)을 참고하세요.

## 검증

CI는 Web의 계약·린트·타입·테스트·빌드, Core API의 Gradle 테스트와 JAR 빌드, AI Service의 Ruff·mypy·pytest를 검사합니다. 이 단계가 모두 통과하면 Compose를 시작해 공개 경로의 스모크 테스트를 실행합니다.

```powershell
corepack pnpm install --frozen-lockfile
corepack pnpm contracts:check
corepack pnpm web:lint
corepack pnpm web:typecheck
corepack pnpm web:test
corepack pnpm web:build
.\services\core-api\gradlew.bat -p .\services\core-api clean test bootJar
docker build --target test -t nexus-world-ai-test .\services\ai-service
docker compose config
docker compose up --build -d
powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1
docker compose down
```

스모크 테스트는 Web → Core → AI 공개 계약, PostgreSQL 연결, 로컬 인증 흐름을 확인합니다. 외부 데이터에 의존하는 통합 경로는 데이터 준비 후 `RUN_GROUNDED_E2E=true`로 별도 실행합니다.

## 주요 문서

- [제품 요구사항](docs/PRD.md)
- [아키텍처](docs/ARCHITECTURE.md)
- [API·이벤트 계약](docs/API_EVENT_CONTRACTS.md) 및 [계약 디렉터리 안내](contracts/README.md)
- [시뮬레이션 사양](docs/SIMULATION_SPEC.md)
- [데이터 카탈로그](docs/DATA_CATALOG.md) 및 [수집 요청 안내](docs/INGESTION_REQUEST_PLAN.md)
- [운영 안내](docs/OPERATIONS.md)
