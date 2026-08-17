import { useMemo } from 'react'

import { EMPTY_EVENTS, useStore } from './store'
import { isVisible } from './filters'

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

/**
 * The timeline with the user's filters applied.
 *
 * Memoised on the event list and the filter object so the tree and graph are not
 * rebuilt on unrelated store updates.
 */
export function useVisibleTimeline() {
  const events = useTimeline()
  const filters = useStore((s) => s.filters)
  return useMemo(() => events.filter((e) => isVisible(e, filters)), [events, filters])
}
