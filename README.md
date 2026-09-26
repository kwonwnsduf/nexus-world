# NEXUS WORLD

> 근거 기반 경제·문명 시뮬레이션과 공급망 디지털 트윈

NEXUS WORLD는 외부의 경제·산업 데이터를 수집해 출처와 가정을 보존하고, 이를 버전이 고정된 세계 그래프로 구성한 뒤, 자연어로 입력한 충격 시나리오를 결정론적으로 비교 실행하는 프로젝트입니다.

이 저장소는 **Phase 1 / Day 21 구현을 최종 기준선으로 마무리**했습니다. 핵심 결과물은 실제 데이터에서 만들어진 동일한 World Version 위에서 공급망 충격과 정책 대안을 A/B/C 분기로 실행하고, 결과뿐 아니라 사용한 근거·그래프 경로·수치 계보·재현 해시까지 함께 확인할 수 있는 수직 통합 플랫폼입니다.

## 핵심 가치

- **Evidence-grounded**: 관측값, 파생값, 사용자 가정을 구분하고 원본 출처와 속성 단위 계보를 보존합니다.
- **Versioned world**: 시뮬레이션은 변경되지 않는 World Version을 기준으로 실행되어 입력 시점을 추적할 수 있습니다.
- **Deterministic simulation**: LLM은 자연어 해석을 보조하고, 수치 변화와 전파는 버전이 지정된 규칙과 시드가 담당합니다.
- **Fail closed**: 필요한 수치나 관계 계수가 없으면 값을 지어내지 않고 `INSUFFICIENT_DATA`로 종료합니다.
- **Comparable branches**: 동일한 세계와 시드에서 기본 충격, 정책 대안, 대체 공급 시나리오를 병렬 비교합니다.
- **Reproducible output**: 실행별 불변조건 검사와 replay hash를 기록해 결과 재현성을 검증합니다.

## 완성된 범위

1. SEC, OpenDART, UN Comtrade, World Bank 등 외부 데이터의 감사 가능한 수집
2. 원본 payload, 정규화 레코드, 거부 레코드, Source/Evidence/Provenance의 분리 저장
3. 경제·산업 온톨로지와 보수적인 식별자 기반 엔티티 해소
4. PostgreSQL 기반 권위 그래프와 선택적 Neo4j projection
5. PostgreSQL FTS와 pgvector를 결합한 hybrid retrieval 및 GraphRAG
6. 자연어 공급망 충격의 구조화, 대상 엔티티 해소, 데이터 품질 검증
7. 관계별 규칙을 사용하는 결정론적 그래프 전파와 A/B/C 병렬 실행
8. 로컬 JWT 인증, refresh token rotation, 역할 기반 접근 제어와 감사 상태
9. Docker Compose 로컬 환경과 AWS 단일 EC2 데모 배포 경로
10. 공개 Web → Core → AI → PostgreSQL 경로를 검증하는 smoke/E2E gate

구현 범위와 검증 근거는 [Day 21 문서](docs/days/DAY-21.md), 제품 의도는 [PRD](docs/PRD.md), 전체 설계는 [Architecture](docs/ARCHITECTURE.md)에서 더 자세히 볼 수 있습니다.

## 시스템 구조

```text
Browser
  └─ Next.js Web / BFF
       └─ Spring Boot Core API
            ├─ PostgreSQL + pgvector (권위 데이터, 검색, 그래프)
            ├─ FastAPI AI Service (검색, 해석, 시뮬레이션)
            └─ Neo4j (선택적 파생 projection)
```

| 구성 요소 | 역할 | 기본 주소 |
|---|---|---|
| `apps/web` | Next.js UI와 외부 공개 BFF | http://localhost:3000 |
| `services/core-api` | 인증, 데이터 수집, World/Scenario/Run, 감사 상태의 system of record | http://localhost:8080 |
| `services/ai-service` | retrieval, GraphRAG, 자연어 해석, 결정론적 시뮬레이션 | http://localhost:8000 |
| PostgreSQL | 관계형 원본, pgvector 검색, 권위 그래프 | localhost:5432 |
| Neo4j | 선택적 관계 탐색 projection | localhost:7474 / 7687 |

Web은 데이터베이스에 직접 접근하지 않으며, 서비스 간 payload는 `contracts/`의 버전 계약을 따릅니다. PostgreSQL이 정본이고 Neo4j는 언제든 재구성할 수 있는 파생 인덱스입니다.

## 대표 실행 흐름

