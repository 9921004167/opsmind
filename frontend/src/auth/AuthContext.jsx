import { createContext, useContext, useState, useCallback } from 'react';
import { getToken, setToken, clearToken } from '../api/client';
import { login as apiLogin, register as apiRegister } from '../api/auth';

function decodeJwtPayload(token) {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => {
    const token = getToken();
    if (!token) return null;
    const claims = decodeJwtPayload(token);
    if (!claims) return null;
    return { userId: claims.sub, organizationId: claims.org, role: claims.role };
  });

  const login = useCallback(async (email, password) => {
    const res = await apiLogin(email, password);
    setToken(res.accessToken);
    setSession({ userId: res.userId, organizationId: res.organizationId, role: res.role });
    return res;
  }, []);

  const register = useCallback(async (fields) => {
    const res = await apiRegister(fields);
    setToken(res.accessToken);
    setSession({ userId: res.userId, organizationId: res.organizationId, role: res.role });
    return res;
  }, []);

  const logout = useCallback(() => {
    clearToken();
    setSession(null);
  }, []);

  return (
    <AuthContext.Provider value={{ session, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
