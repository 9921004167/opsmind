import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { getCapabilities } from '../api/faultlab';

export default function SettingsPage() {
  const { session } = useAuth();
  const [caps, setCaps] = useState(null);
  const [capsError, setCapsError] = useState(null);
  const allowedRole = ['ADMIN', 'SRE', 'ENGINEER'].includes(session?.role);
  useEffect(() => { if (allowedRole) getCapabilities().then(setCaps).catch((e) => setCapsError(e.message)); }, [allowedRole]);
  return (
    <div>
      <h1>Settings</h1>
      <section className="panel"><h2>Session</h2>
        <table><tbody>
          <tr><td>Role</td><td>{session?.role}</td></tr>
          <tr><td>User ID</td><td className="mono">{session?.userId}</td></tr>
          <tr><td>Organization ID</td><td className="mono">{session?.organizationId}</td></tr>
        </tbody></table>
      </section>
      <section className="panel"><h2>Fault Lab</h2>
        {!allowedRole && <p className="panel-hint">Fault injection is not available to your role.</p>}
        {capsError && <p className="panel-hint">{capsError}</p>}
        {caps && (
          <table><tbody>
            <tr><td>Kill switch</td><td>{caps.enabled ? 'Enabled' : 'Disabled (default)'}</td></tr>
            <tr><td>Your organization allow-listed</td><td>{caps.orgAllowed ? 'Yes' : 'No'}</td></tr>
            <tr><td>Server configured</td><td>{caps.tokenConfigured ? 'Yes' : 'No'}</td></tr>
            <tr><td>Duration</td><td>default {caps.defaultSeconds}s, max {caps.maxSeconds}s</td></tr>
          </tbody></table>
        )}
        <p className="panel-hint">These are server-side controls and are changed in deployment configuration, not in the browser.</p>
      </section>
    </div>
  );
}
