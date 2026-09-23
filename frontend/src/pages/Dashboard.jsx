import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listProjects, listServices } from '../api/tenant';
import { listAlerts } from '../api/alerts';
import { listIncidents } from '../api/incidents';
import { checkAllHealth } from '../api/health';
import Loading from '../components/Loading';
import ErrorBanner from '../components/ErrorBanner';
import StatusBadge from '../components/StatusBadge';
import SeverityBadge from '../components/SeverityBadge';

export default function Dashboard() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [serviceCount, setServiceCount] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [incidents, setIncidents] = useState([]);
  const [health, setHealth] = useState([]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const projects = await listProjects();
        let services = [];
        if (projects.length > 0) {
          services = await listServices(projects[0].id);
        }
        const [alertsData, incidentsData, healthData] = await Promise.all([
          listAlerts(), listIncidents(), checkAllHealth(),
        ]);
        if (cancelled) return;
        setServiceCount(services.length);
        setAlerts(alertsData.slice(0, 5));
        setIncidents(incidentsData.slice(0, 5));
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

  if (loading) return <Loading label="Loading dashboard..." />;

  const activeIncidents = incidents.filter((i) => !['RESOLVED', 'CLOSED'].includes(i.status));

  return (
    <div>
      <h1>Dashboard</h1>
      <ErrorBanner message={error} />

      <div className="stat-grid">
        <StatCard label="Registered services" value={serviceCount ?? '—'} to="/services" />
        <StatCard label="Active incidents" value={activeIncidents.length} to="/incidents" />
        <StatCard label="Recent alerts" value={alerts.length} to="/incidents" />
      </div>

      <div className="two-column">
        <section className="panel">
          <h2>System health</h2>
          <p className="panel-hint">Live checks proxied through nginx to each service's real /actuator/health.</p>
          <table className="data-table">
            <tbody>
              {health.map((h) => (
                <tr key={h.key}>
                  <td>{h.label}</td>
                  <td><StatusBadge status={h.status} /></td>
                  <td className="muted">{h.detail || ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="panel">
          <h2>Recent incidents</h2>
          {incidents.length === 0 && <p className="panel-hint">No incidents yet.</p>}
          <table className="data-table">
            <tbody>
              {incidents.map((inc) => (
                <tr key={inc.id}>
                  <td><Link to={`/incidents/${inc.id}`}>{inc.incidentNumber}</Link></td>
                  <td>{inc.title}</td>
                  <td><SeverityBadge severity={inc.severity} /></td>
                  <td><StatusBadge status={inc.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>

      <section className="panel">
        <h2>Recent alerts</h2>
        {alerts.length === 0 && <p className="panel-hint">No alerts yet.</p>}
        <table className="data-table">
          <thead>
            <tr><th>Source</th><th>Type</th><th>Severity</th><th>Title</th><th>Received</th></tr>
          </thead>
          <tbody>
            {alerts.map((a) => (
              <tr key={a.id}>
                <td>{a.source}</td>
                <td>{a.alertType}</td>
                <td><SeverityBadge severity={a.severity} /></td>
                <td>{a.title}</td>
                <td className="muted">{new Date(a.receivedAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function StatCard({ label, value, to }) {
  return (
    <Link to={to} className="stat-card">
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
    </Link>
  );
}
