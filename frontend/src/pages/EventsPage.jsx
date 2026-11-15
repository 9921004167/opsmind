import { useEffect, useState } from 'react';
import { listAuditEvents } from '../api/audit';
import Loading from '../components/Loading';
import ErrorBanner from '../components/ErrorBanner';

export default function EventsPage() {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => { listAuditEvents().then(setRows).catch((e) => setError(e.status === 403 ? 'Audit events require the ADMIN or SRE role.' : e.message)); }, []);
  if (!rows && !error) return <Loading />;
  return (
    <div>
      <h1>Events</h1>
      <ErrorBanner message={error} />
      {rows && (
        <section className="panel">
          <p className="panel-hint">Audit trail for your organization (latest 200): logins, fault experiments, remediation decisions.</p>
          {rows.length === 0 ? <p className="panel-hint">No events recorded.</p> : (
            <table>
              <thead><tr><th>Time</th><th>Action</th><th>Entity</th><th>Actor</th></tr></thead>
              <tbody>{rows.map((r, n) => (
                <tr key={n}><td className="mono">{new Date(r.created_at).toLocaleString()}</td><td>{r.action}</td><td>{r.entity_type}</td><td className="mono">{r.actor_label || (r.actor_user_id || '').slice(0, 8)}</td></tr>
              ))}</tbody>
            </table>
          )}
        </section>
      )}
    </div>
  );
}
