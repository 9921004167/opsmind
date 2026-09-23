# OpsMind Frontend — setup

## 1. Place this folder in your project root

Copy the entire `opsmind-frontend` folder into your project so it sits alongside
`opsmind-core` and `ecommerce`:

```
C:\Users\KIRAN\Downloads\opsmind-phase1-phase2\opsmind\
├── opsmind-core\
├── ecommerce\
├── frontend\          <- rename this folder to "frontend" (or update the compose
│                          path below to match whatever you name it)
├── docker-compose.yml
└── .env
```

## 2. Add the service to docker-compose.yml

```yaml
  frontend:
    build:
      context: ./frontend
    restart: unless-stopped
    ports:
      - "3000:80"
    depends_on:
      - opsmind-core
```

No environment variables needed — everything is proxied through nginx at build/run
time using the service names already in your compose network (`opsmind-core`,
`order-service`, `payment-service`, `inventory-service`, `product-catalog-service`).
If any of your actual service names in `docker-compose.yml` differ from these,
update the `proxy_pass` targets in `frontend/nginx.conf` to match.

## 3. Build and run

```
docker compose up --build frontend
```

Then open **http://localhost:3000** in your browser.

## 4. Log in

Use the same organization/user you've been using all along (from Phase 1's
register/login flow) — this is the exact same JWT-based auth, just from a browser
instead of curl.

## What works out of the box

- **Login / Register** — real calls to `/api/auth/login` and `/api/auth/register`
- **Dashboard** — service count, active incident count, recent alerts, recent
  incidents, and a live System Health panel checking each e-commerce service's
  real `/actuator/health` through nginx
- **Services page** — lists your registered `MonitoredService`s and their live
  health status (matched by slug — see `HEALTH_KEY_BY_SLUG` in
  `src/pages/ServicesPage.jsx` if your slugs differ from `payment-service`,
  `order-service`, `inventory-service`, `product-catalog-service`)
- **Incidents list** — filterable by All / Active / Resolved
- **Incident detail** with 5 tabs:
  - **Overview** — summary + a working status-transition control that only
    offers the incident's actual `allowedNextStatuses` (never lets you attempt
    an illegal transition from the UI)
  - **Timeline** — the real `IncidentEvent` audit trail
  - **Investigation & RCA** — trigger a new investigation, retry a failed one,
    and see real evidence + the Gemini-generated RCA (root cause, confidence,
    reasoning, recommended actions) for each one
  - **Historical Incidents** — see the honest note below
  - **Remediation** — see the honest note below

## Two tabs that are honestly incomplete, not broken

**Historical Incidents tab**: your Phase 7 build wires historical-incident
retrieval directly into the Gemini RCA prompt inside `InvestigationService` — it
was reported to me as "wired into live InvestigationService," not as a separate
queryable REST endpoint. This tab checks for an optional `historicalIncidents`
array on the investigation response and renders it if present; otherwise it shows
a note pointing you to the RCA's reasoning text, which is where any historical
reference currently surfaces. **If you do have (or add) a dedicated retrieval
endpoint, tell me its shape and I'll wire this tab to it properly** — this was
built defensively because I don't have confirmed visibility into that endpoint.

**Remediation tab**: Phase 8 hasn't been built yet (we were mid-design-review on
it). This tab calls the planned endpoints
(`/api/incidents/{id}/remediation-recommendations` etc.) and gracefully shows "not
deployed yet" on a 404, rather than fabricating fake remediation data. Once Phase 8
ships with those exact endpoint paths, this tab will start working with **zero
frontend changes** — that's why the API shape in `src/api/remediation.js` was
written to match the plan I presented earlier.

## Known limitations

- No pagination anywhere (fine for a demo/reference volume of data; would need it
  for real production incident volume)
- Assumes a single project (uses `projects[0]` from `/api/projects`) — if you have
  multiple projects registered, only the first one's services/environments show
- No dark/light theme toggle, no mobile-responsive layout — desktop-oriented for now
- This code has never been run — same standing caveat as every backend phase in
  this project. `npm install && npm run build` inside the Docker build step is the
  first real compile it will ever go through.
