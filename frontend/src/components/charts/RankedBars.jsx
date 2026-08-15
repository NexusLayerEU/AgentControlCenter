/**
 * Horizontal ranked bars — the form for "compare magnitude across named things"
 * when the names are long enough that a column chart would need angled ticks.
 *
 * One hue for every bar. The categories (tool names) have no natural order, so a
 * value-ramp would double-encode length as lightness and tell the reader nothing
 * new. The value rides the tip of each bar; no axis is needed.
 */
export default function RankedBars({ data, nameKey, valueKey, tone = 'cyan', errorKey }) {
  if (data.length === 0) {
    return <p className="py-6 text-center text-[11px] text-ink-faint">no tool calls yet</p>
  }

  const max = Math.max(1, ...data.map((d) => d[valueKey]))

  return (
    <ul className="m-0 flex list-none flex-col gap-1.5 p-0">
      {data.map((row) => {
        const value = row[valueKey]
        const errors = errorKey ? row[errorKey] : 0
        const pct = (value / max) * 100
        return (
          <li key={row[nameKey]} className="group flex items-center gap-2.5">
            <span className="w-20 shrink-0 truncate text-right text-[11px] text-ink-dim">
              {row[nameKey]}
            </span>

            <span className="relative h-3.5 flex-1 bg-deck-2">
              <span
                className="absolute inset-y-0 left-0 transition-[width] duration-500"
                style={{
                  width: `${pct}%`,
                  background: `var(--color-${tone})`,
                  borderRadius: '0 2px 2px 0',
                }}
              />
              {/* Failures ride the same bar in the danger tone — a second colour
                  only where it means something, always with a count beside it. */}
              {errors > 0 && (
                <span
                  className="absolute inset-y-0 left-0 bg-coral"
                  style={{ width: `${(errors / max) * 100}%`, borderRadius: '0 2px 2px 0' }}
                  title={`${errors} failed`}
                />
              )}
            </span>

            <span className="tabular w-8 shrink-0 text-right text-[11px] text-ink">{value}</span>
            <span className="tabular w-10 shrink-0 text-right text-[10px] text-ink-faint">
              {row.avgMs ? `${Math.round(row.avgMs)}ms` : ''}
            </span>
          </li>
        )
      })}
    </ul>
  )
}
