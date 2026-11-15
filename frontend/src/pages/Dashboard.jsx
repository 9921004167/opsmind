import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listProjects, listServices } from '../api/tenant';
import { listAlerts } from '../api/alerts';
import { listIncidents } from '../api/incidents';
import { checkAllHealth } from '../api/health';
import { getMonitoringSummary } from '../api/audit';
import Loading from '../components/Loading';
import ErrorBanner from '../components/ErrorBanner';
import StatusBadge from '../components/StatusBadge';
import SeverityBadge from '../components/SeverityBadge';

const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const pct = (v) => (v == null ? '—' : `${(v * 100).toFixed(2)}%`);
const ms = (v) => (v == null ? '—' : `${Math.round(v * 1000)} ms`);
const rps = (v) => (v == null ? '—' : v.toFixed(2));

export default function Dashboard() {
  const [state, setState] = useState({ loading: true });
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const projects = await listProjects();
        const services = projects.length ? await listServices(projects[0].id) : [];
        const [alerts, incidents, health, monitoring] = await Promise.all([
          listAlerts(), listIncidents(), checkAllHealth(),
          getMonitoringSummary().catch(() => ({ available: false, services: [] })),
        ]);
        if (!cancelled) setState({ services, alerts, incidents, health, monitoring });
      } catch (e) {
        if (!cancelled) setState({ error: e.message });
      }
    })();
    return () => { cancelled = true; };
  }, []);

  if (state.loading) return <Loading label="Loading dashboard..." />;
  if (state.error) return <div><h1>Dashboard</h1><ErrorBanner message={state.error} /></div>;

  const { services, alerts, incidents, health, monitoring } = state;
  const open = incidents.filter((i) => !['RESOLVED', 'CLOSED'].includes(i.status));
  const done = incidents.length - open.length;
  const bySeverity = SEVERITIES.map((s) => [s, open.filter((i) => i.severity === s).length]);
  const maxSev = Math.max(1, ...bySeverity.map(([, n]) => n));
  const healthy = health.filter((h) => h.status === 'UP').length;

  return (
    <div>
      <h1>Dashboard</h1>
      <div className="stat-grid">
        <Stat label="Services healthy" value={`${healthy} / ${health.length}`} to="/services" />
        <Stat label="Active incidents" value={open.length} to="/incidents" />
        <Stat label="Resolved / closed" value={done} to="/archives" />
        <Stat label="Registered services" value={services.length} to="/services" />
      </div>

      <section className="panel">
        <h2>Service health</h2>
        <p className="panel-hint">Live /actuator/health checks, proxied by nginx.</p>
        <div className="svc-row">
          {health.map((h) => <div key={h.key} className="svc-tile"><span>{h.label}</span><StatusBadge status={h.status} /></div>)}
        </div>
        <p className="panel-hint">Kafka, Redis and database health are not exposed to this UI by the current services, so they are not shown.</p>
      </section>

      <section className="panel">
        <h2>Request metrics</h2>
        {!monitoring.available ? (
          <p className="panel-hint">Prometheus data unavailable from this deployment. No metrics are shown rather than estimated ones.</p>
        ) : (
          <>
            <p className="panel-hint">Prometheus, 1-minute rates, as of {new Date(monitoring.asOf).toLocaleTimeString()}. “—” means Prometheus returned no value.</p>
            <table>
              <thead><tr><th>Service</th><th>Up</th><th>5xx ratio</th><th>p95 latency</th><th>Requests/s</th></tr></thead>
              <tbody>
                {monitoring.services.map((s) => (
                  <tr key={s.job}><td>{s.job}</td><td>{s.up == null ? '—' : s.up === 1 ? 'yes' : 'no'}</td><td>{pct(s.errorRatio)}</td><td>{ms(s.p95Seconds)}</td><td>{rps(s.requestsPerSecond)}</td></tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </section>

      <div className="two-col">
        <section className="panel">
          <h2>Active incidents by severity</h2>
          {open.length === 0 ? <p className="panel-hint">No active incidents.</p> : bySeverity.map(([s, n]) => (
            <div className="bar-row" key={s}><span>{s}</span><div className="bar"><i style={{ width: `${(n / maxSev) * 100}%` }} /></div><b>{n}</b></div>
          ))}
        </section>
        <section className="panel">
          <h2>Recent incidents</h2>
          {incidents.length === 0 && <p className="panel-hint">No incidents yet.</p>}
          {incidents.slice(0, 5).map((i) => (
            <div className="list-row" key={i.id}><Link to={`/incidents/${i.id}`}>{i.incidentNumber} — {i.title}</Link><span><SeverityBadge severity={i.severity} /> <StatusBadge status={i.status} /></span></div>
          ))}
        </section>
      </div>

      <section className="panel">
        <h2>Recent alerts</h2>
        {alerts.length === 0 && <p className="panel-hint">No alerts yet.</p>}
        {alerts.slice(0, 5).map((a) => (
          <div className="list-row" key={a.id}><span>{a.title}</span><span><SeverityBadge severity={a.severity} /> <span className="panel-hint">{new Date(a.receivedAt).toLocaleString()}</span></span></div>
        ))}
      </section>
    </div>
  );
}

function Stat({ label, value, to }) {
  return <Link to={to} className="stat-card"><div className="stat-value">{value}</div><div className="stat-label">{label}</div></Link>;
}
