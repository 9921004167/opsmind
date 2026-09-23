# OpsMind

## AI-Native Autonomous Incident Management & Remediation Platform

> **From Detection to Recovery — Autonomously, Safely, and Intelligently.**

OpsMind is an AI-powered incident management platform designed to detect real production failures, automatically create and correlate incidents, investigate them using observability data, learn from historical incidents, recommend safe remediation, execute controlled actions under policy, verify recovery, and preserve the outcome as organizational memory.

## The Vision

```text
Real Production Failure
        ↓
Prometheus Detection
        ↓
Alertmanager
        ↓
Automatic Incident Creation
        ↓
Correlation & Deduplication
        ↓
AI Investigation
        ↓
Metrics + Traces + Historical Memory
        ↓
Root Cause Analysis
        ↓
AI Remediation Recommendation
        ↓
Risk & Approval Policy
        ↓
Controlled Execution
        ↓
Prometheus Verification
        ↓
Recovery
        ↓
Resolved Incident → Organizational Memory
```

OpsMind is designed as an **AI-native SRE platform** where detection, investigation, remediation, verification, and learning form one continuous incident lifecycle.

## What OpsMind Does

### Incident Detection

Integrates with Prometheus and Alertmanager so real application failures can automatically become incidents.

Examples:
- High HTTP 5xx rate
- Service unavailable
- High latency
- Dependency failures

### Incident Correlation & Deduplication

Multiple alerts caused by the same underlying failure can be correlated instead of creating unnecessary duplicate incidents.

### AI Investigation

Investigates available evidence including:
- Prometheus metrics
- Distributed traces through Tempo
- Incident metadata
- Root-cause signals
- Historical incidents

The investigation produces an AI-assisted RCA with confidence information.

### Incident Memory

Resolved incidents are converted into organizational memory. New incidents can retrieve similar historical incidents using vector similarity and service/context-aware reranking.

### AI-Assisted Remediation

OpsMind uses a controlled runbook catalog rather than allowing an AI model to invent arbitrary executable commands.

```text
Incident + RCA
      ↓
AI Remediation Advisor
      ↓
Known Runbook
      ↓
Risk Assessment
      ↓
Approval Policy
      ↓
Controlled Executor
```

### Safety Controls

AI recommendations cannot directly execute arbitrary shell, SSH, Kubernetes, or cloud commands. Only explicitly registered executors can perform automated actions.

Unknown or unmatched remediation recommendations remain **MANUAL_ONLY** and are treated as high risk.

LOW-risk automatic approval is available as a policy option and is **OFF by default**.

### Verification

After remediation, OpsMind verifies recovery using real Prometheus evidence instead of assuming execution success means the incident is fixed.

Example:

```text
5xx rate       47% → 0.3%
p95 latency    4.8s → 180ms
Result         RECOVERED
```

### Auditability

Important incident, approval, execution, and remediation transitions are recorded for operational traceability.

## End-to-End Production Scenario

Imagine the Payment Service in production begins failing:

```text
5xx rate:       2% → 47%
p95 latency:    180ms → 4.8s
```

Prometheus detects the threshold breach and Alertmanager forwards the alert to OpsMind. OpsMind automatically creates a critical incident, correlates related alerts, and starts an AI investigation.

The investigation collects metrics and traces and searches historical incident memory. If a similar historical incident exists, that context can help the AI produce a better RCA.

The remediation advisor then selects only from the predefined runbook catalog. If the selected runbook is LOW risk, the configured approval policy determines whether a human approval is required or automatic approval is allowed.

The registered executor performs the controlled action. OpsMind then verifies the service with Prometheus. If the metrics recover, the incident moves to RESOLVED and the outcome becomes eligible for organizational memory.

## Architecture

```text
                         ┌──────────────────────┐
                         │   E-commerce System   │
                         │ Catalog / Order /     │
                         │ Payment / Inventory  │
                         └──────────┬───────────┘
                                    │
                              Metrics / Traces
                                    │
                 ┌──────────────────┴──────────────────┐
                 │                                     │
          ┌──────▼──────┐                       ┌──────▼──────┐
          │ Prometheus  │                       │    Tempo    │
          └──────┬──────┘                       └──────┬──────┘
                 │                                     │
                 └──────────────────┬──────────────────┘
                                    │
                              ┌─────▼─────┐
                              │Alertmanager│
                              └─────┬─────┘
                                    │
                                    ▼
                         ┌────────────────────┐
                         │      OpsMind       │
                         │                    │
                         │ Alert Ingestion    │
                         │ Incident Engine    │
                         │ Correlation        │
                         │ AI Investigation   │
                         │ Incident Memory    │
                         │ Remediation        │
                         │ Verification       │
                         │ Audit              │
                         └───────┬────────────┘
                                 │
                   ┌─────────────┴─────────────┐
                   │                           │
             ┌─────▼─────┐               ┌─────▼─────┐
             │PostgreSQL │               │   Kafka   │
             │ + pgvector│               │           │
             └───────────┘               └───────────┘
```

