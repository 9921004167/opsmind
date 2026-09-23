# OpsMind Architecture — Phase 1 Foundation

## 1. Target architecture (end state)

```
                       REAL USERS
                           │
                           ▼
                 E-COMMERCE APPLICATION            <- Phase 3+ (not built yet)
      (API Gateway, Order, Payment, Inventory, ...)
                           │
                    TELEMETRY / EVENTS             <- Phase 4+ (not built yet)
                           │
                           ▼
                    ┌──────────────┐
                    │   OPSMIND    │
                    │              │
                    │ Detection    │  <- Phase 1: alert ingestion (done, naive)
                    │ Correlation  │  <- Phase 6 (not built)
                    │ Investigation│  <- Phase 7 (not built)
                    │ RAG/Memory   │  <- Phase 8 (not built)
                    │ Remediation  │  <- Phase 10-13 (not built)
                    │ Verification │  <- Phase 12 (not built)
                    └──────┬───────┘
                           │
                           ▼
                    INCIDENT RESOLVED
```

Phase 1 builds the vertical slice of OpsMind itself (not the e-commerce app) that
every other box above eventually plugs into: tenant model, auth, the alert front
door, the incident state machine + timeline, the Kafka event backbone, and the audit
trail.

## 2. Why these specific boundaries, and not others

**Two independent systems, never one.** OpsMind observes and acts on the monitored
application from the outside. If OpsMind's code lived inside the monitored services,
a failure there could take down the thing meant to detect the failure. Contract
surface between them is intentionally narrow: an HTTP alert ingestion API in, Kafka
events out. No shared database, no shared code.

**One deployable (`opsmind-core`) for Phase 1, not a microservices mesh yet.**
Splitting into separate "detection service" / "correlation service" / "investigation
service" containers before those capabilities exist would add network calls and
deployment complexity around nothing. What matters now is that the *code* is
partitioned correctly (see package list below) so any of these can be extracted into
its own deployable later without a rewrite — only a build/deploy change.

**Package boundaries and why each exists:**

| Package | Why it exists |
|---|---|
| `tenant` | Organization → Project → Environment → MonitoredService, plus Users/Roles. Everything else hangs off this. Built first because retrofitting tenant isolation onto alerts/incidents later is a painful migration; doing it now means every later phase inherits real multi-tenancy for free. |
| `auth` | JWT issuance/verification, registration, login. No endpoint in this codebase is ever "temporarily open" — auth exists before any protected endpoint does. |
| `alert` | The generic, source-agnostic ingestion front door (`POST /api/alerts`). Deliberately has no provider-specific fields, per the spec's "pluggable alert providers" requirement. |
| `incident` | The spine of the whole platform: `Incident`, its timeline (`IncidentEvent`), and the `IncidentStateMachine`. Every later phase (correlation, AI investigation, remediation, verification) is ultimately "something that moves an incident through this state machine and appends to its timeline" — nothing else in the system is allowed to mutate incident status directly. |
| `kafka` | Declares the *entire* event vocabulary from the spec now (including topics no Phase 1 code produces yet), so Phase 2+ doesn't invent inconsistent event names. `EventPublisher` and `PlatformEventListener` are both real — the consumer exists specifically to prove the producer → broker → consumer loop isn't just "the send() call didn't throw." |
| `audit` | Every automated and human action of consequence writes here. Built alongside the first feature that needs auditing (org registration), not bolted on afterward, because the spec requires the platform to never be a black box about its own actions. |
| `config` | Security filter chain, JWT filter, Kafka topic auto-provisioning, OpenAPI docs. |
| `common` | Shared exception types + a single `GlobalExceptionHandler`, so every module fails the same way (structured `ApiError` JSON) instead of each controller inventing its own error shape. |

## 3. Data flow implemented in Phase 1

```
Client ──JWT──▶ POST /api/alerts ──▶ AlertIngestionService
                                          │
                                          ├─ validate alert belongs to caller's org/project/env/service
                                          ├─▶ Alert (Postgres)
                                          ├─▶ Kafka: alert.received
                                          ├─▶ IncidentService.createFromAlert()
                                          │        ├─▶ Incident (Postgres), status = NEW
                                          │        ├─▶ IncidentEvent x2 (timeline: created, alert linked)
                                          │        ├─▶ IncidentAlertLink (Postgres)
                                          │        └─▶ Kafka: incident.created
                                          └─▶ Alert.status = LINKED (Postgres)

                          PlatformEventListener (Kafka consumer)
                                          │
                                          ▼
                                    AuditLog (Postgres)
```

**Why alert→incident is naive 1:1 in Phase 1, and why that's not "fake":** the spec
(Section 8) requires real correlation later — one underlying failure should not
create hundreds of incidents. Building that now, before there's a second alert
source or a service dependency graph to correlate against, would mean writing
correlation logic with nothing real to correlate. Instead, Phase 1 wires the *entire
pipe* (HTTP → DB → state machine → Kafka → audit) end-to-end with the simplest rule
that is still real, honestly labeled (`correlation_reason = "naive_one_to_one"`), and
fully replaceable in Phase 6 without touching the rest of the pipe.

