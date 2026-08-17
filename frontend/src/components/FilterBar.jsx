import { EyeOff } from 'lucide-react'

import { useStore } from '../lib/store'
import { FILTERS, countByFilter } from '../lib/filters'
import Toggle from './Toggle'

/**
 * The switch panel for the timeline below it.
 *
 * Each switch carries its own count, so turning one off is an informed choice —
 * and a category with nothing behind it is dimmed rather than looking broken.
 */
export default function FilterBar({ events }) {
  const filters = useStore((s) => s.filters)
  const toggleFilter = useStore((s) => s.toggleFilter)
  const resetFilters = useStore((s) => s.resetFilters)

  const counts = countByFilter(events)
  const hidden = FILTERS.filter((f) => filters[f.key] === false).reduce(
    (sum, f) => sum + counts[f.key],
    0,
  )

  return (
    <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5">
      <span className="label shrink-0">show</span>

      {FILTERS.map((filter) => {
        const on = filters[filter.key] !== false
        const count = counts[filter.key]
        return (
          <Toggle
            key={filter.key}
            on={on}
            count={count}
            tone={filter.tone}
            label={filter.label}
            onChange={() => toggleFilter(filter.key)}
            title={
              count === 0
                ? `no ${filter.label} in this session`
                : `${on ? 'Hide' : 'Show'} ${count} ${filter.label}`
            }
          />
        )
      })}

      {hidden > 0 && (
        <button
          onClick={resetFilters}
          title="Show everything again"
          className="flex shrink-0 items-center gap-1.5 border border-amber/40 bg-amber/10 px-2 py-0.5 text-[10px] text-amber transition-colors hover:bg-amber/20"
        >
          <EyeOff size={10} />
          <span className="tabular">{hidden} hidden</span>
        </button>
      )}
    </div>
  )
}