## Project Phases

| Phase | Capability |
|---|---|
| Phase 1 | Multi-tenant incident-management foundation |
| Phase 2 | E-commerce reference application |
| Phase 3 | Metrics, Prometheus, Grafana, Tempo & OpenTelemetry |
| Phase 4 | Automatic Prometheus → Alertmanager → OpsMind alerting |
| Phase 5 | Alert correlation & deduplication |
| Phase 6 | AI-powered incident investigation & RCA |
| Phase 7 | Historical incident memory with pgvector |
| Phase 8 | AI-assisted remediation, approval, execution & verification |
| Frontend | OpsMind web interface |

## E-commerce Reference System

The monitored application contains:

- **Product Catalog Service** — `8090`
- **Payment Service** — `8091`
- **Inventory Service** — `8092`
- **Order Service** — `8093`

The services communicate through real REST and Kafka flows and provide controlled fault-injection endpoints for realistic incident demonstrations.

```text
Customer
   ↓
Order Service
   ├── Product Catalog
   ├── Payment
   └── Inventory
```

## Technology Stack

### Backend
- Java 21
- Spring Boot 3
- Spring Security
- JWT
- Maven
- PostgreSQL
- Flyway

### Messaging
- Apache Kafka
- Kafka UI

### Observability
- Prometheus
- Grafana
- OpenTelemetry
- Grafana Tempo
- Micrometer

### AI
- Google Gemini
- AI investigation
- AI-assisted RCA
- Gemini embeddings
- pgvector historical incident memory

### Remediation
- Controlled runbook catalog
- Risk classification
- Approval workflow
- Registered executors
- Remediation verification
- Audit trail

### Frontend & Infrastructure
- React / Vite
- Nginx
- Docker
- Docker Compose

## Security & Safety Principles

### AI cannot execute arbitrary commands

The AI can recommend a known runbook, but executable behavior comes from registered application code.

### Closed runbook catalog

The remediation advisor selects from predefined runbooks rather than generating executable infrastructure commands.

### Risk-based execution

```text
LOW
 ├── Manual approval
 └── Optional automatic approval

MEDIUM
 └── Human approval required

HIGH
 └── Human approval / MANUAL_ONLY
```

### Tenant isolation

Incident and historical-memory access is organization-scoped.

### Auditing

Remediation decisions and important state transitions are persisted for traceability.

## Running the Project

### Prerequisites

- Docker Desktop
- Docker Compose
- JDK 21 for local development
- Maven for local builds

### Configuration

```cmd
copy .env.example .env
```

Configure the required secrets and service settings in `.env`.

**Never commit `.env` or API keys to GitHub.**

### Start the platform

```cmd
docker compose up -d --build
```

Check services:

```cmd
docker compose ps
```

## Real Fault Demonstration

A controlled Payment Service failure can be introduced with the configured fault-admin token:

```cmd
curl -X POST http://localhost:8091/faults/payment/enable-500 ^
  -H "X-Fault-Admin-Token: YOUR_FAULT_ADMIN_TOKEN"
```

Then place a real order through the Order Service. The failure can flow through:

```text
Payment failure
      ↓
Prometheus
      ↓
Alertmanager
      ↓
OpsMind
      ↓
Incident
      ↓
AI Investigation
      ↓
RCA
      ↓
Remediation Recommendation
      ↓
Approval Policy
      ↓
Controlled Executor
      ↓
Verification
      ↓
Recovery
```

Disable the controlled fault:

```cmd
curl -X POST http://localhost:8091/faults/payment/disable ^
  -H "X-Fault-Admin-Token: YOUR_FAULT_ADMIN_TOKEN"
```

## Repository Structure

```text
opsmind/
├── docker-compose.yml
├── .env.example
├── README.md
├── ARCHITECTURE.md
├── API_CONTRACTS.md
├── opsmind-core/
├── ecommerce/
│   ├── product-catalog-service/
│   ├── payment-service/
│   ├── inventory-service/
│   └── order-service/
├── monitoring/
├── prometheus/
├── alertmanager/
└── frontend/
```

## Current Platform Capability

OpsMind currently demonstrates the core architecture for:

**Detection → Investigation → Historical Learning → Safe Remediation → Verification → Recovery**

The platform is deliberately conservative around autonomous execution. Where reliable evidence or a registered safe executor is unavailable, OpsMind does not fabricate an action and keeps the recommendation manual.

## Future Roadmap

- Real application-log evidence collection
- Change/deployment evidence
- More production remediation executors
- Kubernetes remediation
- Cloud infrastructure remediation
- Automated rollback
- Advanced dependency analysis
- Improved incident similarity and feedback loops
- SLO-aware remediation policies
- Expanded frontend dashboards
- Multi-region operational intelligence

## Project Motto

> **From Detection to Recovery — Autonomously, Safely, and Intelligently.**

### Ultimate Goal

> **A real production problem happens → OpsMind detects it, understands it, learns from previous incidents, recommends a safe fix, executes it under controlled policy, verifies recovery, and remembers the outcome.**
