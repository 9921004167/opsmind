import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import '../shell.css';

const canRunFaults = (role) => ['ADMIN', 'SRE', 'ENGINEER'].includes(role);

export default function Layout() {
  const { session, logout } = useAuth();
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <NavLink to="/" className="sidebar-brand"><span className="brand-mark" />OpsMind</NavLink>
        <nav aria-label="Primary">
          <div className="nav-group">Observe</div>
          <NavLink to="/dashboard" className={navClass}>Dashboard</NavLink>
          <NavLink to="/services" className={navClass}>Services</NavLink>
          <NavLink to="/incidents" className={navClass}>Incidents</NavLink>
          <NavLink to="/archives" className={navClass}>Archives</NavLink>
          {canRunFaults(session?.role) && (<>
            <div className="nav-group">Chaos</div>
            <NavLink to="/experiments" className={navClass}>Experiments</NavLink>
          </>)}
          <div className="nav-group">Platform</div>
          {['ADMIN', 'SRE'].includes(session?.role) && <NavLink to="/events" className={navClass}>Events</NavLink>}
          <NavLink to="/architecture" className={navClass}>Architecture</NavLink>
          <NavLink to="/settings" className={navClass}>Settings</NavLink>
          <NavLink to="/docs" className={navClass}>Documentation</NavLink>
        </nav>
        <div className="sidebar-footer">
          <div className="role-badge">{session?.role}</div>
          <button className="link-button" onClick={logout}>Sign out</button>
        </div>
      </aside>
      <main className="main-content"><Outlet /></main>
    </div>
  );
}

function navClass({ isActive }) {
  return isActive ? 'nav-link nav-link-active' : 'nav-link';
}
