import { apiFetch } from './client';

export function listProjects() {
  return apiFetch('/api/projects');
}

export function listEnvironments(projectId) {
  return apiFetch(`/api/projects/${projectId}/environments`);
}

export function listServices(projectId) {
  return apiFetch(`/api/projects/${projectId}/services`);
}
