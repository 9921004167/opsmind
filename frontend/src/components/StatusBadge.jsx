const COLORS = {
  NEW: '#3b82f6', INVESTIGATING: '#f59e0b', MITIGATING: '#f59e0b',
  AWAITING_APPROVAL: '#f59e0b', REMEDIATING: '#f59e0b', VERIFYING: '#f59e0b',
  RESOLVED: '#16a34a', ESCALATED: '#dc2626', CLOSED: '#6b7280', DETECTED: '#3b82f6',
  UP: '#16a34a', DOWN: '#dc2626', UNKNOWN: '#6b7280', UNREACHABLE: '#dc2626',
  PENDING: '#6b7280', RUNNING: '#f59e0b', COMPLETED: '#16a34a', FAILED: '#dc2626',
};

export default function StatusBadge({ status }) {
  const color = COLORS[status] || '#6b7280';
  return (
    <span className="status-badge" style={{ backgroundColor: `${color}22`, color, border: `1px solid ${color}55` }}>
      {status}
    </span>
  );
}
