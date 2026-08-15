import { motion } from 'framer-motion'
import { Trash2 } from 'lucide-react'

import { useStore } from '../lib/store'
import { api } from '../lib/api'
import { ago, cost, duration, shortPath } from '../lib/format'
import { STATUS_TONE, TONE_TEXT } from '../lib/glyphs'

export default function SessionRail() {
  const sessions = useStore((s) => s.sessions)
  const selectedId = useStore((s) => s.selectedId)
  const select = useStore((s) => s.select)

  const live = sessions.filter((s) => !isTerminal(s.status))
  const past = sessions.filter((s) => isTerminal(s.status))

  return (
    <aside className="flex w-[300px] shrink-0 flex-col border-r border-rule bg-deck">
      <Group title="active" count={live.length}>
        {live.map((session, index) => (
          <SessionRow
            key={session.id}
            session={session}
            index={index}
            selected={session.id === selectedId}
            onSelect={select}
          />
        ))}
        {live.length === 0 && (
          <p className="px-4 py-3 text-[11px] text-ink-faint">no agents running</p>
        )}
      </Group>

      <div className="min-h-0 flex-1 overflow-y-auto">
        <Group title="history" count={past.length} sticky>
          {past.map((session, index) => (
            <SessionRow
              key={session.id}
              session={session}
              index={index}
              selected={session.id === selectedId}
              onSelect={select}
            />
          ))}
        </Group>
      </div>
    </aside>
  )
}

/** Mirrors SessionStatus.isTerminal() — IDLE is a live window between turns. */
function isTerminal(status) {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

function Group({ title, count, sticky, children }) {
  return (
    <section className={sticky ? '' : 'border-b border-rule'}>
      <div
        className={`flex items-center gap-2 border-b border-rule px-4 py-2 ${
          sticky ? 'sticky top-0 z-10 bg-deck' : ''
        }`}
      >
        <span className="label">{title}</span>
        <span className="tabular text-[10px] text-ink-faint">{count}</span>
        <div className="h-px flex-1 bg-rule" />
      </div>
      {children}
    </section>
  )
}

function SessionRow({ session, index, selected, onSelect }) {
  const removeSession = useStore((s) => s.removeSession)
  const notify = useStore((s) => s.notify)
  const tone = STATUS_TONE[session.status] ?? 'ink-dim'
  const running = session.status === 'RUNNING'
  const waiting = session.status === 'WAITING_APPROVAL'

  async function remove(event) {
    event.stopPropagation()
    try {
      await api.deleteSession(session.id)
      removeSession(session.id)
    } catch (error) {
      notify(error.message, 'error')
    }
  }

  return (
    <motion.button
      initial={{ opacity: 0, x: -8 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: Math.min(index * 0.025, 0.3), duration: 0.25 }}
      onClick={() => onSelect(session.id)}
      className={`group relative block w-full border-b border-rule/60 px-4 py-2.5 text-left transition-colors ${
        selected ? 'bg-deck-3' : 'hover:bg-deck-2'
      } ${running ? 'scanning' : ''}`}
    >
      {/* Selection spine */}
      {selected && <span className="spine absolute inset-y-0 left-0 w-[2px] bg-live" />}

      <div className="flex items-center gap-2">
        <span
          className={`h-1.5 w-1.5 shrink-0 rounded-full bg-current ${TONE_TEXT[tone]} ${
            running ? 'pulse-live' : waiting ? 'pulse-amber' : ''
          }`}
        />
        <span className="truncate text-[12px] text-ink">{session.name}</span>
        <span
          role="button"
          tabIndex={0}
          onClick={remove}
          onKeyDown={(e) => e.key === 'Enter' && remove(e)}
          className="ml-auto shrink-0 opacity-0 transition-opacity group-hover:opacity-100 hover:text-coral"
          title="Delete session"
        >
          <Trash2 size={12} className="text-ink-faint hover:text-coral" />
        </span>
      </div>

      <div className="mt-1 flex items-center gap-2 pl-3.5">
        <span className={`label ${TONE_TEXT[tone]}`}>{session.status.toLowerCase()}</span>
        {session.autoApprove && (
          <span className="border border-rule px-1 text-[9px] uppercase tracking-wider text-ink-faint">
            auto
          </span>
        )}
        <span className="tabular ml-auto text-[10px] text-ink-faint">
          {isTerminal(session.status) ? duration(session.durationMs) : ago(session.createdAt)}
        </span>
      </div>

      <div className="mt-0.5 flex items-center gap-2 pl-3.5">
        <span className="truncate text-[10px] text-ink-faint">{shortPath(session.cwd)}</span>
        {session.totalCostUsd != null && (
          <span className="tabular ml-auto shrink-0 text-[10px] text-ink-faint">
            {cost(session.totalCostUsd)}
          </span>
        )}
      </div>
    </motion.button>
  )
}
