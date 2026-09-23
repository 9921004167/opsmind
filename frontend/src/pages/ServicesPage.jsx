import { useEffect, useState } from 'react';
import { listProjects, listServices, listEnvironments } from '../api/tenant';
import { checkAllHealth } from '../api/health';
import Loading from '../components/Loading';
import ErrorBanner from '../components/ErrorBanner';
import StatusBadge from '../components/StatusBadge';

// Maps OpsMind MonitoredService slugs (registered via the API, see README Phase 4)
// to the live health-proxy key. If your service slugs differ from these, the
// health column will just show "—" for that row rather than guessing wrong.
const HEALTH_KEY_BY_SLUG = {
  'order-service': 'order',
  'payment-service': 'payment',
  'inventory-service': 'inventory',
  'product-catalog-service': 'catalog',
};

export default function ServicesPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [services, setServices] = useState([]);
  const [environments, setEnvironments] = useState([]);
  const [health, setHealth] = useState([]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const projects = await listProjects();
        if (projects.length === 0) {
          if (!cancelled) { setServices([]); setLoading(false); }
          return;
        }
        const project = projects[0];
        const [svc, env, healthData] = await Promise.all([
          listServices(project.id), listEnvironments(project.id), checkAllHealth(),
        ]);
        if (cancelled) return;
        setServices(svc);
        setEnvironments(env);
        setHealth(healthData);
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, []);

  if (loading) return <Loading label="Loading services..." />;

  function healthFor(slug) {
    const key = HEALTH_KEY_BY_SLUG[slug];
    return health.find((h) => h.key === key);
  }

  return (
    <div>
      <h1>Services</h1>
      <ErrorBanner message={error} />

      <section className="panel">
        <h2>Environments</h2>
        <div className="chip-row">
          {environments.map((e) => <span key={e.id} className="chip">{e.type}</span>)}
          {environments.length === 0 && <p className="panel-hint">No environments registered yet.</p>}
        </div>
      </section>

      <section className="panel">
        <h2>Monitored services</h2>
        {services.length === 0 && (
          <p className="panel-hint">
            No services registered yet. Register one via
            <code> POST /api/projects/&#123;projectId&#125;/services</code>.
          </p>
        )}
        <div className="service-grid">
          {services.map((s) => {
            const h = healthFor(s.slug);
            return (
              <div className="service-card" key={s.id}>
                <div className="service-card-header">
                  <h3>{s.name}</h3>
                  {h ? <StatusBadge status={h.status} /> : <span className="muted">no live check</span>}
                </div>
                <p className="muted">{s.slug}</p>
                {s.description && <p>{s.description}</p>}
                {h?.detail && <p className="muted small">{h.detail}</p>}
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}
