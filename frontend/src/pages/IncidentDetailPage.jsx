import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { getIncident, getIncidentTimeline, updateIncidentStatus } from '../api/incidents';
import { listInvestigations, createInvestigation, rerunInvestigation } from '../api/investigations';
import { listRecommendations, createRecommendations, approveRecommendation, rejectRecommendation, executeRecommendation } from '../api/remediation';
import Loading from '../components/Loading';
import ErrorBanner from '../components/ErrorBanner';
import StatusBadge from '../components/StatusBadge';
import SeverityBadge from '../components/SeverityBadge';

const TABS = ['Overview', 'Timeline', 'Investigation & RCA', 'Historical Incidents', 'Remediation'];

export default function IncidentDetailPage() {
  const { id } = useParams();
  const [tab, setTab] = useState('Overview');
  const [incident, setIncident] = useState(null);
  const [timeline, setTimeline] = useState([]);
  const [investigations, setInvestigations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const reload = useCallback(async () => {
    try {
      const [inc, tl, inv] = await Promise.all([
        getIncident(id), getIncidentTimeline(id), listInvestigations(id),
      ]);
      setIncident(inc);
      setTimeline(tl);
      setInvestigations(inv);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { reload(); }, [reload]);

  if (loading) return <Loading label="Loading incident..." />;
  if (error) return <ErrorBanner message={error} />;
  if (!incident) return null;

  return (
    <div>
      <div className="incident-header">
        <div>
          <h1>{incident.incidentNumber} — {incident.title}</h1>
          <div className="chip-row">
            <SeverityBadge severity={incident.severity} />
            <StatusBadge status={incident.status} />
          </div>
        </div>
      </div>

      <div className="tab-row">
        {TABS.map((t) => (
          <button key={t} className={`tab-button ${tab === t ? 'tab-button-active' : ''}`} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'Overview' && <OverviewTab incident={incident} onChanged={reload} />}
      {tab === 'Timeline' && <TimelineTab timeline={timeline} />}
      {tab === 'Investigation & RCA' && <InvestigationTab incidentId={id} investigations={investigations} onChanged={reload} />}
      {tab === 'Historical Incidents' && <HistoricalTab investigations={investigations} />}
      {tab === 'Remediation' && <RemediationTab incidentId={id} />}
    </div>
  );
}

function OverviewTab({ incident, onChanged }) {
  const [note, setNote] = useState('');
  const [targetStatus, setTargetStatus] = useState(incident.allowedNextStatuses[0] || '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function handleTransition(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await updateIncidentStatus(incident.id, targetStatus, note);
      setNote('');
      onChanged();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <dl className="detail-list">
        <dt>Summary</dt><dd>{incident.summary || '—'}</dd>
        <dt>Created</dt><dd>{new Date(incident.createdAt).toLocaleString()}</dd>
        <dt>Updated</dt><dd>{new Date(incident.updatedAt).toLocaleString()}</dd>
        <dt>Resolved</dt><dd>{incident.resolvedAt ? new Date(incident.resolvedAt).toLocaleString() : '—'}</dd>
        <dt>Closed</dt><dd>{incident.closedAt ? new Date(incident.closedAt).toLocaleString() : '—'}</dd>
      </dl>

      <h3>Change status</h3>
      <ErrorBanner message={error} />
      {incident.allowedNextStatuses.length === 0 ? (
        <p className="panel-hint">This incident is in a terminal status - no further transitions are allowed.</p>
      ) : (
        <form onSubmit={handleTransition} className="inline-form">
          <select value={targetStatus} onChange={(e) => setTargetStatus(e.target.value)}>
            {incident.allowedNextStatuses.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <input placeholder="Optional note" value={note} onChange={(e) => setNote(e.target.value)} />
          <button type="submit" disabled={busy}>{busy ? 'Updating...' : 'Update status'}</button>
        </form>
      )}
    </div>
  );
}

function TimelineTab({ timeline }) {
  if (timeline.length === 0) return <div className="panel"><p className="panel-hint">No timeline entries yet.</p></div>;
  return (
    <div className="panel">
      <ul className="timeline">
        {timeline.map((entry) => (
          <li key={entry.id}>
            <div className="timeline-time">{new Date(entry.occurredAt).toLocaleString()}</div>
            <div className="timeline-type">{entry.eventType}</div>
            <div className="timeline-desc">{entry.description}</div>
            <div className="muted small">actor: {entry.actor}</div>
          </li>
        ))}
      </ul>
    </div>
  );
}

function InvestigationTab({ incidentId, investigations, onChanged }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function runNew() {
    setBusy(true);
    setError(null);
    try {
      await createInvestigation(incidentId);
      onChanged();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function rerun(investigationId) {
    setBusy(true);
    setError(null);
    try {
      await rerunInvestigation(investigationId);
      onChanged();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <div className="panel">
        <ErrorBanner message={error} />
        <button onClick={runNew} disabled={busy}>
          {busy ? 'Running (this can take several seconds - Prometheus + Tempo + Gemini)...' : 'Run new investigation'}
        </button>
        <p className="panel-hint">This call is synchronous and may take a few seconds.</p>
      </div>

      {investigations.length === 0 && <div className="panel"><p className="panel-hint">No investigations yet.</p></div>}

      {investigations.map((inv) => (
        <div className="panel" key={inv.id}>
          <div className="chip-row">
            <StatusBadge status={inv.status} />
            <span className="muted small">started {new Date(inv.startedAt).toLocaleString()}</span>
            {inv.aiProvider && <span className="muted small">{inv.aiProvider} / {inv.aiModel}</span>}
          </div>

          {inv.status === 'FAILED' && (
            <div>
              <p className="error-banner">{inv.failureReason}</p>
              <button onClick={() => rerun(inv.id)} disabled={busy}>Retry investigation</button>
            </div>
          )}

          {inv.rcaFinding && <RcaCard rca={inv.rcaFinding} />}

          <details>
            <summary>Evidence ({inv.evidence.length})</summary>
            <table className="data-table">
              <thead><tr><th>Type</th><th>Source</th><th>Title</th><th>Value</th><th>Observed</th></tr></thead>
              <tbody>
                {inv.evidence.map((e) => (
                  <tr key={e.id}>
                    <td>{e.type}</td><td>{e.source}</td><td>{e.title}</td>
                    <td>{e.observedValue ?? '—'}</td>
                    <td className="muted">{new Date(e.observedAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </details>
        </div>
      ))}
    </div>
  );
}

function RcaCard({ rca }) {
  return (
    <div className="rca-card">
      <div className="chip-row">
        <span className="chip">confidence: {rca.confidenceLevel}{rca.confidenceScore != null ? ` (${rca.confidenceScore.toFixed(2)})` : ''}</span>
        {rca.affectedService && <span className="chip">{rca.affectedService}</span>}
      </div>
      <h4>Root cause (hypothesis)</h4>
      <p>{rca.rootCause}</p>
      <h4>Reasoning</h4>
      <p>{rca.reasoningSummary}</p>
      {rca.impact && <><h4>Impact</h4><p>{rca.impact}</p></>}
      <h4>Recommended actions</h4>
      <ul>{rca.recommendedActions.map((a, i) => <li key={i}>{a}</li>)}</ul>
      <p className="muted small">Generated {new Date(rca.generatedAt).toLocaleString()} · citing {rca.supportingEvidenceIds.length} evidence item(s)</p>
    </div>
  );
}

function HistoricalTab({ investigations }) {
  // No dedicated "list similar historical incidents" endpoint is confirmed to
  // exist - Phase 7 wires retrieval directly into the RCA prompt rather than
  // exposing it separately. This tab shows whatever structured data IS present
  // on the investigation response (if your backend adds a `historicalIncidents`
  // field later, it will render automatically) and otherwise points you at the
  // RCA reasoning text, which is where any historical reference currently
  // surfaces in this codebase's Phase 7 design.
  const withHistory = investigations.find((inv) => Array.isArray(inv.historicalIncidents) && inv.historicalIncidents.length > 0);

  if (withHistory) {
    return (
      <div className="panel">
        <table className="data-table">
          <thead><tr><th>Incident</th><th>Similarity</th><th>Summary</th></tr></thead>
          <tbody>
            {withHistory.historicalIncidents.map((h, i) => (
              <tr key={i}><td>{h.incidentNumber || h.incidentId}</td><td>{h.similarity ?? '—'}</td><td>{h.summary}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <div className="panel">
      <p className="panel-hint">
        No structured "similar historical incidents" field was found on this incident's investigations.
        Phase 7 retrieval is wired directly into Gemini's RCA prompt - check the
        <strong> Reasoning</strong> section under Investigation &amp; RCA for any reference
        to a prior incident the model was given as context.
      </p>
    </div>
  );
}

function RemediationTab({ incidentId }) {
  const [state, setState] = useState({ loading: true, available: true, recommendations: [] });
  const [error, setError] = useState(null);

  const reload = useCallback(async () => {
    const res = await listRecommendations(incidentId);
    setState({ loading: false, available: res.available, recommendations: res.data || [] });
  }, [incidentId]);

  useEffect(() => { reload(); }, [reload]);

  async function generate() {
    setError(null);
    try {
      await createRecommendations(incidentId);
      reload();
    } catch (err) {
      setError(err.message);
    }
  }

  async function act(fn, id) {
    setError(null);
    try {
      await fn(id);
      reload();
    } catch (err) {
      setError(err.message);
    }
  }

  if (state.loading) return <Loading label="Checking remediation availability..." />;

  if (!state.available) {
    return (
      <div className="panel">
        <p className="panel-hint">
          Remediation (Phase 8) is not deployed on this backend yet. Once it is, this tab will show
          AI-generated recommendations, approvals, executions, and verification results automatically -
          no frontend changes needed.
        </p>
      </div>
    );
  }

  return (
    <div>
      <div className="panel">
        <ErrorBanner message={error} />
        <button onClick={generate}>Generate remediation recommendations</button>
      </div>

      {state.recommendations.length === 0 && (
        <div className="panel"><p className="panel-hint">No recommendations yet for this incident.</p></div>
      )}

      {state.recommendations.map((r) => (
        <div className="panel" key={r.id}>
          <div className="chip-row">
            <StatusBadge status={r.status} />
            <span className="chip">risk: {r.riskLevel}</span>
          </div>
          <p>{r.description || r.rationale}</p>
          {r.status === 'PROPOSED' && (
            <div className="chip-row">
              <button onClick={() => act(approveRecommendation, r.id)}>Approve</button>
              <button onClick={() => act(rejectRecommendation, r.id)}>Reject</button>
            </div>
          )}
          {r.status === 'APPROVED' && (
            <button onClick={() => act(executeRecommendation, r.id)}>Execute</button>
          )}
        </div>
      ))}
    </div>
  );
}
