import { EyeOff } from 'lucide-react'

import { useStore } from '../lib/store'
import { FILTERS, countByFilter } from '../lib/filters'
import { TONE_TEXT } from '../lib/glyphs'

/**
 * Row of toggles above the timeline.
 *
 * Each chip carries its own count, so turning one off is an informed choice
 * rather than a guess — and a filter with nothing behind it is visibly empty
 * instead of looking broken.
 */
export default function FilterBar({ events }) {
  const filters = useStore((s) => s.filters)
  const toggleFilter = useStore((s) => s.toggleFilter)
  const resetFilters = useStore((s) => s.resetFilters)

  const counts = countByFilter(events)
  const hidden = FILTERS.filter((f) => filters[f.key] === false)
    .reduce((sum, f) => sum + counts[f.key], 0)

  return (
    <div className="flex flex-wrap items-center gap-1">
      <span className="label mr-0.5">show</span>

      {FILTERS.map((filter) => {
        const on = filters[filter.key] !== false
        const count = counts[filter.key]
        const empty = count === 0
        return (
          <button
            key={filter.key}
            onClick={() => toggleFilter(filter.key)}
            aria-pressed={on}
            title={
              empty
                ? `no ${filter.label} in this session`
                : `${on ? 'Hide' : 'Show'} ${count} ${filter.label}`
            }
            className={`flex items-center gap-1.5 border px-1.5 py-0.5 text-[10px] transition-colors ${
              on
                ? 'border-rule-hot bg-deck-3 text-ink'
                : 'border-rule text-ink-faint hover:text-ink-dim'
            } ${empty ? 'opacity-40' : ''}`}
          >
            <span
              aria-hidden="true"
              className="h-1.5 w-1.5 shrink-0"
              style={{
                background: on ? `var(--color-${filter.tone})` : 'transparent',
                border: on ? 'none' : '1px solid var(--color-rule-hot)',
              }}
            />
            {filter.label}
            <span className="tabular text-ink-faint">{count}</span>
          </button>
        )
      })}

      {hidden > 0 && (
        <button
          onClick={resetFilters}
          title="Show everything again"
          className="ml-1 flex items-center gap-1 border border-amber/40 bg-amber/10 px-1.5 py-0.5 text-[10px] text-amber transition-colors hover:bg-amber/20"
        >
          <EyeOff size={10} />
          <span className="tabular">{hidden} hidden</span>
        </button>
      )}
    </div>
  )
}
