import { evaluateExperiment } from './evaluate';

const services = [{ id: 's-pay', slug: 'payment-service' }, { id: 's-inv', slug: 'inventory-service' }];
const base = { target_service: 'payment', started_at: '2026-11-15T10:00:00Z', ended_at: '2026-11-15T10:00:30Z', status: 'EXPIRED' };

test('running experiment is in progress', () => {
  expect(evaluateExperiment({ ...base, status: 'RUNNING' }, [], services).result).toBe('IN_PROGRESS');
});
test('no correlated incident is INCONCLUSIVE, never PASSED', () => {
  expect(evaluateExperiment(base, [], services).result).toBe('INCONCLUSIVE');
});
test('incident on another service does not count', () => {
  const inc = [{ primaryServiceId: 's-inv', createdAt: '2026-11-15T10:00:12Z', resolvedAt: '2026-11-15T10:01:00Z' }];
  expect(evaluateExperiment(base, inc, services).result).toBe('INCONCLUSIVE');
});
test('detected but unresolved is INCONCLUSIVE with detection time', () => {
  const r = evaluateExperiment(base, [{ primaryServiceId: 's-pay', createdAt: '2026-11-15T10:00:14Z' }], services);
  expect(r.result).toBe('INCONCLUSIVE'); expect(r.detectionSeconds).toBe(14);
});
test('detected and resolved is PASSED with detection and recovery times', () => {
  const r = evaluateExperiment(base, [{ primaryServiceId: 's-pay', createdAt: '2026-11-15T10:00:14Z', resolvedAt: '2026-11-15T10:01:10Z' }], services);
  expect(r).toMatchObject({ result: 'PASSED', detectionSeconds: 14, recoverySeconds: 40 });
});
test('incident long after the experiment is not correlated', () => {
  const inc = [{ primaryServiceId: 's-pay', createdAt: '2026-11-15T11:00:00Z', resolvedAt: '2026-11-15T11:05:00Z' }];
  expect(evaluateExperiment(base, inc, services).result).toBe('INCONCLUSIVE');
});
test('unconfirmed disable and failed start are FAILED', () => {
  expect(evaluateExperiment({ ...base, status: 'DISABLE_UNCONFIRMED' }, [], services).result).toBe('FAILED');
  expect(evaluateExperiment({ ...base, status: 'FAILED_TO_START' }, [], services).result).toBe('FAILED');
});
