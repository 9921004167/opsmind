import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Landing from './Landing';

const renderLanding = () => render(<MemoryRouter><Landing /></MemoryRouter>);

afterEach(() => vi.restoreAllMocks());

test('shows hero heading and both CTAs', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
  renderLanding();
  expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Detect. Investigate. Recover.');
  expect(screen.getByText('Explore Platform')).toBeTruthy();
  expect(screen.getByText('Launch Demo').getAttribute('href')).toBe('/demo');
  await waitFor(() => screen.getByText(/Live health unavailable/));
});

test('when services are unreachable, no health figures are fabricated', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
  renderLanding();
  await waitFor(() => screen.getByText(/no figures shown/));
  expect(screen.queryByText(/: UP/)).toBeNull();
});

test('renders real status returned by the health endpoints', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP' }) }));
  renderLanding();
  await waitFor(() => expect(screen.getAllByText(/: UP/).length).toBe(4));
});

test('fault showcase lists only supported faults and notes auth requirement', () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('x')));
  renderLanding();
  expect(screen.getByText(/Redis failure/)).toBeTruthy();
  expect(screen.getByText(/requires an authorized sign-in/)).toBeTruthy();
});
