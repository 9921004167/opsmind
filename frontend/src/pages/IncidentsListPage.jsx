import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listIncidents } from '../api/incidents';
import Loading from '../components/Loading';
import ErrorBanner from '../components/ErrorBanner';
import StatusBadge from '../components/StatusBadge';
import SeverityBadge from '../components/SeverityBadge';

export default function IncidentsListPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [incidents, setIncidents] = useState([]);
  const [filter, setFilter] = useState('ALL');

  useEffect(() => {
    let cancelled = false;
    listIncidents()
      .then((data) => { if (!cancelled) setIncidents(data); })
      .catch((err) => { if (!cancelled) setError(err.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  if (loading) return <Loading label="Loading incidents..." />;

  const visible = filter === 'ALL' ? incidents
    : filter === 'ACTIVE' ? incidents.filter((i) => !['RESOLVED', 'CLOSED'].includes(i.status))
    : incidents.filter((i) => ['RESOLVED', 'CLOSED'].includes(i.status));

  return (
    <div>
      <h1>Incidents</h1>
      <ErrorBanner message={error} />

      <div className="chip-row">
        {['ALL', 'ACTIVE', 'RESOLVED'].map((f) => (
          <button key={f} className={`chip-button ${filter === f ? 'chip-button-active' : ''}`} onClick={() => setFilter(f)}>
            {f}
          </button>
        ))}
      </div>

      <section className="panel">
        {visible.length === 0 && <p className="panel-hint">No incidents match this filter.</p>}
        <table className="data-table">
          <thead>
            <tr><th>Number</th><th>Title</th><th>Severity</th><th>Status</th><th>Created</th></tr>
          </thead>
          <tbody>
            {visible.map((inc) => (
              <tr key={inc.id}>
                <td><Link to={`/incidents/${inc.id}`}>{inc.incidentNumber}</Link></td>
                <td>{inc.title}</td>
                <td><SeverityBadge severity={inc.severity} /></td>
                <td><StatusBadge status={inc.status} /></td>
                <td className="muted">{new Date(inc.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
