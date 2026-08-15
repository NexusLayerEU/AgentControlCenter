import { useState } from 'react'

/**
 * Column chart for a single measure over time.
 *
 * One hue, not a palette: the job is magnitude, and colouring each column by its
 * own value would double-encode what the height already says.
 *
 * Laid out in HTML rather than a stretched SVG viewBox — a `preserveAspectRatio:
 * none` viewBox scales bar width with the container, which silently blows past
 * the 24px mark cap on a wide panel. Flex keeps the cap in real pixels, and the
 * space between columns is surface, never a stroke.
 */
export default function Bars({
  data,
  xKey,
  yKey,
  label,
  tone = 'live',
  height = 132,
  formatX = (v) => v,
  formatValue = (v) => v,
}) {
  const [hover, setHover] = useState(null)

  const max = Math.max(1, ...data.map((d) => d[yKey]))
  const peak = data.reduce((best, d) => (d[yKey] > (best?.[yKey] ?? -1) ? d : best), null)

  return (
    <figure className="m-0">
      <div
        className="relative flex items-end gap-[3px] border-b border-rule"
        style={{ height }}
        role="img"
        aria-label={`${label}. Peak ${formatValue(max)}.`}
        onMouseLeave={() => setHover(null)}
      >
        {data.map((d, i) => {
          const value = d[yKey]
          const pct = value === 0 ? 0 : Math.max(4, (value / max) * 100)
          const active = hover === i
          return (
            <div
              key={d[xKey]}
              className="flex h-full flex-1 cursor-default items-end justify-center"
              style={{ maxWidth: 24 }}
              onMouseEnter={() => setHover(i)}
            >
              <div
                className="w-full transition-[opacity,height] duration-300"
                style={{
                  height: `${pct}%`,
                  background: value === 0 ? 'var(--color-rule)' : `var(--color-${tone})`,
                  // Rounded data-end, square at the baseline.
                  borderRadius: '3px 3px 0 0',
                  opacity: hover === null || active ? 1 : 0.4,
                  minHeight: value === 0 ? 2 : undefined,
                }}
              />
            </div>
          )
        })}

        {hover !== null && (
          <div className="deck pointer-events-none absolute top-0 left-1/2 z-10 -translate-x-1/2 border px-2 py-1 text-[10px] whitespace-nowrap">
            <span className="text-ink-dim">{formatX(data[hover][xKey])}</span>{' '}
            <span className="tabular" style={{ color: `var(--color-${tone})` }}>
              {formatValue(data[hover][yKey])}
            </span>
          </div>
        )}
      </div>

      {/* Only the endpoints and the peak are labelled — a number on every column
          is chaos and goes unread; the tooltip carries the rest. */}
      <figcaption className="mt-1.5 flex justify-between text-[9px] text-ink-faint">
        <span>{formatX(data[0]?.[xKey])}</span>
        {peak && peak[yKey] > 0 && (
          <span className="tabular">peak {formatValue(peak[yKey])}</span>
        )}
        <span>{formatX(data[data.length - 1]?.[xKey])}</span>
      </figcaption>
    </figure>
  )
}
