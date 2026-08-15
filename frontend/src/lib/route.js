const VIEWS = ['flow', 'graph', 'term']
const DEFAULT_VIEW = 'flow'

/**
 * Routing lives in the URL hash — no SPA fallback needed on the daemon, and every
 * destination stays linkable and reload-safe.
 *
 * `#/overview` (and an empty hash) is the landing page; `#/<sessionId>/<view>`
 * is the control center. Overview is the default so a fresh open lands on the
 * dashboard rather than whichever session happens to be first.
 */
export function readRoute() {
  const [, first, view] = location.hash.replace(/^#\/?/, '/').split('/')
  if (!first || first === 'overview') {
    return { page: 'overview', sessionId: null, view: DEFAULT_VIEW }
  }
  return {
    page: 'control',
    sessionId: first,
    view: VIEWS.includes(view) ? view : DEFAULT_VIEW,
  }
}

export function writeRoute({ page, sessionId, view }) {
  const next =
    page === 'overview' || !sessionId
      ? '#/overview'
      : `#/${sessionId}/${VIEWS.includes(view) ? view : DEFAULT_VIEW}`
  if (location.hash !== next) {
    history.replaceState(null, '', next)
  }
}

export function onRouteChange(listener) {
  const handler = () => listener(readRoute())
  window.addEventListener('hashchange', handler)
  return () => window.removeEventListener('hashchange', handler)
}