```text
외부 데이터 수집
  → 원본/정규화/거부 레코드와 provenance 저장
  → 온톨로지 검증 및 엔티티 해소
  → World graph와 불변 World Version 생성
  → 검색 문서 인덱싱 및 READY 검증
  → 자연어 what-if 입력
  → 대상·지표·변화율·기간 해석
  → 같은 World Version의 GraphRAG 근거 조회
  → A/B/C 결정론적 시뮬레이션
  → 지표·인용·계보·불변조건·replay hash 반환
```

LLM은 대상 후보와 충격 구조를 추출할 뿐, 기준값·전파 계수·시뮬레이션 결과를 생성하지 않습니다. Core API가 World Version 내부의 엔티티와 데이터 완전성을 확인한 후에만 시뮬레이션을 실행합니다.

## 기술 스택

- Web: Next.js 16, React 19, TypeScript 5, Vitest
- Core API: Java 17, Spring Boot 3.5, Spring Security, JPA, Flyway, Gradle
- AI Service: Python 3.12, FastAPI, Pydantic, pytest, Ruff, mypy
- Data: PostgreSQL 16, pgvector, 선택적 Neo4j 5
- Platform: Docker Compose, Nginx, Terraform, AWS EC2/SSM/S3
- Contracts: OpenAPI와 JSON Schema 기반 `v1` 계약

## 빠른 시작

### 준비 사항

- Git
- Docker Desktop (Linux containers)
- 로컬 검증 시 Node.js + Corepack, Java 17

Python은 로컬에 설치하지 않아도 AI Service를 Docker에서 빌드하고 검사할 수 있습니다.

### 실행

```powershell
git clone <repository-url>
cd nexus-world
Copy-Item .env.example .env
powershell -ExecutionPolicy Bypass -File .\scripts\doctor.ps1
docker compose up --build -d
powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1
```

브라우저에서 http://localhost:3000 을 엽니다. 로컬 데모의 기본 관리자 계정은 다음과 같습니다.

```text
username: admin
password: nexus-world-local-admin
```

이 값은 개발 전용입니다. 공유·배포 환경에서는 `.env`의 JWT secret, 데이터베이스 비밀번호, bootstrap 관리자 비밀번호를 반드시 교체해야 합니다.

서비스 종료:

```powershell
docker compose down
```

데이터 volume까지 삭제하려면 그 데이터가 더 이상 필요하지 않은지 확인한 뒤 `docker compose down -v`를 별도로 실행하십시오.

### 선택적 플랫폼 서비스

Redis, Neo4j, Redpanda는 기본 실행에 필요하지 않습니다.

```powershell
$env:NEO4J_ENABLED="true"
docker compose --profile platform up --build -d
```

Neo4j가 꺼져 있어도 PostgreSQL 그래프가 권위 저장소와 bounded path traversal을 담당합니다.

## 환경 설정

`.env.example`을 복사해 사용하고 실제 키가 들어간 `.env`는 커밋하지 마십시오.

| 분류 | 주요 변수 | 설명 |
|---|---|---|
| 인증 | `JWT_SECRET_BASE64`, `BOOTSTRAP_ADMIN_*` | JWT 서명과 로컬 관리자 bootstrap |
| AI | `OPENAI_API_KEY`, `OPENAI_*_MODEL` | embedding과 자연어 시나리오 해석; health check에는 불필요 |
| 수집 | `SEC_USER_AGENT`, `OPENDART_API_KEY`, `UN_COMTRADE_API_KEY`, `KOSIS_API_KEY` | 공급자별 자격 정보 |
| 그래프 | `NEO4J_ENABLED`, `NEO4J_URI`, `NEO4J_*` | 선택적 Neo4j projection |
| 스케줄 | `INGESTION_SCHEDULE_ENABLED`, `INGESTION_SCHEDULE_SOURCES_JSON` | 감사 가능한 정기 수집; 기본값은 비활성 |
| 데모 | `DEMO_MODE_ENABLED`, `DEMO_USERNAME`, `DEMO_PASSWORD` | Web BFF의 로컬 데모 실행 |

외부 API 및 OpenAI 키가 없어도 서비스 health check와 fixture 기반 테스트는 동작합니다. 실제 데이터 수집, embedding, 자연어 시나리오 실행에는 해당 공급자의 키 또는 설정이 필요할 수 있습니다.

## 데이터 소스

수집 어댑터는 SEC, OpenDART, UN Comtrade, World Bank, USGS, UN/LOCODE, World Port Index, HS, ISIC, UN WPP, ILOSTAT, KOSIS, OECD를 지원합니다. 관리자는 인증 후 아래 API로 수집을 시작하고 상태를 조회할 수 있습니다.

```http
POST /api/v1/admin/ingestions/{source}
GET  /api/v1/admin/ingestions/{id}
GET  /api/v1/admin/ingestions
```

