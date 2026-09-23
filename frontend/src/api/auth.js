import { apiFetch } from './client';

export function login(email, password) {
  return apiFetch('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export function register({ organizationName, organizationSlug, fullName, email, password }) {
  return apiFetch('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ organizationName, organizationSlug, fullName, email, password }),
  });
}
