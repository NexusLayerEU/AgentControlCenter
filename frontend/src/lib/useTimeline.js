import { EMPTY_EVENTS, useStore } from './store'

/**
 * Events for the currently selected session, or a stable empty array.
 *
 * zustand v5 compares snapshots by identity, so a selector that returns a fresh
 * `[]` literal reports a change on every render and spins into an infinite
 * update loop. Every selector must be reference-stable when nothing changed.
 */
export function useTimeline() {
  return useStore((s) => s.timelines[s.selectedId] ?? EMPTY_EVENTS)
}
