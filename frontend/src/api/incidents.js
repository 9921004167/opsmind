import { apiFetch } from './client';

export function listIncidents() {
  return apiFetch('/api/incidents');
}

export function getIncident(id) {
  return apiFetch(`/api/incidents/${id}`);
}

export function getIncidentTimeline(id) {
  return apiFetch(`/api/incidents/${id}/timeline`);
}

export function updateIncidentStatus(id, status, note) {
  return apiFetch(`/api/incidents/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status, note }),
  });
}
