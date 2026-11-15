import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listIncidents } from '../api/incidents';
import Loading from '../components/Loading';
import ErrorBanner from '../components/ErrorBanner';
import SeverityBadge from '../components/SeverityBadge';
import StatusBadge from '../components/StatusBadge';

export default function ArchivesPage() {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => { listIncidents().then((r) => setRows(r.filter((i) => ['RESOLVED', 'CLOSED'].includes(i.status)))).catch((e) => setError(e.message)); }, []);
  if (!rows && !error) return <Loading />;
  return (
    <div>
      <h1>Archives</h1>
      <ErrorBanner message={error} />
      <section className="panel">
        <p className="panel-hint">Resolved and closed incidents — the organizational memory source.</p>
        {rows && rows.length === 0 ? <p className="panel-hint">No resolved incidents yet.</p> : rows && (
          <table>
            <thead><tr><th>Incident</th><th>Severity</th><th>Status</th><th>Resolved</th></tr></thead>
            <tbody>{rows.map((i) => (
              <tr key={i.id}><td><Link to={`/incidents/${i.id}`}>{i.incidentNumber} — {i.title}</Link></td><td><SeverityBadge severity={i.severity} /></td><td><StatusBadge status={i.status} /></td><td className="mono">{i.resolvedAt ? new Date(i.resolvedAt).toLocaleString() : '—'}</td></tr>
            ))}</tbody>
          </table>
        )}
      </section>
    </div>
  );
}
