/**
 * A two-position slider.
 *
 * Square rather than a pill: the rest of the deck is hairlines and right angles,
 * and a rounded iOS-style capsule would read as borrowed from somewhere else. The
 * knob slides on a CSS transform, so the reduced-motion media query already
 * covers it without pulling in an animation library.
 *
 * State and identity are separate channels on purpose. The track is always the
 * same accent when on, so "is it on?" is answered at a glance; the little square
 * beside the label carries which category it controls. Tinting the track by
 * category made switches for the neutral categories (prompts, system) look off
 * while they were on, because their tones are text greys.
 */
export default function Toggle({ on, onChange, label, count, tone = 'live', title }) {
  const empty = count === 0

  return (
    <button
      role="switch"
      aria-checked={on}
      aria-label={label}
      title={title}
      onClick={onChange}
      className={`group flex shrink-0 items-center gap-2 py-0.5 text-left transition-opacity ${
        empty ? 'opacity-45' : ''
      }`}
    >
      {/* Track — one colour for every switch, so on and off are unmistakable */}
      <span
        aria-hidden="true"
        className="relative block h-[14px] w-[28px] shrink-0 border transition-colors duration-150"
        style={{
          borderColor: on ? 'var(--color-live)' : 'var(--color-rule-hot)',
          background: on
            ? 'color-mix(in oklab, var(--color-live) 26%, transparent)'
            : 'var(--color-void)',
          boxShadow: on ? '0 0 10px -3px var(--color-live)' : 'none',
        }}
      >
        <span
          className="absolute top-[1.5px] block h-[9px] w-[9px] transition-[left,background] duration-150"
          style={{
            left: on ? '16px' : '2px',
            background: on ? 'var(--color-live)' : 'var(--color-ink-faint)',
          }}
        />
      </span>

      {/* Identity — which category this switch controls */}
      <span
        aria-hidden="true"
        className="h-2 w-2 shrink-0"
        style={{ background: `var(--color-${tone})`, opacity: on ? 1 : 0.35 }}
      />

      <span
        className={`whitespace-nowrap text-[11px] transition-colors ${
          on ? 'text-ink' : 'text-ink-faint group-hover:text-ink-dim'
        }`}
      >
        {label}
      </span>

      <span className="tabular text-[10px] text-ink-faint">{count}</span>
    </button>
  )
}
