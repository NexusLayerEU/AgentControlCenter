/**
 * Horizontal stacked bar for part-to-whole, with a legend beneath.
 *
 * These segments carry STATUS colours (running / waiting / failed, or the risk
 * bands), so identity is never left to colour alone: every segment is named and
 * counted in the legend. Segments are separated by a 2px gap in the surface
 * colour rather than a stroke — the gap is the separator, ink is data.
 */
export default function StackBar({ segments, total, empty = 'nothing recorded yet' }) {
  const sum = total ?? segments.reduce((acc, s) => acc + s.value, 0)

  if (sum === 0) {
    return <p className="py-4 text-center text-[11px] text-ink-faint">{empty}</p>
  }

  const shown = segments.filter((s) => s.value > 0)

  return (
    <div>
      <div className="flex h-4 w-full gap-[2px] overflow-hidden">
        {shown.map((segment) => (
          <span
            key={segment.label}
            title={`${segment.label}: ${segment.value}`}
            style={{
              width: `${(segment.value / sum) * 100}%`,
              background: `var(--color-${segment.tone})`,
            }}
            className="h-full first:rounded-l-[2px] last:rounded-r-[2px]"
          />
        ))}
      </div>

      <ul className="m-0 mt-2.5 flex list-none flex-wrap gap-x-4 gap-y-1 p-0">
        {segments.map((segment) => (
          <li key={segment.label} className="flex items-center gap-1.5">
            <span
              aria-hidden="true"
              className="h-2 w-2 shrink-0"
              style={{
                background: `var(--color-${segment.tone})`,
                opacity: segment.value === 0 ? 0.3 : 1,
              }}
            />
            <span className="text-[10px] text-ink-dim">{segment.label}</span>
            <span
              className={`tabular text-[10px] ${
                segment.value === 0 ? 'text-ink-faint' : 'text-ink'
              }`}
            >
              {segment.value}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
