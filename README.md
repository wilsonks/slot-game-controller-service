# slot-game-controller-service

**Spin orchestration service** for the `slot-central` microservices platform.

Pure orchestration layer: validate JWT → reserve bet (Bank) → compute spin result (Game Engine) → settle winnings (Bank) → persist game history → return result.

Part of the re-architecture of `slot-central-server-express-rmq` (Node.js/Express EGM slot-floor monolith) into Spring Boot microservices.

- **Spring Boot 3.3.x**, Java 21, Gradle
- **Postgres** (Spring Data JPA + Flyway)
- **Resilience4j** circuit breakers + retries on all downstream calls
- **Spring Security** OAuth2 resource-server (JWT validated against Auth Service JWKS)

---

## Platform Context

```
slot-api-gateway (edge, port 8080)
    ↓ JWT-validated requests
slot-auth-service (port 8081)           ← issues JWTs, exposes JWKS
slot-game-controller-service (port 8082) ← THIS SERVICE — spin orchestration
slot-game-engine-service (port 8083)    ← RNG / reels / paytables (not yet built)
slot-bank-service (port 8084)           ← wallet / ledger (not yet built)
slot-floor-management-service           ← EGM/station registry
slot-jackpot-service                    ← progressive jackpot pool
slot-config-service                     ← feature flags
slot-logging-service                    ← audit trail
```

---

## Orchestration Sequence Diagram

```
Client (EGM/terminal)
  │
  │  POST /api/v1/spin  {gameId, betAmount, lines, denomination}
  │  Authorization: ******
  ▼
slot-game-controller-service
  │
  ├─1─ Validate JWT (Spring Security resource-server, verifies against Auth JWKS)
  │    Extract playerUid from sub claim → reject 401 if missing/invalid
  │
  ├─2─ Generate spinId (UUID) — idempotency key for all downstream calls
  │    Persist GameRecord{status=PENDING} in local Postgres
  │
  ├─3─ POST /api/v1/bank/reserve  → slot-bank-service
  │    {spinId, playerUid, amount}
  │    ◦ Failure → update status=PENDING, return 502 (retryable)
  │    ◦ Circuit breaker + 3 retries configured
  │
  ├─4─ POST /api/v1/engine/spin   → slot-game-engine-service
  │    {spinId, gameId, betAmount, lines, denomination}
  │    ◦ Failure → update status=ENGINE_FAILURE, return 502
  │    ◦ Circuit breaker + 3 retries configured
  │
  ├─5─ POST /api/v1/bank/settle   → slot-bank-service
  │    {spinId, playerUid, winAmount}          ← same spinId for idempotency
  │    ◦ Failure → update status=SETTLEMENT_FAILED, return 500 with discrepancy body
  │      (do NOT silently return success — see Correctness Fixes section)
  │
  ├─6─ Update GameRecord{status=COMPLETED, payoutAmount, resultSummary}
  │
  └─7─ Return 201 Created  {spinId, playerUid, gameId, betAmount, payoutAmount, resultSummary, status, timestamp}
```

### Reconciliation Job

A scheduled job (`@Scheduled(fixedDelay=60s)`) scans for records with `status IN (SETTLEMENT_FAILED, PENDING)` and logs them for operator attention.

> **TODO**: Implement full outbox/retry logic — debit the payout to the player by re-calling Bank settle with the same `spinId` (idempotent). Add dead-letter queue integration after `n` retries.

---

## Endpoints

### `POST /api/v1/spin`

Requires `Authorization: ****** (player-type token issued by `slot-auth-service`).

**Request body**:
```json
{
  "gameId":       "GAME-001",
  "betAmount":    5.00,
  "lines":        20,
  "denomination": 1
}
```

| Field         | Type           | Required | Description                              |
|---------------|----------------|----------|------------------------------------------|
| `gameId`      | string         | ✅       | Game identifier                          |
| `betAmount`   | decimal        | ✅       | Bet amount per spin (must be > 0)        |
| `lines`       | integer        | ❌       | Number of bet lines (game engine default)|
| `denomination`| integer        | ❌       | Denomination multiplier                  |

> `playerUid` is extracted from the JWT `sub` claim — **not** from the request body.

**Success response** — `201 Created`:
```json
{
  "spinId":        "550e8400-e29b-41d4-a716-446655440000",
  "playerUid":     "player-123",
  "gameId":        "GAME-001",
  "betAmount":     5.00,
  "payoutAmount":  10.00,
  "resultSummary": "{\"reels\":[[\"7\",\"BAR\",\"7\"],[...]],\"winLines\":[1,3]}",
  "status":        "COMPLETED",
  "timestamp":     "2025-01-15T10:30:00Z"
}
```

**Error responses**:

| Status | When |
|--------|------|
| `401`  | Missing or invalid JWT |
| `422`  | Validation failure (missing gameId, betAmount ≤ 0) |
| `502`  | Bank service or Game Engine service unreachable / returned error |
| `500`  | Settlement failed after spin computed (SETTLEMENT_FAILED) — see body for reconciliation details |

**SETTLEMENT_FAILED body** (500):
```json
{
  "spinId":       "550e8400-...",
  "playerUid":    "player-123",
  "payoutAmount": 10.00,
  "message":      "Spin completed but settlement failed - requires reconciliation",
  "status":       "SETTLEMENT_FAILED"
}
```

---

### `GET /api/v1/history`

Requires `Authorization: ******

