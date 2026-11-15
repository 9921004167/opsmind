import { apiFetch } from './client';
export const listAuditEvents = () => apiFetch('/api/audit-events');
export const getMonitoringSummary = () => apiFetch('/api/monitoring/summary');
