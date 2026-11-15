export default function DocsPage() {
  return (
    <div>
      <h1>Documentation</h1>
      <section className="panel"><h2>Start</h2>
        <p><code>copy .env.example .env</code>, set secrets, then <code>docker compose up -d --build</code>. Open <code>http://localhost:3000</code>.</p>
      </section>
      <section className="panel"><h2>Enable the Fault Lab</h2>
        <p>Off by default. Set <code>OPSMIND_FAULTLAB_ENABLED=true</code> and <code>OPSMIND_FAULTLAB_ALLOWED_ORGS=&lt;organization UUID&gt;</code> (shown under Settings) in <code>.env</code>, then restart opsmind-core.</p>
      </section>
      <section className="panel"><h2>Roles</h2>
        <p>ADMIN, SRE, ENGINEER may change incident status and run experiments (if allow-listed). Approve/reject/execute remediation: ADMIN, SRE. Audit events: ADMIN, SRE. VIEWER is read-only.</p>
      </section>
      <section className="panel"><h2>API</h2>
        <table><tbody>
          <tr><td><code>/api/incidents</code></td><td>list, detail, timeline, status</td></tr>
          <tr><td><code>/api/incidents/:id/investigations</code></td><td>AI investigation and RCA</td></tr>
          <tr><td><code>/api/incidents/:id/remediation-recommendations</code></td><td>recommend, approve, reject, execute</td></tr>
          <tr><td><code>/api/fault-lab/*</code></td><td>capabilities, experiments, stop, emergency-stop</td></tr>
          <tr><td><code>/api/monitoring/summary</code></td><td>Prometheus snapshot</td></tr>
          <tr><td><code>/api/audit-events</code></td><td>organization audit trail</td></tr>
        </tbody></table>
        <p className="panel-hint">Full contracts: API_CONTRACTS.md in the repository; Swagger at /swagger-ui on opsmind-core.</p>
      </section>
    </div>
  );
}
