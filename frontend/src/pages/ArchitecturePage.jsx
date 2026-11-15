import { useState } from 'react';

// Derived from docker-compose.yml, prometheus.yml, alertmanager.yml and the service clients in the repo.
const NODES = {
  frontend: { x: 70, y: 40, label: 'Frontend (nginx)', info: 'React SPA served by nginx. Proxies /api to OpsMind Core and /health-proxy to each e-commerce service’s health endpoint.' },
  core: { x: 330, y: 40, label: 'OpsMind Core', info: 'Spring Boot: auth/RBAC, alerts, incidents, AI investigation, memory, remediation, Fault Lab.' },
  prom: { x: 590, y: 40, label: 'Prometheus + Alertmanager', info: 'Scrapes the four services; rules fire alerts that Alertmanager posts to the Core webhook.' },
  order: { x: 70, y: 190, label: 'Order', info: 'Orchestrates orders via REST to Catalog, Payment and Inventory; publishes order events to Kafka.' },
  catalog: { x: 250, y: 190, label: 'Product Catalog', info: 'Authoritative product names and prices. No fault endpoints.' },
  payment: { x: 430, y: 190, label: 'Payment', info: 'Charges. Fault endpoints: forced 500, latency.' },
  inventory: { x: 610, y: 190, label: 'Inventory', info: 'Stock with Redis read-through cache. Fault endpoints: Redis failure, DB latency.' },
  kafka: { x: 70, y: 340, label: 'Kafka', info: 'Event backbone for Core events and order events.' },
  pg: { x: 330, y: 340, label: 'PostgreSQL', info: 'Core DB with pgvector; separate databases per e-commerce service.' },
  redis: { x: 610, y: 340, label: 'Redis', info: 'Inventory stock-lookup cache.' },
};
const EDGES = [
  ['frontend', 'core', 'REST /api'], ['prom', 'core', 'alert webhook'], ['core', 'prom', 'metric queries'],
  ['order', 'catalog', 'REST'], ['order', 'payment', 'REST'], ['order', 'inventory', 'REST'],
  ['order', 'kafka', 'events'], ['core', 'kafka', 'events'], ['core', 'pg', 'JDBC'], ['inventory', 'redis', 'cache'],
  ['core', 'payment', 'fault/disable'], ['prom', 'payment', 'scrape'],
];

export default function ArchitecturePage() {
  const [sel, setSel] = useState('core');
  const c = (k) => ({ x: NODES[k].x + 70, y: NODES[k].y + 22 });
  return (
    <div>
      <h1>Architecture</h1>
      <section className="panel">
        <p className="panel-hint">Select a component. Edges are real communication paths found in the repository configuration.</p>
        <div style={{ overflowX: 'auto' }}>
          <svg viewBox="0 0 780 400" className="arch" role="img" aria-label="OpsMind architecture diagram">
            {EDGES.map(([a, b, t], n) => { const p = c(a), q = c(b); return (
              <g key={n}><line x1={p.x} y1={p.y} x2={q.x} y2={q.y} /><text x={(p.x + q.x) / 2} y={(p.y + q.y) / 2 - 4}>{t}</text></g>); })}
            {Object.entries(NODES).map(([k, n]) => (
              <g key={k} className={`node ${sel === k ? 'sel' : ''}`} tabIndex="0" role="button" aria-label={n.label} onClick={() => setSel(k)} onKeyDown={(e) => e.key === 'Enter' && setSel(k)}>
                <rect x={n.x} y={n.y} width="140" height="44" rx="8" /><text x={n.x + 70} y={n.y + 26} textAnchor="middle">{n.label}</text>
              </g>
            ))}
          </svg>
        </div>
      </section>
      <section className="panel"><h2>{NODES[sel].label}</h2><p>{NODES[sel].info}</p></section>
    </div>
  );
}
