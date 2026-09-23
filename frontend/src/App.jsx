import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import ProtectedRoute from './auth/ProtectedRoute';
import Login from './auth/Login';
import Register from './auth/Register';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import ServicesPage from './pages/ServicesPage';
import IncidentsListPage from './pages/IncidentsListPage';
import IncidentDetailPage from './pages/IncidentDetailPage';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
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
          <Route path="/incidents/:id" element={<IncidentDetailPage />} />
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AuthProvider>
  );
}
