import { apiFetch } from './client';

/**
 * Phase 8 (remediation) may not be deployed yet. Every function here returns
 * `{ available: false }` on a 404 rather than throwing, so IncidentDetailPage's
 * Remediation tab can render a clean "not available yet" state instead of
 * crashing the whole page when these endpoints don't exist.
 */
async function safeFetch(path, options) {
  try {
    const data = await apiFetch(path, options);
    return { available: true, data };
  } catch (e) {
    if (e.status === 404) return { available: false, data: null };
    throw e;
  }
}

export function listRecommendations(incidentId) {
  return safeFetch(`/api/incidents/${incidentId}/remediation-recommendations`);
}

export function createRecommendations(incidentId) {
  return safeFetch(`/api/incidents/${incidentId}/remediation-recommendations`, { method: 'POST' });
}

export function approveRecommendation(id) {
  return safeFetch(`/api/remediation-recommendations/${id}/approve`, { method: 'POST' });
}

export function rejectRecommendation(id) {
  return safeFetch(`/api/remediation-recommendations/${id}/reject`, { method: 'POST' });
}

export function executeRecommendation(id) {
  return safeFetch(`/api/remediation-recommendations/${id}/execute`, { method: 'POST' });
}