요청 파라미터와 공급자별 제약은 [Ingestion Request Plan](docs/INGESTION_REQUEST_PLAN.md), 데이터 분류와 계보는 [Data Catalog](docs/DATA_CATALOG.md)를 참고하십시오.

## 주요 API

| 영역 | 대표 endpoint |
|---|---|
| 상태 | `GET /api/platform/status`, `GET /api/v1/platform/status`, `GET /api/v1/platform/capabilities` |
| 인증 | `POST /api/v1/auth/login`, `refresh`, `logout`; `GET /api/v1/auth/me` |
| 근거 | `/api/v1/sources`, `/evidence`, `/assumptions`, `/provenance-links` |
| 온톨로지/그래프 | `/api/v1/ontology`, `/api/v1/world-versions/{id}/graph/**` |
| 검색/GraphRAG | `/api/v1/retrieval/**`, `POST /api/v1/graphrag/query` |
| 시뮬레이션 | `POST /api/v1/scenario-runs/from-query`, `/world-versions/{id}/parallel-simulations` |

정확한 요청·응답 형식은 [계약 안내](contracts/README.md)와 `contracts/openapi`, `contracts/schemas`를 기준으로 합니다.

## 검증

영향받은 서비스에 대해 아래 검증을 실행하는 것이 프로젝트의 최종 품질 기준입니다.

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

live ingestion 테스트는 기본적으로 꺼져 있으며, fixture 테스트는 외부 네트워크 없이 실행됩니다. 실제 데이터로 전체 공개 경로를 검증하는 Day 21 gate는 READY World와 필요한 자격 정보가 준비된 환경에서만 `RUN_GROUNDED_E2E=true`로 실행합니다.

## 저장소 구조

```text
apps/web/                 Next.js UI와 BFF
services/core-api/        Spring Boot 도메인/API와 Flyway migration
services/ai-service/      FastAPI retrieval, GraphRAG, simulation
contracts/                OpenAPI 및 JSON Schema 버전 계약
docs/                     설계, 사양, 운영, 평가, 일자별 구현 기록
infra/aws/                Terraform 기반 AWS 인프라
deploy/                   배포 구성
scripts/                  진단, smoke, 운영 보조 스크립트
compose.yaml              로컬 통합 환경
```

## 설계 원칙과 한계

- 수치 상태 변화는 LLM 출력이 아니라 시뮬레이션 규칙에서만 발생합니다.
- 공개 사회 API는 집계만 노출하고 작은 셀은 억제하는 방향을 따릅니다.
- 이전 World Version은 수정하지 않으며 새 데이터는 새 버전을 만듭니다.
- Neo4j, Redis, Redpanda는 확장 지점이며 Phase 1 핵심 경로의 필수 의존성이 아닙니다.
- AWS 구성은 비용을 고려한 단일 EC2 데모 토폴로지로, 단일 장애점이 있습니다. 고가용성 운영 환경으로 간주하면 안 됩니다.
- 외부 데이터의 라이선스·갱신 주기·정확성은 각 원 제공자의 정책을 따릅니다.
- 본 결과는 분석과 시나리오 비교를 위한 것이며 투자·법률·정책 결정의 단독 근거가 아닙니다.

## 문서 안내

- [Master Plan](docs/MASTER_PLAN.md): 전체 프로젝트 계획과 단계
- [PRD](docs/PRD.md): 제품 목표와 사용자 흐름
- [Architecture](docs/ARCHITECTURE.md): 서비스 경계와 기술 구조
- [API & Event Contracts](docs/API_EVENT_CONTRACTS.md): 계약 설계
- [Domain Ontology](docs/DOMAIN_ONTOLOGY.md): 경제·산업 그래프 모델
- [Simulation Specification](docs/SIMULATION_SPEC.md): 시뮬레이션 규칙과 불변조건
- [Retrieval Evaluation](docs/RETRIEVAL_EVALUATION.md): 검색 품질 평가
- [Security](docs/SECURITY.md): 인증과 보안 모델
- [Operations](docs/OPERATIONS.md): 로컬·배포 운영 기준
- [Day 01–21 기록](docs/days/): 구현 과정과 일자별 acceptance criteria

## 프로젝트 상태

NEXUS WORLD Phase 1은 Day 21 기준선으로 종료되었습니다. 이 저장소는 연구·데모·후속 확장을 위한 최종 산출물로 보존합니다. 후속 개발을 시작한다면 기존 계약과 불변 World Version 원칙을 유지하고, 변경 범위를 새 계약 버전과 문서에 먼저 기록하는 것을 권장합니다.
