export function duration(ms) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`
  const minutes = Math.floor(ms / 60_000)
  const seconds = Math.round((ms % 60_000) / 1000)
  return `${minutes}m ${String(seconds).padStart(2, '0')}s`
}

export function clock(ts) {
  if (!ts) return '--:--:--'
  return new Date(ts).toLocaleTimeString('en-GB', { hour12: false })
}

export function ago(ts) {
  if (!ts) return '—'
  const delta = Date.now() - ts
  if (delta < 60_000) return `${Math.max(1, Math.round(delta / 1000))}s ago`
  if (delta < 3_600_000) return `${Math.round(delta / 60_000)}m ago`
  if (delta < 86_400_000) return `${Math.round(delta / 3_600_000)}h ago`
  return `${Math.round(delta / 86_400_000)}d ago`
}

export function cost(usd) {
  if (usd == null) return '—'
  return usd < 0.01 ? `$${usd.toFixed(4)}` : `$${usd.toFixed(2)}`
}

export function parsePayload(raw) {
  if (!raw) return {}
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(raw)
  } catch {
    return { raw }
  }
}

/**
 * Rewrites absolute paths in displayed text so the project-relative part stands
 * out. Applied to previews only — the Inspector deliberately shows raw values,
 * because when reviewing what an agent actually did, the literal string matters.
 */
export function relativise(text, cwd) {
  if (!text) return ''
  let result = text
  if (cwd) {
    const base = cwd.endsWith('/') ? cwd.slice(0, -1) : cwd
    result = result.split(`${base}/`).join('').split(base).join('.')
  }
  return result
}

/** Shortens absolute paths for display without losing the tail. */
export function shortPath(path) {
  if (!path) return ''
  const parts = path.split('/').filter(Boolean)
  return parts.length <= 3 ? path : `…/${parts.slice(-3).join('/')}`
}