**Query parameters**:

| Parameter   | Type     | Description                              |
|-------------|----------|------------------------------------------|
| `playerUid` | string   | Filter by player                         |
| `gameId`    | string   | Filter by game                           |
| `status`    | string   | Filter by status (COMPLETED, PENDING, …) |
| `from`      | ISO 8601 | Start of date range (`createdAt >=`)     |
| `to`        | ISO 8601 | End of date range (`createdAt <=`)       |
| `page`      | integer  | Page number (0-indexed, default 0)       |
| `size`      | integer  | Page size (default 20)                   |
| `sort`      | string   | `field,direction` e.g. `createdAt,desc` |

**Example**:
```
GET /api/v1/history?playerUid=player-123&status=COMPLETED&from=2025-01-01T00:00:00Z&page=0&size=10
```

**Response** — Spring Data `Page<SpinResponse>`:
```json
{
  "content": [ { "spinId": "...", ... } ],
  "totalElements": 42,
  "totalPages": 5,
  "size": 10,
  "number": 0
}
```

---

## Downstream Service Client Contracts

These interfaces are defined in this codebase (`BankServiceClient`, `GameEngineServiceClient`) so those services can be built to match. Both use Spring `RestClient` with Resilience4j circuit breaker + retry.

### Bank Service (`slot-bank-service`, port 8084)

#### Reserve Bet — `POST /api/v1/bank/reserve`

Request:
```json
{
  "spinId":      "550e8400-...",   // idempotency key
  "playerUid":   "player-123",
  "amount":      5.00,
  "description": "Bet reserve for game GAME-001"
}
```
Response (`200 OK`):
```json
{
  "transactionId": "txn-abc123",
  "status":        "RESERVED",
  "message":       "Bet reserved successfully"
}
```

#### Settle Win — `POST /api/v1/bank/settle`

Request:
```json
{
  "spinId":      "550e8400-...",   // same spinId as reserve — idempotent
  "playerUid":   "player-123",
  "winAmount":   10.00,
  "description": "Win settlement for game GAME-001"
}
```
Response (`200 OK`):
```json
{
  "transactionId": "txn-def456",
  "status":        "SETTLED",
  "message":       "Win credited successfully"
}
```

### Game Engine Service (`slot-game-engine-service`, port 8083)

#### Compute Spin — `POST /api/v1/engine/spin`

Request:
```json
{
  "spinId":      "550e8400-...",
  "gameId":      "GAME-001",
  "betAmount":   5.00,
  "lines":       20,
  "denomination": 1
}
```
Response (`200 OK`):
```json
{
  "spinId":        "550e8400-...",
  "gameId":        "GAME-001",
  "payoutAmount":  10.00,
  "resultSummary": "{\"reels\":[[\"7\",\"BAR\",\"7\"],[...]],\"winLines\":[1,3]}",
  "valid":         true,
  "errorMessage":  null
}
```

When `valid=false` (e.g. invalid bet parameters):
```json
{
  "spinId":       "550e8400-...",
  "gameId":       "GAME-001",
  "payoutAmount": 0,
  "resultSummary": null,
  "valid":         false,
  "errorMessage":  "Bet amount exceeds maximum for this game"
}
```

---

## Correctness Fixes vs. the Monolith

The original Node.js monolith (`gameController.js`) had two notable bugs that are explicitly corrected in this rewrite:

### Fix 1: HTTP 201 on invalid spin → now 422 / 400 / 502

**Monolith behaviour**: `handleGetTestSpinRequest` returns HTTP **201** even when `validateOneUserSpinRequest` returns an error — embedding the error in the response body with a 201 status code.

**This service**: Returns **422 Unprocessable Entity** for validation failures (invalid bet, missing fields), **401** for auth failures, **502** for downstream service errors. HTTP **201** is returned only when a spin is genuinely created and completed successfully.

### Fix 2: Silent failure on game-record save → now explicit SETTLEMENT_FAILED status

**Monolith behaviour**: `computeOneUserSpinRequest` is called, then `saveGameRecord(result)` is called — but the result is returned **either way**, even if the save (or the downstream `game-service` settle call) fails silently.

