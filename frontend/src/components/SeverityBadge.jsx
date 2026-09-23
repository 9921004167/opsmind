const COLORS = { LOW: '#6b7280', MEDIUM: '#f59e0b', HIGH: '#ea580c', CRITICAL: '#dc2626' };

export default function SeverityBadge({ severity }) {
  const color = COLORS[severity] || '#6b7280';
  return (
    <span className="status-badge" style={{ backgroundColor: `${color}22`, color, border: `1px solid ${color}55` }}>
      {severity}
    </span>
  );
}
