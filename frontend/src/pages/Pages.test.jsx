import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Dashboard from './Dashboard';
import ArchitecturePage from './ArchitecturePage';
import DemoPage from './DemoPage';

const wrap = (el) => render(<MemoryRouter>{el}</MemoryRouter>);
const json = (body) => ({ ok: true, status: 200, json: async () => body, text: async () => JSON.stringify(body) });

test('dashboard counts come from the full incident list and monitoring gaps are stated honestly', async () => {
  const incidents = [
    { id: '1', incidentNumber: 'INC-1', title: 'a', severity: 'HIGH', status: 'INVESTIGATING' },
    { id: '2', incidentNumber: 'INC-2', title: 'b', severity: 'LOW', status: 'RESOLVED' },
  ];
  vi.stubGlobal('fetch', vi.fn(async (url) => {
    const u = String(url);
    if (u.includes('/api/projects')) return json([]);
    if (u.includes('/api/alerts')) return json([]);
    if (u.includes('/api/incidents')) return json(incidents);
    if (u.includes('/api/monitoring')) return json({ available: false, services: [] });
    return json({ status: 'UP' });
  }));
  wrap(<Dashboard />);
  await waitFor(() => screen.getByText(/Prometheus data unavailable/));
  expect(screen.getByText('Active incidents').previousSibling.textContent).toBe('1');
  expect(screen.getByText('Resolved / closed').previousSibling.textContent).toBe('1');
  expect(screen.getByText('4 / 4')).toBeTruthy();
});

test('architecture explorer renders components and shows details on selection', () => {
  wrap(<ArchitecturePage />);
  for (const n of ['OpsMind Core', 'Order', 'Payment', 'Inventory', 'Kafka', 'PostgreSQL', 'Redis']) expect(screen.getAllByText(n).length).toBeGreaterThan(0);
  fireEvent.click(screen.getByLabelText('Redis'));
  expect(screen.getByText(/stock-lookup cache/)).toBeTruthy();
});

test('demo is clearly labelled simulated and makes no network calls', () => {
  const f = vi.fn(); vi.stubGlobal('fetch', f);
  wrap(<DemoPage />);
  expect(screen.getByText('SIMULATED DEMO')).toBeTruthy();
  fireEvent.click(screen.getByText('Next'));
  expect(f).not.toHaveBeenCalled();
});
