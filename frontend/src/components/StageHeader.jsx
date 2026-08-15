import { GitBranch, ListTree, Square, TerminalSquare, Zap, ZapOff } from 'lucide-react'

import { useStore } from '../lib/store'
import { useTimeline } from '../lib/useTimeline'
import { api } from '../lib/api'
import { cost, duration } from '../lib/format'
import { STATUS_TONE, TONE_TEXT } from '../lib/glyphs'

const VIEWS = [
  { id: 'flow', label: 'flow', Icon: ListTree },
  { id: 'graph', label: 'graph', Icon: GitBranch },
  { id: 'term', label: 'term', Icon: TerminalSquare },
]

export default function StageHeader() {
  const session = useStore((s) => s.sessions.find((x) => x.id === s.selectedId))
  const events = useTimeline()
  const view = useStore((s) => s.view)
  const setView = useStore((s) => s.setView)
  const notify = useStore((s) => s.notify)

  if (!session) return null

  const tone = STATUS_TONE[session.status] ?? 'ink-dim'
  const toolCalls = events.filter((e) => e.type === 'TOOL_CALL').length
  const running = !['COMPLETED', 'FAILED', 'CANCELLED'].includes(session.status)
  const adopted = session.origin === 'hook'

  async function toggleAutoApprove() {
    try {
      await api.setAutoApprove(session.id, !session.autoApprove)
      notify(
        session.autoApprove
          ? 'Approval gate armed — tool calls will now pause'
          : 'Auto-approve on — tool calls run unattended',
        session.autoApprove ? 'warn' : 'ok',
      )
    } catch (error) {
      notify(error.message, 'error')
    }
  }

  async function stop() {
    try {
      await api.cancelSession(session.id)
      notify('Session cancelled', 'warn')
    } catch (error) {
      notify(error.message, 'error')
    }
  }

  return (
    <div className="shrink-0 border-b border-rule bg-deck">
      <div className="flex items-center gap-3 px-5 pb-2 pt-3">
        <span
          className={`h-2 w-2 shrink-0 rounded-full bg-current ${TONE_TEXT[tone]} ${
            session.status === 'RUNNING' ? 'pulse-live' : ''
          } ${session.status === 'WAITING_APPROVAL' ? 'pulse-amber' : ''}`}
        />
        <h1 className="truncate font-display text-[15px] font-semibold tracking-tight text-ink">
          {session.name}
        </h1>
        <span className={`label ${TONE_TEXT[tone]}`}>{session.status.replace('_', ' ')}</span>

        <div className="flex-1" />

        <button
          onClick={toggleAutoApprove}
          title={
            session.autoApprove
              ? 'Tool calls run without asking. Click to arm the approval gate.'
              : 'Tool calls pause for approval. Click to let this session run unattended.'
          }
          className={`flex items-center gap-1.5 border px-2 py-1 text-[10px] uppercase tracking-wider transition-colors ${
            session.autoApprove
              ? 'border-amber/45 bg-amber/10 text-amber'
              : 'border-rule text-ink-dim hover:border-rule-hot hover:text-ink'
          }`}
        >
          {session.autoApprove ? <Zap size={11} /> : <ZapOff size={11} />}
          {session.autoApprove ? 'auto-approve' : 'gated'}
        </button>

        {running && !adopted && (
          <button
            onClick={stop}
            className="flex items-center gap-1.5 border border-rule px-2 py-1 text-[10px] uppercase tracking-wider text-ink-dim transition-colors hover:border-coral/50 hover:text-coral"
          >
            <Square size={10} />
            stop
          </button>
        )}
      </div>

      <div className="flex items-end justify-between gap-6 px-5 pb-0">
        <div className="flex gap-5 pb-2.5">
          <Stat label="mode" value={session.permissionMode} />
          <Stat label="tools" value={toolCalls} />
          <Stat label="turns" value={session.numTurns ?? '—'} />
          <Stat label="elapsed" value={duration(session.durationMs)} />
          <Stat label="cost" value={cost(session.totalCostUsd)} />
        </div>

        {/* View switcher sits on the rule, tab-style */}
        <div className="flex gap-px">
          {VIEWS.map(({ id, label, Icon }) => (
            <button
              key={id}
              onClick={() => setView(id)}
              className={`flex items-center gap-1.5 border border-b-0 px-3 py-1.5 text-[10px] uppercase tracking-widest transition-colors ${
                view === id
                  ? 'border-rule bg-void text-live'
                  : 'border-transparent text-ink-faint hover:text-ink-dim'
              }`}
              style={view === id ? { marginBottom: '-1px' } : undefined}
            >
              <Icon size={11} />
              {label}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="label">{label}</span>
      <span className="tabular text-[12px] text-ink">{value}</span>
    </div>
  )
}
