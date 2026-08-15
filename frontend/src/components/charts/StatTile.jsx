import { motion } from 'framer-motion'

/**
 * A headline number. Deliberately not a chart: a single current value with no
 * series is a figure, and a one-bar bar chart would say less in more space.
 */
export default function StatTile({ label, value, unit, tone = 'ink', note, Icon, index = 0, live }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, delay: index * 0.05, ease: [0.16, 1, 0.3, 1] }}
      className="ticked deck relative flex flex-col justify-between px-4 py-3"
    >
      <div className="flex items-center gap-1.5">
        {Icon && <Icon size={11} style={{ color: `var(--color-${tone})` }} />}
        <span className="label">{label}</span>
        {live && (
          <span
            className="pulse-live ml-auto h-1.5 w-1.5 rounded-full"
            style={{ background: `var(--color-${tone})` }}
          />
        )}
      </div>

      <div className="mt-2 flex items-baseline gap-1">
        <span
          className="tabular font-display text-[30px] leading-none font-bold"
          style={{ color: `var(--color-${tone})` }}
        >
          {value}
        </span>
        {unit && <span className="text-[11px] text-ink-faint">{unit}</span>}
      </div>

      {note && <p className="mt-1.5 text-[10px] leading-tight text-ink-faint">{note}</p>}
    </motion.div>
  )
}
