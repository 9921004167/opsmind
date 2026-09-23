import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from './AuthContext';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [fields, setFields] = useState({
    organizationName: '', organizationSlug: '', fullName: '', email: '', password: '',
  });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  function update(key, value) {
    setFields((f) => ({ ...f, [key]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await register(fields);
      navigate('/dashboard');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>OpsMind</h1>
        <p className="auth-subtitle">Register a new organization</p>
        {error && <div className="error-banner">{error}</div>}
        <label>Organization name</label>
        <input value={fields.organizationName} onChange={(e) => update('organizationName', e.target.value)} required />
        <label>Organization slug</label>
        <input value={fields.organizationSlug} onChange={(e) => update('organizationSlug', e.target.value)}
               placeholder="lowercase-with-hyphens" pattern="^[a-z0-9-]+$" required />
        <label>Your full name</label>
        <input value={fields.fullName} onChange={(e) => update('fullName', e.target.value)} required />
        <label>Email</label>
        <input type="email" value={fields.email} onChange={(e) => update('email', e.target.value)} required />
        <label>Password</label>
        <input type="password" value={fields.password} onChange={(e) => update('password', e.target.value)} minLength={8} required />
        <button type="submit" disabled={busy}>{busy ? 'Creating...' : 'Create organization'}</button>
        <p className="auth-footer">Already have an account? <Link to="/login">Sign in</Link></p>
      </form>
    </div>
  );
}
