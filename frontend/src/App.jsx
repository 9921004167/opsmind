import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import ProtectedRoute from './auth/ProtectedRoute';
import Login from './auth/Login';
import Register from './auth/Register';
import Layout from './components/Layout';
import Landing from './pages/Landing';
import Dashboard from './pages/Dashboard';
import ServicesPage from './pages/ServicesPage';
import IncidentsListPage from './pages/IncidentsListPage';
import IncidentDetailPage from './pages/IncidentDetailPage';
import FaultLabPage from './pages/FaultLabPage';
import ArchivesPage from './pages/ArchivesPage';
import EventsPage from './pages/EventsPage';
import ArchitecturePage from './pages/ArchitecturePage';
import DemoPage from './pages/DemoPage';
import SettingsPage from './pages/SettingsPage';
import DocsPage from './pages/DocsPage';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/demo" element={<DemoPage />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/services" element={<ServicesPage />} />
          <Route path="/incidents" element={<IncidentsListPage />} />
          <Route path="/archives" element={<ArchivesPage />} />
          <Route path="/events" element={<EventsPage />} />
          <Route path="/architecture" element={<ArchitecturePage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/docs" element={<DocsPage />} />
          <Route path="/experiments" element={<FaultLabPage />} />
          <Route path="/incidents/:id" element={<IncidentDetailPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
