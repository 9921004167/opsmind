import { apiFetch } from './client';

export const getCapabilities = () => apiFetch('/api/fault-lab/capabilities');
export const listExperiments = () => apiFetch('/api/fault-lab/experiments');
export const startExperiment = (body) => apiFetch('/api/fault-lab/experiments', { method: 'POST', body: JSON.stringify(body) });
export const stopExperiment = (id) => apiFetch(`/api/fault-lab/experiments/${id}/stop`, { method: 'POST' });
export const emergencyStop = () => apiFetch('/api/fault-lab/emergency-stop', { method: 'POST' });