**This service**: If the Bank settle call fails after the Game Engine has computed a result:
1. The `GameRecord` is persisted with `status=SETTLEMENT_FAILED` (never silently swallowed).
2. The API returns **HTTP 500** with a structured `SpinDiscrepancyResponse` body containing the `spinId` for operator reconciliation.
3. A scheduled reconciliation job scans for `SETTLEMENT_FAILED` records periodically.
4. The `spinId` is used as an idempotency key on the Bank settle call, so safe retry will not double-credit.

---

## Environment Variables

| Variable               | Default                                     | Description                              |
|------------------------|---------------------------------------------|------------------------------------------|
| `DB_URL`               | `jdbc:postgresql://localhost:5432/gamecontroller` | Postgres JDBC URL               |
| `DB_USER`              | `gamecontroller`                            | Postgres username                        |
| `DB_PASSWORD`          | `gamecontroller`                            | Postgres password                        |
| `BANK_SERVICE_URL`     | `http://localhost:8084`                     | Base URL for slot-bank-service           |
| `GAME_ENGINE_SERVICE_URL` | `http://localhost:8083`                  | Base URL for slot-game-engine-service    |
| `AUTH_SERVICE_JWKS_URL`| `http://localhost:8081/.well-known/jwks.json` | JWKS endpoint for JWT validation       |

---

## Running Locally

### With Docker Compose (Postgres only)

```bash
docker-compose up -d postgres
./gradlew bootRun
```

> Downstream services (Bank, Game Engine) are not yet built. The service will start, but spin calls will fail with 502 until those services exist.

### Full Docker

```bash
docker-compose up --build
```

### Build & Test

```bash
./gradlew build          # compile + all tests
./gradlew test           # tests only
./gradlew bootJar        # produce runnable JAR
```

---

## Testing

| Test Class | Type | Description |
|---|---|---|
| `SpinOrchestrationServiceTest` | Unit (Mockito) | Happy path, Engine failure, Settlement failure (SETTLEMENT_FAILED status), Invalid bet |
| `BankServiceClientTest` | WireMock contract | Reserve and settle HTTP interactions |
| `GameEngineServiceClientTest` | WireMock contract | Compute spin HTTP interaction |
| `GameRecordRepositoryTest` | Data (H2) | Filtering by playerUid / gameId / status / date range, pagination |

---

## Game History Schema

```sql
CREATE TABLE game_records (
    id               BIGSERIAL PRIMARY KEY,
    spin_id          VARCHAR(36) NOT NULL UNIQUE,  -- idempotency key
    player_uid       VARCHAR(255) NOT NULL,
    game_id          VARCHAR(255) NOT NULL,
    bet_amount       NUMERIC(19,4) NOT NULL,
    payout_amount    NUMERIC(19,4),
    result_summary   TEXT,                         -- JSON from Game Engine
    status           VARCHAR(50) NOT NULL,         -- COMPLETED | PENDING | INVALID_BET | ENGINE_FAILURE | SETTLEMENT_FAILED
    bank_reserve_status VARCHAR(100),
    bank_settle_status  VARCHAR(100),
    engine_status       VARCHAR(100),
    correlation_id   VARCHAR(36),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## Observability

- **Health**: `GET /actuator/health`
- **Prometheus metrics**: `GET /actuator/prometheus`
- **Correlation ID**: `X-Correlation-Id` header read from incoming requests (or generated if absent), propagated on all outbound calls to Bank and Game Engine, available in structured logs via MDC.
- **Structured JSON logging**: `logback-spring.xml` with logstash-logback-encoder.

---

## TODOs

- [ ] **Reconciliation retry job**: Implement full outbox/retry for `SETTLEMENT_FAILED` records — call Bank settle with same `spinId` (idempotent), integrate dead-letter queue after `n` retries.
- [ ] **Circuit breaker tuning**: Tune `slidingWindowSize`, `failureRateThreshold`, and `waitDurationInOpenState` based on observed production traffic patterns.
- [ ] **Distributed tracing**: Add `spring-boot-starter-actuator` + Micrometer Tracing / OpenTelemetry for distributed trace propagation alongside correlation IDs.
- [ ] **Rate limiting**: Enforce per-player spin rate limit (delegate to `slot-api-gateway` or add local token-bucket).
- [ ] **Jackpot integration**: Add call to `slot-jackpot-service` after Game Engine compute to check/update progressive jackpot pools.
- [ ] **Real signature validation**: Currently validates JWT against Auth Service JWKS. Add player-type claim check (`role=PLAYER`) to prevent staff tokens from calling spin endpoints.
- [ ] **OpenAPI spec**: Add `springdoc-openapi-ui` for auto-generated API docs.
