# OpsMind Core — API & Event Contracts (Phase 1)

All endpoints are versionless (`/api/...`) for now — no external consumer exists yet
to force a versioning scheme (Section 29 is later work).

## Auth

### `POST /api/auth/register` — create a new Organization + its first ADMIN user
No auth required.
```json
// Request
{
  "organizationName": "Acme Corp",
  "organizationSlug": "acme-corp",
  "fullName": "Ada Admin",
  "email": "ada@acme.test",
  "password": "at-least-8-characters"
}
// Response 201
{ "accessToken": "...", "expiresInSeconds": 3600, "userId": "...", "organizationId": "...", "role": "ADMIN" }
```

### `POST /api/auth/login`
```json
// Request
{ "email": "ada@acme.test", "password": "..." }
// Response 200: same shape as register
```

All endpoints below require `Authorization: Bearer <accessToken>`.

## Tenant

| Method & Path | Roles | Purpose |
|---|---|---|
| `POST /api/projects` | ADMIN, SRE | Create a project in the caller's organization |
| `GET /api/projects` | any | List projects in the caller's organization |
| `POST /api/projects/{projectId}/environments` | ADMIN, SRE | Create a LOCAL/STAGING/PRODUCTION environment |
| `GET /api/projects/{projectId}/environments` | any | List environments |
| `POST /api/projects/{projectId}/services` | ADMIN, SRE | Register a monitored service |
| `GET /api/projects/{projectId}/services` | any | List monitored services |

`CreateProjectRequest`: `{ "name": string, "slug": "^[a-z0-9-]+$" }`
`CreateEnvironmentRequest`: `{ "type": "LOCAL" | "STAGING" | "PRODUCTION" }`
`CreateServiceRequest`: `{ "name": string, "slug": "^[a-z0-9-]+$", "description": string? }`

## Alerts

### `POST /api/alerts` — generic alert ingestion (Section 6)
```json
// Request
{
  "projectId": "uuid",
  "environmentId": "uuid",
  "serviceId": "uuid | null",
  "source": "prometheus",
  "alertType": "high_error_rate",
  "severity": "LOW | MEDIUM | HIGH | CRITICAL",
  "title": "Payment Service Elevated 5xx Rate",
  "description": "string?",
  "metricName": "string?",
  "thresholdValue": 10,
  "currentValue": 23.4,
  "timestamp": "2026-09-12T10:02:11Z | omit for now()",
  "metadata": { "any": "provider-specific extra fields, not yet used" }
}
// Response 201
{
  "id": "uuid", "projectId": "uuid", "environmentId": "uuid", "serviceId": "uuid|null",
  "source": "prometheus", "alertType": "high_error_rate", "severity": "HIGH", "status": "LINKED",
  "title": "...", "description": "...", "metricName": "...", "thresholdValue": 10, "currentValue": 23.4,
  "receivedAt": "...", "createdIncidentId": "uuid", "createdIncidentNumber": "INC-000042"
}
```
Every valid alert currently creates exactly one new incident (naive 1:1 — see
ARCHITECTURE.md §3). `projectId`/`environmentId`/`serviceId` must belong to the
caller's own organization or the request is rejected with 400.

### `GET /api/alerts` — list alerts for the caller's organization, most recent first

## Incidents

| Method & Path | Roles | Purpose |
|---|---|---|
| `GET /api/incidents` | any | List incidents for the caller's org, newest first |
| `GET /api/incidents/{id}` | any | Get one incident (404 if it belongs to another org) |
| `GET /api/incidents/{id}/timeline` | any | Chronological `IncidentEvent` list |
| `PATCH /api/incidents/{id}/status` | ADMIN, SRE, ENGINEER | Move the incident through the state machine |

`IncidentStatusUpdateRequest`: `{ "status": "<IncidentStatus>", "note": "string?" }`

`IncidentResponse` includes `allowedNextStatuses`, so a client always knows the legal
next moves without hardcoding the state machine itself.

An illegal transition (e.g. `NEW → RESOLVED`) returns:
```json
// 409
{ "status": 409, "error": "DOMAIN_RULE_VIOLATION",
  "message": "Illegal incident state transition: NEW -> RESOLVED. Allowed from NEW: [INVESTIGATING, ESCALATED]" }
```

## Kafka events

Envelope every event is wrapped in:
```json
{ "eventId": "uuid", "eventType": "string", "occurredAt": "iso-instant",
  "organizationId": "uuid", "payload": { "...": "event-specific" } }
```

| Topic | Status | Payload |
|---|---|---|
| `alert.received` | **Produced (Phase 1)** | `AlertReceivedEvent`: alertId, projectId, environmentId, serviceId, source, alertType, severity, title, receivedAt |
| `incident.created` | **Produced (Phase 1)** | `IncidentCreatedEvent`: incidentId, incidentNumber, projectId, environmentId, primaryServiceId, title, severity, sourceAlertId, createdAt |
| `incident.status.changed` | **Produced (Phase 1)** | `IncidentStatusChangedEvent`: incidentId, incidentNumber, previousStatus, newStatus, changedAt |
| `incident.investigation.started` | Reserved (Phase 7) | not yet defined |
| `incident.investigation.completed` | Reserved (Phase 7) | not yet defined |
| `remediation.requested` | Reserved (Phase 10-11) | not yet defined |
| `remediation.approved` | Reserved (Phase 11) | not yet defined |
| `remediation.executed` | Reserved (Phase 10) | not yet defined |
| `remediation.failed` | Reserved (Phase 10) | not yet defined |
| `incident.verification.started` | Reserved (Phase 12) | not yet defined |
| `incident.resolved` | Reserved (Phase 12) | not yet defined |

"Reserved" topics are declared as constants in `KafkaTopics.java` for naming
consistency but nothing produces or consumes them yet — treat any reference to them
elsewhere as a contract for later phases, not a working feature today.
