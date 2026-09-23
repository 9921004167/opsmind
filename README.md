# OpsMind — Phase 1: Foundation

This is Phase 1 of the OpsMind platform: an AI-native incident detection, investigation,
remediation, and response platform for distributed applications.

Phase 1 delivers the **foundation** everything else builds on: multi-tenant model,
authentication, the generic alert ingestion API, the incident domain model and state
machine, and the Kafka event backbone. It intentionally does **not** include the
e-commerce reference application, AI investigation, RAG, or the remediation engine —
those are later phases (see `ARCHITECTURE.md`).

## What actually works in Phase 1

- Register an organization + its first (ADMIN) user, and log in, receiving a JWT.
- Create Projects, Environments (LOCAL/STAGING/PRODUCTION), and Monitored Services
  under an organization, tenant-isolated by JWT claims (not client-supplied IDs).
- POST a generic alert to `/api/alerts`. This validates it belongs to your org's
  project/environment/service, persists it, publishes an `alert.received` Kafka
  event, and creates exactly one Incident from it (naive 1:1 mapping — real
  correlation is Phase 6).
- Every incident has an enforced state machine (`IncidentStateMachine`) and a
  timestamped timeline (`IncidentEvent`) — illegal transitions are rejected, not
  silently allowed.
- A Kafka consumer (`PlatformEventListener`) actually consumes the events this same
  service publishes and writes them to `audit_logs` — proving the producer → broker →
  consumer loop is real, not just "send() didn't throw."
- All schema is owned by Flyway migrations (`V1`–`V3`) — no Hibernate auto-DDL.

## Important limitation — I could not compile or run this in my sandbox