## 4. Incident state machine

```
DETECTED → NEW → INVESTIGATING ──────────────┬─▶ AWAITING_APPROVAL ─▶ REMEDIATING ─┐
                     │                        └─▶ MITIGATING ───────────────────────┤
                     │                                                              ▼
                     │                                                         VERIFYING
                     │                                                        /          \
                     │                                                 RESOLVED       INVESTIGATING (retry)
                     │                                                     │
                     │                                                  CLOSED
                     └──────────────────────────▶ ESCALATED ─▶ INVESTIGATING / CLOSED
```

- `DETECTED` is reserved for a future pre-incident "signal observed, correlation in
  progress" state (Phase 6). Phase 1 incidents are created directly at `NEW`, since a
  naive 1:1 mapping has nothing to correlate before creating the incident.
- Every transition is validated by `IncidentStateMachine.validateTransition()`. An
  illegal transition throws `DomainException` → HTTP 409, never a silent no-op.
- `CLOSED` is terminal — no transitions out.

## 5. Multi-tenancy and auth model

- `Organization` is the tenant boundary. A JWT's `org` claim is the *only* source of
  truth for which organization a request acts on — no endpoint accepts a
  client-supplied `organizationId`.
- Roles: `ADMIN`, `SRE`, `ENGINEER`, `VIEWER` (Section 19 of the spec). Phase 1 uses
  them for coarse method-level authorization (`@PreAuthorize`) — e.g. only
  ADMIN/SRE can create projects; VIEWER can read everything but change nothing.
- Registration currently only supports "create a brand-new organization + its first
  ADMIN user." Inviting additional users to an existing organization needs an
  invitation flow, which is out of scope for Phase 1.

## 6. Architectural risks / things Phase 2+ must address

- **No integration/E2E test run yet.** I could not run Maven, Docker, or Testcontainers
  in the sandbox I built this in (no JDK compiler, no Docker daemon, no internet).
  The unit tests for the state machine and the two core services are real and
  should compile and pass, but the very first `docker compose up` and `mvn test` run
  on your machine is effectively also the first real build validation of this code.
- **No dedicated machine-to-machine ingestion credential.** `/api/alerts` currently
  authenticates with the same user JWT as everything else. Section 29 (external
  customer integration) will need a separate, project-scoped ingestion credential
  that doesn't require a human login.
- **Optimistic locking (`@Version` on `Incident`) is in place but untested under
  concurrent writes.** Once multiple phases (correlation, remediation, verification)
  can all try to move the same incident, this needs a concurrency test.
- **No rate limiting yet** on `/api/alerts` — Section 20 requires it eventually; not
  needed until there's a real alert source that could flood it.

## 7. Phase 2 — E-commerce reference application

Four independent Spring Boot services, each with its own database, deployed
separately from `opsmind-core` and from each other:

```
Client ──▶ order-service ──REST──▶ product-catalog-service   (fetch authoritative price/name)
                │
                ├──REST──▶ payment-service    ──▶ payments (Postgres)
                │              (real fault injection: force-500, latency)
                │
                └──REST──▶ inventory-service  ──▶ inventory_items (Postgres)
                               │                    (source of truth, optimistic locking)
                               └──▶ Redis (read-through cache for stock lookups only;
                                    real fault injection: redis-failure, db-latency)

order-service also publishes ecommerce.order.{created,confirmed,failed} to the same
Kafka cluster opsmind-core uses (separate topics, same broker).
```

**Why synchronous REST orchestration instead of a Kafka saga:** the spec's full
vision implies event-driven order processing eventually. Phase 2 chose direct REST
calls from `order-service` because it's simpler to reason about and debug for a
first working slice, and because a saga's main benefit — resilience to a
downstream service being temporarily down — isn't yet needed since nothing depends
on eventual consistency in this slice. This is a deliberate, documented simplification, not
a placeholder: `order-service` genuinely calls the other services over HTTP and
genuinely persists the result of each step.

**Why inventory reservation bypasses Redis but stock lookup uses it:** reservation
correctness (never overselling) must never depend on cache freshness, so it goes
straight to Postgres with optimistic locking (`InventoryItem.version`). The stock
*lookup* endpoint (what a product page would show) is a legitimate place for a
cache, and is where the Redis failure-injection fault actually bites.

**Known gaps, explicitly not hidden:**
- No compensating transaction if inventory reservation fails after payment
  succeeds — the order is marked `INVENTORY_FAILED` but the customer is not
  automatically refunded. This needs saga/compensation logic, which is future work.
- No User/Delivery/Notification services, no API Gateway yet.
- No auth on any e-commerce service yet (Phase 17: security hardening).
- No OpenTelemetry/observability instrumentation yet (Phase 4) — failures are
  currently only visible via direct API calls or container logs, not via metrics
  or traces OpsMind could alert on. This is the single biggest gap standing between
  where the platform is now and the "real alert triggers automatic incident"
  loop that is the actual point of OpsMind — Phase 4 should be prioritized next.
