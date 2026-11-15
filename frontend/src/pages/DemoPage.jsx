import { useState } from 'react';
import { Link } from 'react-router-dom';
import '../landing.css';
import '../shell.css';

// Every step below is a static, SIMULATED walkthrough. Nothing here calls the backend.
const STEPS = [
  ['Platform overview', 'OpsMind turns real production failures into incidents: Prometheus detects, Alertmanager forwards, OpsMind correlates, investigates, recommends a runbook, gates it behind approval, and verifies recovery.'],
  ['Architecture', 'Four e-commerce services (Catalog, Order, Payment, Inventory) are monitored by Prometheus; OpsMind Core ingests alerts and owns the incident lifecycle. Open the Architecture page after signing in for the interactive diagram.'],
  ['Sample incident investigation', 'Example flow (illustrative, not a real incident): a payment-service alert opens an incident → investigation collects metric and trace evidence → an AI-assisted RCA with a stated confidence is attached → a remediation recommendation is matched to a known runbook. Where evidence is missing, OpsMind says so instead of guessing.'],
  ['Safe fault injection', 'In the real product, an authorized engineer picks an allow-listed fault, a duration up to 120 s (30 s default), confirms, and the fault auto-expires. An emergency stop is always available. The demo never injects anything.'],
  ['CI/CD overview', 'Services ship as Docker images with Kubernetes manifests in the repository. The Jenkins pipeline lives outside this repository snapshot and is not described here.'],
  ['Monitoring and recovery', 'After remediation, OpsMind verifies recovery against Prometheus rather than assuming success, then stores the outcome as incident memory.'],
];

export default function DemoPage() {
  const [i, setI] = useState(0);
  return (
    <div className="om-landing">
      <header className="om-nav"><Link to="/" className="om-brand">OpsMind</Link><nav><Link to="/login">Sign in</Link></nav></header>
      <main className="om-section">
        <div className="banner" role="note"><strong>SIMULATED DEMO</strong> — static sample content, not live system data. No privileged operations are possible here.</div>
        <h1>Guided demo</h1>
        <ol className="om-steps" style={{ counterReset: 'none' }}>
          {STEPS.map(([t], n) => (
            <li key={t} style={{ gridTemplateColumns: '1fr', cursor: 'pointer', opacity: n === i ? 1 : 0.55 }} onClick={() => setI(n)}>
              <strong>{n + 1}. {t}</strong>
            </li>
          ))}
        </ol>
        <div className="om-card" aria-live="polite"><h3>{STEPS[i][0]}</h3><p>{STEPS[i][1]}</p></div>
        <div className="om-cta" style={{ marginTop: 16 }}>
          <button className="om-btn om-btn-secondary" disabled={i === 0} onClick={() => setI(i - 1)}>Back</button>
          <button className="om-btn om-btn-primary" disabled={i === STEPS.length - 1} onClick={() => setI(i + 1)}>Next</button>
        </div>
      </main>
    </div>
  );
}
