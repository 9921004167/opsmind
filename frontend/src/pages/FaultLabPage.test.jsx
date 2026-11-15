import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import FaultLabPage from './FaultLabPage';
import { AuthProvider } from '../auth/AuthContext';

const faults = [{ key: 'payment.force-500', service: 'payment', label: 'Forced HTTP 500', needsMs: false, expectedImpact: 'x' }];
const base = { faults, maxSeconds: 120, defaultSeconds: 30, minLatencyMs: 100, maxLatencyMs: 5000, tokenConfigured: true };
const jwt = (role) => `h.${btoa(JSON.stringify({ sub: 'u', org: 'o', role }))}.s`;

function setup(caps, role = 'ADMIN') {
  localStorage.setItem('opsmind_access_token', jwt(role));
  vi.stubGlobal('fetch', vi.fn(async (url) => {
    const u = String(url);
    const body = u.includes('capabilities') ? caps : [];
    return { ok: true, status: 200, text: async () => JSON.stringify(body) };
  }));
  render(<MemoryRouter><AuthProvider><FaultLabPage /></AuthProvider></MemoryRouter>);
}
afterEach(() => localStorage.clear());

test('kill switch OFF: cannot create, but Emergency stop stays available to an authorized ADMIN', async () => {
  setup({ ...base, enabled: false, orgAllowed: true, emergencyStopAllowed: true });
  await waitFor(() => screen.getByText(/switched off/));
  expect(screen.getByText('New Experiment').disabled).toBe(true);
  expect(screen.getByText('Emergency stop').disabled).toBe(false);
});

test('organization removed from allow-list: cannot create, Emergency stop still available if it has fault standing', async () => {
  setup({ ...base, enabled: true, orgAllowed: false, emergencyStopAllowed: true }, 'SRE');
  await waitFor(() => screen.getByText(/not on the fault-injection allow-list/));
  expect(screen.getByText('New Experiment').disabled).toBe(true);
  expect(screen.getByText('Emergency stop').disabled).toBe(false);
});

test('organization with no fault standing: Emergency stop disabled', async () => {
  setup({ ...base, enabled: false, orgAllowed: false, emergencyStopAllowed: false });
  await waitFor(() => screen.getByText(/switched off/));
  expect(screen.getByText('Emergency stop').disabled).toBe(true);
});

test('ENGINEER can never use Emergency stop', async () => {
  setup({ ...base, enabled: true, orgAllowed: true, emergencyStopAllowed: true }, 'ENGINEER');
  await waitFor(() => expect(screen.getByText('New Experiment').disabled).toBe(false));
  expect(screen.getByText('Emergency stop').disabled).toBe(true);
});

test('start requires explicit confirmation', async () => {
  setup({ ...base, enabled: true, orgAllowed: true, emergencyStopAllowed: true });
  await waitFor(() => expect(screen.getByText('New Experiment').disabled).toBe(false));
  screen.getByText('New Experiment').click();
  expect((await screen.findByText('Start Experiment')).disabled).toBe(true);
});