I do not have Maven or a JDK compiler (`javac`) available in the environment I built
this in, and it has no internet access (so even installing them wasn't possible). I
wrote this code carefully and reviewed it by hand, but **it has not been
compiled or executed anywhere**. Please treat "first `mvn clean package` / first
`docker compose up`" as part of validating Phase 1, and tell me about any compile
errors or bugs you hit — I'd expect a small number of issues in a codebase this size
that's never been built.

## Prerequisites (on your machine)

- Docker + Docker Compose
- JDK 21 (only needed if you want to run `opsmind-core` outside Docker, e.g. from an IDE)
- (Optional) Maven, if not using the included Dockerfile's build stage
- `curl` and `jq`, only needed to run the example commands below as-is

## Running it

```bash
cp .env.example .env
# Edit .env: set OPSMIND_JWT_SECRET to a real random 32+ byte value, e.g.:
# openssl rand -base64 48

docker compose up --build
```

This starts: Postgres, Kafka (KRaft single-node), Redis (reserved for later phases),
Kafka UI (dev-only, http://localhost:8081), and `opsmind-core` (http://localhost:8080).

Flyway runs the migrations automatically on startup. Swagger UI is at
`http://localhost:8080/swagger-ui.html`.

## Trying the end-to-end Phase 1 flow

```bash
# 1. Register an organization + admin user
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{
  "organizationName": "Acme Corp",
  "organizationSlug": "acme-corp",
  "fullName": "Ada Admin",
  "email": "ada@acme.test",
  "password": "correct-horse-battery-staple"
}' | tee /tmp/auth.json

TOKEN=$(jq -r .accessToken /tmp/auth.json)

# 2. Create a project
curl -s -X POST localhost:8080/api/projects -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Shop Platform","slug":"shop-platform"}' | tee /tmp/project.json

PROJECT_ID=$(jq -r .id /tmp/project.json)

# 3. Create a PRODUCTION environment
curl -s -X POST localhost:8080/api/projects/$PROJECT_ID/environments -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"type":"PRODUCTION"}' | tee /tmp/env.json

ENV_ID=$(jq -r .id /tmp/env.json)

# 4. Register a monitored service
curl -s -X POST localhost:8080/api/projects/$PROJECT_ID/services -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Payment Service","slug":"payment-service"}' | tee /tmp/service.json

SERVICE_ID=$(jq -r .id /tmp/service.json)

# 5. Send an alert - this creates an incident automatically
curl -s -X POST localhost:8080/api/alerts -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "projectId": "'"$PROJECT_ID"'",
    "environmentId": "'"$ENV_ID"'",
    "serviceId": "'"$SERVICE_ID"'",
    "source": "prometheus",
    "alertType": "high_error_rate",
    "severity": "HIGH",
    "title": "Payment Service Elevated 5xx Rate",
    "description": "5xx rate above 10% for 2 consecutive minutes",
    "metricName": "http_5xx_rate",
    "thresholdValue": 10,
    "currentValue": 23.4
  }' | tee /tmp/alert.json

# 6. List incidents - the one created above should appear with status NEW
curl -s localhost:8080/api/incidents -H "Authorization: Bearer $TOKEN" | jq .

INCIDENT_ID=$(jq -r .createdIncidentId /tmp/alert.json)

# 7. View its timeline
curl -s localhost:8080/api/incidents/$INCIDENT_ID/timeline -H "Authorization: Bearer $TOKEN" | jq .

# 8. Move it through the state machine
curl -s -X PATCH localhost:8080/api/incidents/$INCIDENT_ID/status -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"status":"INVESTIGATING","note":"looking into it"}' | jq .

# 9. Try an illegal transition (should be rejected with HTTP 409)
curl -s -o /dev/null -w '%{http_code}\n' -X PATCH localhost:8080/api/incidents/$INCIDENT_ID/status \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"status":"RESOLVED"}'
```

You can also open Kafka UI at `http://localhost:8081` to see the real
`alert.received` and `incident.created` messages on the topics, and check
`audit_logs` in Postgres to see the Kafka consumer's audit rows for the same events.

## Repository structure

```
opsmind/
├── docker-compose.yml
├── .env.example
├── ARCHITECTURE.md
├── API_CONTRACTS.md
└── opsmind-core/                  # Phase 1: the only service that exists so far
    ├── pom.xml
    ├── Dockerfile
    └── src/
        ├── main/java/com/opsmind/core/
        │   ├── tenant/            # Organization, Project, Environment, Service, User, Role
        │   ├── auth/              # Registration, login, JWT issuance
        │   ├── alert/             # Generic alert ingestion
        │   ├── incident/          # Incident, timeline, state machine
        │   ├── kafka/             # Topic contract, event envelope, publisher, consumer
        │   ├── audit/             # Audit log
        │   ├── config/            # Security, JWT filter, Kafka topic provisioning, OpenAPI
        │   └── common/            # Shared exceptions, error handling, incident numbering
        └── test/java/com/opsmind/core/
            ├── incident/          # State machine + service unit tests
            └── alert/             # Alert ingestion unit tests
```

## Phase 2 — E-commerce reference application

Four new services, deliberately the minimum vertical slice that produces one real,
failing, observable transaction (full scope in `ARCHITECTURE.md`):

- **product-catalog-service** (`:8090`) — public product browsing, seeded with 5 demo products.
- **payment-service** (`:8091`) — processes charges; has real failure-injection endpoints.
- **inventory-service** (`:8092`) — reserves stock in Postgres, uses Redis as a real read-through cache; has real failure-injection endpoints.
- **order-service** (`:8093`) — orchestrates catalog → payment → inventory via real REST calls; publishes Kafka events on the same cluster as OpsMind.

Each service has its own Postgres database (`catalog_db`, `order_db`, `payment_db`,
`inventory_db`) inside the new `ecommerce-db` container — a separate instance from
OpsMind's own database, since the monitored application must not share infrastructure
with the platform monitoring it.

### Running it

Add the new variables from `.env.example` to your `.env` (`FAULT_ADMIN_TOKEN` is
required — every `/faults/**` endpoint checks it), then:

```bash
docker compose up --build
```

### Seeding inventory (one-time, after first startup)

`product-catalog-service`'s Flyway migration seeds 5 products with UUIDs generated
at insert time, so `inventory-service` can't pre-seed matching stock — you set it
via its real stock-management endpoint:

```bash
# Fetch the seeded products
curl -s http://localhost:8090/api/products | jq .

# For each product id returned above, set some stock:
curl -s -X PUT http://localhost:8092/api/inventory/<PRODUCT_ID>/stock \
  -H "Content-Type: application/json" -d '{"availableQuantity": 100}'
```

### Placing an order end-to-end

```bash
curl -s -X POST http://localhost:8093/api/orders -H "Content-Type: application/json" -d '{
  "customerId": "cust-001",
  "items": [ { "productId": "<PRODUCT_ID>", "quantity": 2 } ]
}' | jq .
```

A successful order shows `"status": "CONFIRMED"`. Check Kafka UI
(`http://localhost:8081`) for `ecommerce.order.created` / `ecommerce.order.confirmed`
messages.

### Triggering real failures

All fault endpoints require `X-Fault-Admin-Token: <your FAULT_ADMIN_TOKEN>`.

```bash
# Make every payment charge fail with a real HTTP 500
curl -s -X POST http://localhost:8091/faults/payment/enable-500 -H "X-Fault-Admin-Token: $FAULT_ADMIN_TOKEN"

# Place an order again - it should come back with status PAYMENT_FAILED
curl -s -X POST http://localhost:8093/api/orders -H "Content-Type: application/json" -d '{"customerId":"cust-001","items":[{"productId":"<PRODUCT_ID>","quantity":1}]}' | jq .

# Turn it off
curl -s -X POST http://localhost:8091/faults/payment/disable -H "X-Fault-Admin-Token: $FAULT_ADMIN_TOKEN"

# Simulate Redis being unreachable for inventory's stock-check endpoint
curl -s -X POST http://localhost:8092/faults/inventory/redis-failure -H "X-Fault-Admin-Token: $FAULT_ADMIN_TOKEN"
curl -s http://localhost:8092/api/inventory/<PRODUCT_ID>   # should now return 503

# For a genuinely real "database unavailable" test (no toggle needed):
docker compose stop ecommerce-db
curl -s -X POST http://localhost:8093/api/orders ...        # should fail for real
docker compose start ecommerce-db
```

### Known gaps in this slice (see ARCHITECTURE.md for full list)

- No automatic refund if inventory reservation fails after a successful charge (no saga/compensation logic yet).
- No User/Delivery/Notification services, no API Gateway.
- No OpenTelemetry/observability wiring yet — that's Phase 4. Right now the only way to "see" a failure is to call the API directly or watch container logs.
- These services have no auth at all (Phase 2 scope) — that's Phase 17 (security hardening).


