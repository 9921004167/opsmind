import { apiFetch } from './client';

export function listInvestigations(incidentId) {
  return apiFetch(`/api/incidents/${incidentId}/investigations`);
}

export function getInvestigation(id) {
  return apiFetch(`/api/investigations/${id}`);
}

export function createInvestigation(incidentId) {
  return apiFetch(`/api/incidents/${incidentId}/investigations`, { method: 'POST' });
}

export function rerunInvestigation(id) {
  return apiFetch(`/api/investigations/${id}/run`, { method: 'POST' });
}
