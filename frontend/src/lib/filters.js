/**
 * What each toggle above the timeline covers.
 *
 * Grouped by what a reader is actually looking for — "show me what it did" vs
 * "show me what it said" — rather than one switch per event type, which would be
 * a wall of chips nobody reads.
 *
 * ERROR is deliberately in no group: a failure is never hidden by a filter.
 */
export const FILTERS = [
  { key: 'prompts', label: 'prompts', tone: 'live', types: ['USER_PROMPT'] },
  { key: 'replies', label: 'replies', tone: 'ink', types: ['ASSISTANT_TEXT'] },
  { key: 'thinking', label: 'thinking', tone: 'violet', types: ['THINKING'] },
  { key: 'tools', label: 'tools', tone: 'cyan', types: ['TOOL_CALL', 'TOOL_RESULT'] },
  {
    key: 'gate',
    label: 'gate',
    tone: 'amber',
    types: ['APPROVAL_REQUEST', 'APPROVAL_DECISION'],
  },
  {
    key: 'system',
    label: 'system',
    tone: 'ink-dim',
    types: ['SESSION_START', 'SESSION_END', 'HOOK', 'SYSTEM'],
  },
]

const STORAGE_KEY = 'acc-filters'
const ALWAYS_VISIBLE = new Set(['ERROR'])

/** Event type -> filter key, built once from the table above. */
const TYPE_TO_KEY = new Map(
  FILTERS.flatMap((filter) => filter.types.map((type) => [type, filter.key])),
)

export function defaultFilters() {
  return Object.fromEntries(FILTERS.map((f) => [f.key, true]))
}

export function readStoredFilters() {
  // ?filters=tools,prompts turns on exactly those and nothing else, so a
  // filtered view can be linked or screenshotted without touching local state.
  try {
    const requested = new URLSearchParams(location.search).get('filters')
    if (requested !== null) {
      const wanted = new Set(requested.split(',').map((s) => s.trim()).filter(Boolean))
      const picked = Object.fromEntries(FILTERS.map((f) => [f.key, wanted.has(f.key)]))
      persistFilters(picked)
      return picked
    }
  } catch {
    // Malformed URL — fall through to the stored preference.
  }
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
    // Merge rather than replace, so a filter added in a later version defaults
    // to on instead of vanishing for anyone with saved preferences.
    return { ...defaultFilters(), ...stored }
  } catch {
    return defaultFilters()
  }
}

export function persistFilters(filters) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(filters))
  } catch {
    // Not persisting is survivable; the choice still applies this session.
  }
}

export function isVisible(event, filters) {
  if (ALWAYS_VISIBLE.has(event.type)) {
    return true
  }
  const key = TYPE_TO_KEY.get(event.type)
  return key === undefined ? true : filters[key] !== false
}

/** Per-filter counts, so a toggle can say how much it is holding back. */
export function countByFilter(events) {
  const counts = Object.fromEntries(FILTERS.map((f) => [f.key, 0]))
  for (const event of events) {
    const key = TYPE_TO_KEY.get(event.type)
    if (key !== undefined) {
      counts[key] += 1
    }
  }
  return counts
}
