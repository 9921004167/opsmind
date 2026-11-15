import { useEffect, useState } from 'react';
import { listServices } from '../api/tenant';
import { listAuditEvents } from '../api/audit';
import { useAuth } from '../auth/AuthContext';

/** Affected service + audit history for one incident, from real endpoints only. */
export default function IncidentExtras({ incident }) {
  const { session } = useAuth();
  const [service, setService] = useState(null);
  const [audit, setAudit] = useState(null);
  const canAudit = ['ADMIN', 'SRE'].includes(session?.role);

  useEffect(() => {
    listServices(incident.projectId).then((list) => setService(list.find((s) => s.id === incident.primaryServiceId) || null)).catch(() => setService(null));
    if (canAudit) listAuditEvents().then((rows) => setAudit(rows.filter((r) => r.entity_id === incident.id))).catch(() => setAudit([]));
  }, [incident, canAudit]);

  return (
    <div className="extras">
      <section className="panel"><h2>Affected service</h2>
        {service ? <p>{service.name} <span className="panel-hint">({service.slug})</span></p> : <p className="panel-hint">No primary service recorded for this incident.</p>}
      </section>
      <section className="panel"><h2>Audit history</h2>
        {!canAudit && <p className="panel-hint">Audit history requires the ADMIN or SRE role.</p>}
        {canAudit && audit && audit.length === 0 && <p className="panel-hint">No audit entries recorded for this incident.</p>}
        {canAudit && audit && audit.map((r, n) => <div className="list-row" key={n}><span>{r.action}</span><span className="mono">{new Date(r.created_at).toLocaleString()}</span></div>)}
      </section>
    </div>
  );
}
