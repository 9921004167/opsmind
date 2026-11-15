import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getCapabilities, listExperiments, startExperiment, stopExperiment, emergencyStop } from '../api/faultlab';
import { listIncidents } from '../api/incidents';
import { listProjects, listServices } from '../api/tenant';
import { useAuth } from '../auth/AuthContext';
import { evaluateExperiment } from '../faultlab/evaluate';
import Loading from '../components/Loading';

const fmt = (s) => (s == null ? '—' : `${s}s`);

function Countdown({ exp }) {
  const end = new Date(exp.started_at).getTime() + exp.duration_seconds * 1000;
  const [left, setLeft] = useState(Math.max(0, Math.round((end - Date.now()) / 1000)));
  useEffect(() => {
    const t = setInterval(() => setLeft(Math.max(0, Math.round((end - Date.now()) / 1000))), 1000);
    return () => clearInterval(t);
  }, [end]);
  return <span>{left}s remaining</span>;
}

export default function FaultLabPage() {
  const { session } = useAuth();
  const [caps, setCaps] = useState(null);
  const [exps, setExps] = useState([]);
  const [incidents, setIncidents] = useState([]);
  const [services, setServices] = useState([]);
  const [error, setError] = useState(null);
  const [stopResult, setStopResult] = useState(null);
  const [form, setForm] = useState({ faultKey: '', durationSeconds: 30, latencyMs: 1000, confirmed: false });
  const [busy, setBusy] = useState(false);
  const [showForm, setShowForm] = useState(false);

  const refresh = useCallback(async () => {
    try {
      setExps(await listExperiments());
      setIncidents(await listIncidents().catch(() => []));
    } catch (e) { setError(e.message); }
  }, []);

  useEffect(() => {
    getCapabilities().then((c) => { setCaps(c); setForm((f) => ({ ...f, faultKey: c.faults[0]?.key || '' })); }).catch((e) => setError(e.message));
    listProjects().then((p) => (p.length ? listServices(p[0].id) : [])).then(setServices).catch(() => setServices([]));
    refresh();
  }, [refresh]);

  const running = exps.filter((e) => e.status === 'RUNNING');
  const unconfirmed = exps.filter((e) => e.status === 'DISABLE_UNCONFIRMED');
  useEffect(() => {
    if (running.length === 0) return undefined;
    const t = setInterval(refresh, 3000);
    return () => clearInterval(t);
  }, [running.length, refresh]);

  if (!caps && !error) return <Loading />;
  const fault = caps?.faults.find((f) => f.key === form.faultKey);
  const canCreate = !!caps && caps.enabled && caps.orgAllowed && caps.tokenConfigured;
  // Emergency stop is a safety control: independent of the kill switch and of the allow-list.
  const canEmergency = ['ADMIN', 'SRE'].includes(session?.role) && !!caps?.emergencyStopAllowed;

  const act = async (fn) => {
    setBusy(true); setError(null);
    try { await fn(); await refresh(); } catch (e) { setError(e.message); } finally { setBusy(false); }
  };
  const start = () => act(async () => {
    await startExperiment({ faultKey: form.faultKey, durationSeconds: Number(form.durationSeconds), latencyMs: fault?.needsMs ? Number(form.latencyMs) : null });
    setShowForm(false); setForm((f) => ({ ...f, confirmed: false }));
  });
  const doEmergency = () => act(async () => { setStopResult(await emergencyStop()); });

  return (
    <div>
      <div className="fl-head">
        <h1>Experiments</h1>
        <div className="fl-actions">
          <button className="om-danger" disabled={!canEmergency || busy}
            title={canEmergency ? '' : 'Requires the ADMIN or SRE role and an organization with fault-injection standing'}
            onClick={() => window.confirm('Disable all injected faults on every service now? This affects all organizations.') && doEmergency()}>Emergency stop</button>
          <button className="om-primary" disabled={!canCreate} onClick={() => setShowForm((v) => !v)}>New Experiment</button>
        </div>
      </div>

      {error && <div className="banner" role="alert">{error}</div>}
      {stopResult && (
        <div className="banner" role="status">
          Emergency stop: confirmed disabled — {stopResult.servicesConfirmed.join(', ') || 'none'}.
          {stopResult.servicesNotConfirmed.length > 0 && <strong> NOT confirmed — {stopResult.servicesNotConfirmed.join(', ')}. A fault may still be active; check the service and retry.</strong>}
          {' '}Experiments affected: {stopResult.experimentsAffected}.
        </div>
      )}
      {unconfirmed.length > 0 && <div className="banner" role="alert">A fault on {[...new Set(unconfirmed.map((e) => e.target_service))].join(', ')} may still be active (disable not confirmed). New experiments on it are blocked; use Emergency stop.</div>}
      {caps && !caps.enabled && <div className="banner">Fault injection is switched off on this deployment (kill switch). New experiments cannot start; Emergency stop remains available to authorized users.</div>}
      {caps && caps.enabled && !caps.orgAllowed && <div className="banner">Your organization is not on the fault-injection allow-list. You cannot start experiments.</div>}
      {caps && caps.enabled && !caps.tokenConfigured && <div className="banner">Fault injection is not configured on the server.</div>}

      {showForm && canCreate && (
        <section className="panel">
          <h2>New experiment</h2>
          <div className="fl-form">
            <label>Fault
              <select value={form.faultKey} onChange={(e) => setForm({ ...form, faultKey: e.target.value })}>
                {caps.faults.map((f) => <option key={f.key} value={f.key}>{f.service} — {f.label}</option>)}
              </select>
            </label>
            {fault?.needsMs && (
              <label>Latency (ms, {caps.minLatencyMs}–{caps.maxLatencyMs})
                <input type="number" min={caps.minLatencyMs} max={caps.maxLatencyMs} value={form.latencyMs} onChange={(e) => setForm({ ...form, latencyMs: e.target.value })} />
              </label>
            )}
            <label>Duration (seconds, 1–{caps.maxSeconds}; default {caps.defaultSeconds})
              <input type="number" min="1" max={caps.maxSeconds} value={form.durationSeconds} onChange={(e) => setForm({ ...form, durationSeconds: e.target.value })} />
            </label>
            <p className="panel-hint">Expected impact: {fault?.expectedImpact} The fault is removed automatically when the duration ends.</p>
            <label className="check">
              <input type="checkbox" checked={form.confirmed} onChange={(e) => setForm({ ...form, confirmed: e.target.checked })} />
              I understand this injects a real failure into the reference e-commerce system.
            </label>
            <button className="om-primary" disabled={!form.confirmed || busy} onClick={start}>Start Experiment</button>
          </div>
        </section>
      )}

      {running.map((e) => (
        <section className="panel fl-active" key={e.id}>
          <h2>Running: {e.target_service} — {e.fault_key}</h2>
          <p><Countdown exp={e} /></p>
          <button className="om-danger" disabled={busy} onClick={() => act(() => stopExperiment(e.id))}>Stop Experiment</button>
        </section>
      ))}

      <section className="panel">
        <h2>History</h2>
        <p className="panel-hint">Detection, recovery and result are derived from real incidents on the target service (correlated by service and time). “Passed” = an incident was detected and resolved; otherwise Inconclusive, or Failed if the fault could not be applied or cleanly removed.</p>
        {exps.length === 0 ? <p className="panel-hint">No experiments have been run yet.</p> : (
          <table>
            <thead><tr><th>Target / fault</th><th>Duration</th><th>Status</th><th>Observed</th><th>Detection</th><th>Recovery</th><th>Result</th></tr></thead>
            <tbody>
              {exps.map((e) => {
                const ev = evaluateExperiment(e, incidents, services);
                const def = caps?.faults.find((f) => f.key === e.fault_key);
                return (
                  <tr key={e.id}>
                    <td>{e.target_service} — {e.fault_key}{e.param_ms ? ` (${e.param_ms} ms)` : ''}<div className="panel-hint">Expected: {def?.expectedImpact || '—'}</div></td>
                    <td>{e.duration_seconds}s</td>
                    <td><span className={`chip chip-${e.status}`} title={e.status_detail || ''}>{e.status}</span></td>
                    <td>{ev.incident ? <Link to={`/incidents/${ev.incident.id}`}>{ev.incident.incidentNumber} ({ev.incident.severity})</Link> : <span className="panel-hint">None observed</span>}</td>
                    <td>{fmt(ev.detectionSeconds)}</td>
                    <td>{fmt(ev.recoverySeconds)}</td>
                    <td><span className={`chip chip-R-${ev.result}`} title={ev.reason}>{ev.result.replace('_', ' ')}</span></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
