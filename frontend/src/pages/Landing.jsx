import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { checkAllHealth } from '../api/health';
import { Button, Card, StatusDot } from '../components/ui';
import '../landing.css';

const GITHUB_URL = 'https://github.com/9921004167/opsmind';

// Only faults the backend really supports today (see UPGRADE_PLAN.md section 3).
const FAULTS = [
  { svc: 'Payment', what: 'Forced HTTP 500s, injected latency' },
  { svc: 'Inventory', what: 'Redis failure, database latency' },
  { svc: 'Order', what: 'Database latency' },
];

const WORKFLOW = [
  ['Detect', 'Prometheus rules fire; Alertmanager forwards to OpsMind.'],
  ['Correlate', 'Alerts become a deduplicated incident with a timeline.'],
  ['Investigate', 'Metrics, traces and similar past incidents feed an AI-assisted RCA.'],
  ['Remediate', 'Recommendations come from a closed runbook catalog, gated by approval.'],
  ['Verify', 'Recovery is confirmed against Prometheus, not assumed.'],
];

const STACK = ['Java 21', 'Spring Boot 3', 'PostgreSQL + pgvector', 'Kafka', 'Redis', 'Prometheus', 'Alertmanager', 'Grafana Tempo', 'OpenTelemetry', 'React + Vite', 'Docker', 'Kubernetes'];

const NODES = [
  'Frontend (nginx)', 'OpsMind Core', 'Order', 'Product Catalog', 'Payment', 'Inventory',
  'PostgreSQL', 'Redis', 'Kafka',
];

function LiveHealth() {
  const [rows, setRows] = useState(null);
  useEffect(() => {
    let alive = true;
    checkAllHealth().then((r) => alive && setRows(r)).catch(() => alive && setRows([]));
    return () => { alive = false; };
  }, []);

  if (rows === null) return <p className="om-muted">Checking live service health…</p>;
  const reachable = rows.filter((r) => r.status !== 'UNREACHABLE');
  if (rows.length === 0 || reachable.length === 0) {
    return <p className="om-muted">Live health unavailable from this deployment — no figures shown.</p>;
  }
  return (
    <ul className="om-health" aria-label="Live service health">
      {rows.map((r) => (
        <li key={r.key}>
          <StatusDot state={r.status === 'UP' ? 'up' : r.status === 'UNREACHABLE' ? 'unknown' : 'down'} />
          {r.label}: {r.status}
        </li>
      ))}
    </ul>
  );
}

export default function Landing() {
  return (
    <div className="om-landing">
      <header className="om-nav">
        <span className="om-brand">OpsMind</span>
        <nav>
          <a href="#architecture">Architecture</a>
          <a href="#fault-injection">Fault injection</a>
          <a href="#workflow">Workflow</a>
          <a href="#stack">Stack</a>
          <Link to="/login">Sign in</Link>
        </nav>
      </header>

      <section className="om-hero">
        <h1>Detect. Investigate. Recover.</h1>
        <p>
          OpsMind turns real production failures into incidents, investigates them with metrics,
          traces and incident history, and recommends safe, approval-gated remediation, then
          verifies recovery.
        </p>
        <div className="om-cta">
          <Button as="a" href="#architecture">Explore Platform</Button>
          <Button as={Link} to="/demo" variant="secondary">Launch Demo</Button>
        </div>
        <div className="om-live"><span className="om-label">Live status</span><LiveHealth /></div>
      </section>

      <section id="architecture" className="om-section">
        <h2>Architecture</h2>
        <p className="om-muted">Six application services plus PostgreSQL, Redis and Kafka. An interactive explorer is planned.</p>
        <div className="om-grid">{NODES.map((n) => <Card key={n}>{n}</Card>)}</div>
      </section>

      <section id="fault-injection" className="om-section">
        <h2>Fault injection</h2>
        <p className="om-muted">Real, controlled failures in the reference e-commerce system, used to exercise detection and recovery.</p>
        <div className="om-grid">
          {FAULTS.map((f) => <Card key={f.svc} title={f.svc}>{f.what}</Card>)}
        </div>
        <p className="om-muted">Running experiments requires an authorized sign-in.</p>
      </section>

      <section id="workflow" className="om-section">
        <h2>Incident workflow</h2>
        <ol className="om-steps">
          {WORKFLOW.map(([t, d]) => <li key={t}><strong>{t}</strong><span>{d}</span></li>)}
        </ol>
      </section>

      <section id="stack" className="om-section">
        <h2>Technology</h2>
        <ul className="om-chips">{STACK.map((s) => <li key={s}>{s}</li>)}</ul>
      </section>

      <footer className="om-footer">
        <span>OpsMind — a portfolio incident-management project.</span>
        <a href={GITHUB_URL} target="_blank" rel="noreferrer">GitHub</a>
      </footer>
    </div>
  );
}
