import { apiFetch } from './client';

export function listAlerts() {
  return apiFetch('/api/alerts');
}
