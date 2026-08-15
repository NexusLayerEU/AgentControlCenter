import { create } from 'zustand'
import { api } from './api'
import { readRoute, writeRoute } from './route'
import { applyTheme, readStoredTheme } from './theme'

/** Shared stable reference for "this session has no events yet". */
export const EMPTY_EVENTS = Object.freeze([])

/**
 * Single source of truth for the dashboard. The websocket pushes deltas in;
 * REST is only used for the initial hydrate and for user-initiated actions.
 */
export const useStore = create((set, get) => ({
  connected: false,
  system: null,
  hooks: null,

  sessions: [],
  selectedId: null,

  /** sessionId -> event[] */
  timelines: {},
  approvals: [],

  // Session and view both come from the URL hash, so a link restores both.
  view: readRoute().view,
  page: readRoute().page,
  theme: readStoredTheme(),
  inspectId: null,
  composerOpen: false,
  toast: null,

  setConnected: (connected) => set({ connected }),
  setView: (view) => {
    set({ view })
    writeRoute({ page: get().page, sessionId: get().selectedId, view })
  },

  /** Land on the dashboard. */
  showOverview: () => {
    set({ page: 'overview', inspectId: null })
    writeRoute({ page: 'overview' })
  },

  /** Enter the control center, optionally on a specific session. */
  async openControl(sessionId) {
    const id = sessionId ?? get().selectedId ?? get().sessions[0]?.id ?? null
    set({ page: 'control' })
    if (id && id !== get().selectedId) {
      await get().select(id)
    } else {
      writeRoute({ page: 'control', sessionId: id, view: get().view })
    }
  },
  setInspect: (inspectId) => set({ inspectId }),
  setTheme: (theme) => set({ theme: applyTheme(theme) }),
  setComposerOpen: (composerOpen) => set({ composerOpen }),

  notify: (message, tone = 'info') => {
    set({ toast: { message, tone, at: Date.now() } })
    setTimeout(() => {
      if (Date.now() - (get().toast?.at ?? 0) >= 3400) set({ toast: null })
    }, 3500)
  },

  async hydrate() {
    const [system, sessions, approvals] = await Promise.all([
      api.systemStatus().catch(() => null),
      api.listSessions().catch(() => []),
      api.pendingApprovals().catch(() => []),
    ])
    const hooks = await api.hookStatus(system?.cwd).catch(() => null)
    set({ system, hooks, sessions, approvals })
    const routed = readRoute().sessionId
    const known = sessions.some((s) => s.id === routed)
    const selected = get().selectedId ?? (known ? routed : null) ?? sessions[0]?.id ?? null
    if (selected) await get().select(selected)
  },

  async refreshHooks() {
    const hooks = await api.hookStatus(get().system?.cwd).catch(() => null)
    set({ hooks })
  },

  async select(id) {
    set({ selectedId: id, inspectId: null })
    writeRoute({ page: get().page, sessionId: id, view: get().view })
    if (!id) return
    const events = await api.timeline(id).catch(() => [])
    set((state) => ({ timelines: { ...state.timelines, [id]: events } }))
  },

  /** Websocket: a session row was created or changed. */
  upsertSession(session) {
    set((state) => {
      const index = state.sessions.findIndex((s) => s.id === session.id)
      const sessions =
        index === -1
          ? [session, ...state.sessions]
          : state.sessions.map((s) => (s.id === session.id ? session : s))
      return { sessions, selectedId: state.selectedId ?? session.id }
    })
  },

  removeSession(id) {
    set((state) => {
      const sessions = state.sessions.filter((s) => s.id !== id)
      const timelines = { ...state.timelines }
      delete timelines[id]
      return {
        sessions,
        timelines,
        selectedId: state.selectedId === id ? (sessions[0]?.id ?? null) : state.selectedId,
      }
    })
  },

  /** Websocket: a new activity node. */
  appendEvent(event) {
    set((state) => {
      const existing = state.timelines[event.sessionId] ?? EMPTY_EVENTS
      if (existing.some((e) => e.id === event.id)) return state
      return {
        timelines: {
          ...state.timelines,
          [event.sessionId]: [...existing, event].sort((a, b) => a.seq - b.seq),
        },
      }
    })
  },

  /** Websocket: an existing node changed status or gained a duration. */
  updateEvent(event) {
    set((state) => {
      const existing = state.timelines[event.sessionId]
      if (!existing) return state
      return {
        timelines: {
          ...state.timelines,
          [event.sessionId]: existing.map((e) => (e.id === event.id ? event : e)),
        },
      }
    })
  },

  upsertApproval(approval) {
    set((state) => {
      const others = state.approvals.filter((a) => a.id !== approval.id)
      return { approvals: approval.status === 'pending' ? [...others, approval] : others }
    })
  },

  async decide(approvalId, allow, reason) {
    const result = allow ? await api.approve(approvalId, reason) : await api.deny(approvalId, reason)
    set((state) => ({ approvals: state.approvals.filter((a) => a.id !== approvalId) }))
    get().notify(result.message, result.delivered ? (allow ? 'ok' : 'warn') : 'warn')
  },
}))
