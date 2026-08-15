import { AnimatePresence, motion } from 'framer-motion'
import { X } from 'lucide-react'

import { useStore } from '../lib/store'
import { useTimeline } from '../lib/useTimeline'
import { clock, duration, parsePayload } from '../lib/format'
import { TONE_TEXT, iconFor, styleFor } from '../lib/glyphs'
import DiffView from './DiffView'

/** Right-hand detail pane for whichever node is selected in the tree or graph. */
export default function Inspector() {
  const inspectId = useStore((s) => s.inspectId)
  const events = useTimeline()
  const event = events.find((e) => e.id === inspectId)
  const setInspect = useStore((s) => s.setInspect)

  return (
    <AnimatePresence>
      {event && (
        <motion.aside
          initial={{ width: 0, opacity: 0 }}
          animate={{ width: 400, opacity: 1 }}
          exit={{ width: 0, opacity: 0 }}
          transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
          className="shrink-0 overflow-hidden bg-deck"
        >
          <div className="flex h-full w-[400px] flex-col">
            <Header event={event} onClose={() => setInspect(null)} />
            <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
              <Body event={event} />
            </div>
          </div>
        </motion.aside>
      )}
    </AnimatePresence>
  )
}

function Header({ event, onClose }) {
  const style = styleFor(event)
  const Icon = iconFor(event)

  return (
    <div className="flex shrink-0 items-center gap-2 border-b border-rule px-4 py-2.5">
      <Icon size={13} className={TONE_TEXT[style.tone]} />
      <span className={`label ${TONE_TEXT[style.tone]}`}>
        {event.type === 'TOOL_CALL' ? event.toolName : style.label}
      </span>
      <span className="tabular ml-auto text-[10px] text-ink-faint">{clock(event.ts)}</span>
      {event.durationMs != null && (
        <span className="tabular text-[10px] text-ink-faint">{duration(event.durationMs)}</span>
      )}
      <button onClick={onClose} className="ml-1 text-ink-faint hover:text-ink">
        <X size={14} />
      </button>
    </div>
  )
}

function Body({ event }) {
  const payload = parsePayload(event.payload)

  if (event.type === 'ASSISTANT_TEXT' || event.type === 'THINKING') {
    return (
      <p className="whitespace-pre-wrap text-[12px] leading-relaxed text-ink-dim">{payload.text}</p>
    )
  }

  if (event.type === 'TOOL_CALL') {
    const input = payload.input ?? {}
    const isEdit = event.toolName === 'Edit' || event.toolName === 'Write'

    return (
      <div className="space-y-4">
        <Field label="risk">
          <span className="text-[12px] text-ink">{payload.risk ?? 'normal'}</span>
        </Field>

        {isEdit && <DiffView toolName={event.toolName} input={input} />}

        {!isEdit &&
          Object.entries(input).map(([key, value]) => (
            <Field key={key} label={key}>
              <pre className="overflow-x-auto whitespace-pre-wrap border-l border-rule-hot bg-void/60 px-2.5 py-1.5 text-[11px] leading-relaxed text-ink">
                {typeof value === 'string' ? value : JSON.stringify(value, null, 2)}
              </pre>
            </Field>
          ))}
      </div>
    )
  }

  if (event.type === 'TOOL_RESULT') {
    return (
      <Field label={payload.isError ? 'error output' : 'output'}>
        <pre
          className={`overflow-x-auto whitespace-pre-wrap text-[11px] leading-relaxed ${
            payload.isError ? 'text-coral/85' : 'text-ink-dim'
          }`}
        >
          {payload.output || '(empty)'}
        </pre>
      </Field>
    )
  }

  return (
    <pre className="overflow-x-auto whitespace-pre-wrap text-[11px] leading-relaxed text-ink-dim">
      {JSON.stringify(payload, null, 2)}
    </pre>
  )
}

function Field({ label, children }) {
  return (
    <div>
      <p className="label mb-1.5">{label}</p>
      {children}
    </div>
  )
}
