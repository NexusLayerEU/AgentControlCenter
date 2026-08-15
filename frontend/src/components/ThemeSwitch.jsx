import { motion } from 'framer-motion'

import { useStore } from '../lib/store'
import { THEMES } from '../lib/theme'

/**
 * Two-position hardware switch rather than an icon toggle: with only two decks,
 * showing both names makes the choice legible instead of a guess about what the
 * icon will do.
 */
export default function ThemeSwitch() {
  const theme = useStore((s) => s.theme)
  const setTheme = useStore((s) => s.setTheme)

  return (
    <div
      role="radiogroup"
      aria-label="Theme"
      className="flex gap-px border border-rule"
    >
      {THEMES.map((option) => {
        const active = option.id === theme
        return (
          <button
            key={option.id}
            role="radio"
            aria-checked={active}
            title={`${option.name} — ${option.hint}`}
            onClick={() => setTheme(option.id)}
            className={`relative px-2.5 py-1 text-[10px] uppercase tracking-widest transition-colors ${
              active ? 'text-live' : 'text-ink-faint hover:text-ink-dim'
            }`}
          >
            {active && (
              <motion.span
                layoutId="theme-slot"
                transition={{ type: 'spring', stiffness: 420, damping: 34 }}
                className="absolute inset-0 border border-live/45 bg-live/10"
              />
            )}
            <span className="relative">{option.name}</span>
          </button>
        )
      })}
    </div>
  )
}
