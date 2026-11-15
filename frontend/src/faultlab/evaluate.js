/**
 * Derives an experiment's observed outcome ONLY from real data: the experiment row and the organization's incidents.
 * Correlation = an incident on the target service created between the experiment start and 60s after it ended.
 * It is a heuristic and is labelled as such in the UI. Nothing is invented: if no incident correlates, the result
 * is INCONCLUSIVE, never PASSED.
 */
const secs = (a, b) => Math.round((new Date(b).getTime() - new Date(a).getTime()) / 1000);

export function matchingServiceIds(target, services) {
  return (services || []).filter((s) => (s.slug || '').includes(target)).map((s) => s.id);
}

export function evaluateExperiment(exp, incidents, services) {
  if (exp.status === 'RUNNING') return { result: 'IN_PROGRESS', reason: 'Experiment is running.' };
  if (exp.status === 'FAILED_TO_START') return { result: 'FAILED', reason: 'The fault was not applied.' };
  if (exp.status === 'DISABLE_UNCONFIRMED') return { result: 'FAILED', reason: 'The service did not confirm the fault was removed. Use Emergency stop.' };

  const ids = matchingServiceIds(exp.target_service, services);
  const from = new Date(exp.started_at).getTime() - 5000;
  const to = new Date(exp.ended_at || Date.now()).getTime() + 60000;
  const hit = (incidents || [])
    .filter((i) => ids.includes(i.primaryServiceId) && new Date(i.createdAt).getTime() >= from && new Date(i.createdAt).getTime() <= to)
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))[0];

  if (!hit) return { result: 'INCONCLUSIVE', reason: 'No incident correlated with this experiment (the fault may be below alert thresholds, or the run too short).' };
  const detectionSeconds = Math.max(0, secs(exp.started_at, hit.createdAt));
  if (!hit.resolvedAt) return { result: 'INCONCLUSIVE', reason: 'Incident detected but not resolved yet.', incident: hit, detectionSeconds };
  return { result: 'PASSED', reason: 'Incident detected and resolved.', incident: hit, detectionSeconds,
    recoverySeconds: Math.max(0, secs(exp.ended_at || exp.started_at, hit.resolvedAt)) };
}
