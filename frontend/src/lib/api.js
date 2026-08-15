const BASE = ''

async function request(path, options = {}) {
  const response = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!response.ok) {
    const detail = await response.text().catch(() => '')
    throw new Error(`${options.method || 'GET'} ${path} failed (${response.status}): ${detail.slice(0, 300)}`)
  }
  if (response.status === 204) return null
  const text = await response.text()
  return text ? JSON.parse(text) : null
}

export const api = {
  systemStatus: () => request('/api/system/status'),
  stats: () =>
    request(
      `/api/stats/overview?tz=${encodeURIComponent(
        Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
      )}`,
    ),

  listSessions: () => request('/api/sessions?limit=200'),
  getSession: (id) => request(`/api/sessions/${id}`),
  startSession: (body) => request('/api/sessions', { method: 'POST', body: JSON.stringify(body) }),
  cancelSession: (id) => request(`/api/sessions/${id}/cancel`, { method: 'POST' }),
  deleteSession: (id) => request(`/api/sessions/${id}`, { method: 'DELETE' }),
  setAutoApprove: (id, enabled) =>
    request(`/api/sessions/${id}/auto-approve`, {
      method: 'POST',
      body: JSON.stringify({ enabled }),
    }),

  timeline: (id) => request(`/api/sessions/${id}/events`),

  pendingApprovals: () => request('/api/approvals/pending'),
  approve: (id, reason) =>
    request(`/api/approvals/${id}/approve`, { method: 'POST', body: JSON.stringify({ reason }) }),
  deny: (id, reason) =>
    request(`/api/approvals/${id}/deny`, { method: 'POST', body: JSON.stringify({ reason }) }),

  hookStatus: (projectDir) =>
    request(`/api/hooks/status${projectDir ? `?projectDir=${encodeURIComponent(projectDir)}` : ''}`),
  installHooks: (body) => request('/api/hooks/install', { method: 'POST', body: JSON.stringify(body || {}) }),
  uninstallHooks: (body) =>
    request('/api/hooks/uninstall', { method: 'POST', body: JSON.stringify(body || {}) }),

  openTerminal: (id, body) =>
    request(`/api/terminals/${id}/open`, { method: 'POST', body: JSON.stringify(body) }),
  closeTerminal: (id) => request(`/api/terminals/${id}/close`, { method: 'POST' }),
}
