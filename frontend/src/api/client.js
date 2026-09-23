const TOKEN_KEY = 'opsmind_access_token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

/**
 * Every OpsMind API call goes through here. Paths are relative ("/api/...") -
 * nginx (in Docker) or Vite's dev proxy (in `npm run dev`) forwards them to
 * opsmind-core, so the browser never needs to know opsmind-core's real address
 * and never hits a cross-origin request.
 */
export async function apiFetch(path, options = {}) {
  const token = getToken();
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(path, { ...options, headers });

  if (response.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new Error('Session expired - please log in again');
  }

  if (!response.ok) {
    let body;
    try {
      body = await response.json();
    } catch {
      body = { message: `Request failed with status ${response.status}` };
    }
    const error = new Error(body.message || 'Request failed');
    error.status = response.status;
    error.body = body;
    throw error;
  }

  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
