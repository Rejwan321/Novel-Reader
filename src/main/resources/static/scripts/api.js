// static/scripts/api.js
const API_BASE = '/api';

function handleResponse(res) {
  if (!res.ok) {
    return res.text().then(t => { throw new Error(t || `HTTP ${res.status}`); });
  }
  // Some endpoints may 204 No Content
  if (res.status === 204) return null;
  const ct = res.headers.get('content-type') || '';
  return ct.includes('application/json') ? res.json() : res.text();
}

export async function apiGet(path, params = {}) {
  const url = new URL(API_BASE + path, window.location.origin);
  Object.entries(params).forEach(([k, v]) => v !== undefined && url.searchParams.append(k, v));
  const res = await fetch(url, { credentials: 'include' });
  return handleResponse(res);
}

export async function apiPost(path, body = {}) {
  const res = await fetch(API_BASE + path, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  return handleResponse(res);
}

export async function apiDelete(path) {
  const res = await fetch(API_BASE + path, { method: 'DELETE', credentials: 'include' });
  return handleResponse(res);
}

export function formatDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleDateString();
}

export function imgOrPlaceholder(url) {
  return url && url.trim().length > 0 ? url : '/images/placeholder.png';
}
