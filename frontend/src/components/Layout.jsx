import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Layout() {
  const { session, logout } = useAuth();

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">OpsMind</div>
        <nav>
          <NavLink to="/dashboard" className={navClass}>Dashboard</NavLink>
          <NavLink to="/incidents" className={navClass}>Incidents</NavLink>
          <NavLink to="/services" className={navClass}>Services</NavLink>
        </nav>
        <div className="sidebar-footer">
          <div className="role-badge">{session?.role}</div>
          <button className="link-button" onClick={logout}>Sign out</button>
        </div>
      </aside>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}

function navClass({ isActive }) {
  return isActive ? 'nav-link nav-link-active' : 'nav-link';
}
