// Small shared primitives for the OpsMind design system (tokens live in landing.css :root).
export function Button({ variant = 'primary', as: Tag = 'button', className = '', ...props }) {
  return <Tag className={`om-btn om-btn-${variant} ${className}`} {...props} />;
}

export function StatusDot({ state }) {
  // state: 'up' | 'down' | 'unknown'
  return <span className={`om-dot om-dot-${state}`} aria-hidden="true" />;
}

export function Card({ title, children, className = '' }) {
  return (
    <div className={`om-card ${className}`}>
      {title && <h3>{title}</h3>}
      {children}
    </div>
  );
}
