import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import {
  Activity,
  ArrowRight,
  CircleDollarSign,
  Hammer,
  Plus,
  RefreshCw,
  ShieldAlert,
  Zap,
} from 'lucide-react'

import { useStore } from '../lib/store'
import { api } from '../lib/api'
import { ago, cost, duration } from '../lib/format'
import { STATUS_TONE, TONE_TEXT } from '../lib/glyphs'
import StatTile from './charts/StatTile'
import Bars from './charts/Bars'
import RankedBars from './charts/RankedBars'
import StackBar from './charts/StackBar'

export default function Overview() {
  const sessions = useStore((s) => s.sessions)
  const approvals = useStore((s) => s.approvals)
  const openControl = useStore((s) => s.openControl)
  const setComposerOpen = useStore((s) => s.setComposerOpen)
  const [stats, setStats] = useState(null)
  const [error, setError] = useState(null)

  // Session/approval deltas already arrive over the websocket; the aggregates
  // are re-pulled alongside them rather than recomputed in the browser.
  useEffect(() => {
    let alive = true
    const load = () =>
      api
        .stats()
        .then((data) => alive && setStats(data))
        .catch((e) => alive && setError(e.message))
    load()
    return () => {
      alive = false
    }
  }, [sessions.length, approvals.length])

  if (error) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="text-[12px] text-coral">{error}</p>
      </div>
    )
  }

  if (!stats) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="label">reading telemetry…</p>
      </div>
    )
  }

  const { sessions: counts, totals, activity, tools, risk, approvals: gate, modes } = stats
  const recent = sessions.slice(0, 6)
  const errorRate = totals.toolCalls ? (totals.toolErrors / totals.toolCalls) * 100 : 0

  return (
    <div className="h-full overflow-y-auto px-6 py-6">
      <div className="mx-auto max-w-[1500px]">
        <Masthead counts={counts} onOpen={openControl} onDispatch={() => setComposerOpen(true)} />

        {/* ── Headline figures ─────────────────────────────────────── */}
        <div className="mt-5 grid grid-cols-2 gap-3 lg:grid-cols-5">
          <StatTile
            index={0}
            label="active"
            value={counts.active}
            tone={counts.active > 0 ? 'live' : 'ink-dim'}
            live={counts.active > 0}
            Icon={Activity}
            note={
              counts.waitingApproval > 0
                ? `${counts.waitingApproval} waiting on you`
                : counts.active > 0
                  ? 'agents running now'
                  : 'nothing running'
            }
          />
          <StatTile
            index={1}
            label="history"
            value={counts.history}
            tone="ink"
            Icon={RefreshCw}
            note={`${counts.completed} ok · ${counts.failed} failed · ${counts.cancelled} cancelled`}
          />
          <StatTile
            index={2}
            label="tool calls"
            value={totals.toolCalls}
            tone="cyan"
            Icon={Hammer}
            note={
              totals.toolErrors > 0
                ? `${totals.toolErrors} failed · ${errorRate.toFixed(1)}%`
                : 'no failures'
            }
          />
          <StatTile
            index={3}
            label="spend"
            value={cost(totals.costUsd)}
            tone="amber"
            Icon={CircleDollarSign}
            note={`${totals.turns} turns · avg ${duration(totals.avgSessionMs)}`}
          />
          <StatTile
            index={4}
            label="held now"
            value={gate.pending}
            tone={gate.pending > 0 ? 'coral' : 'ink-dim'}
            live={gate.pending > 0}
            Icon={ShieldAlert}
            note={gate.pending > 0 ? 'agents are blocked' : `${gate.approved} approved all-time`}
          />
        </div>

        {/* ── Charts ───────────────────────────────────────────────── */}
        <div className="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-3">
          <Panel
            title="sessions dispatched"
            note="last 14 days"
            className="xl:col-span-2"
            delay={0.1}
          >
            <Bars
              data={activity}
              xKey="day"
              yKey="sessions"
              tone="live"
              label="Sessions dispatched per day over the last 14 days"
              formatX={shortDay}
              formatValue={(v) => `${v} session${v === 1 ? '' : 's'}`}
            />
          </Panel>

          <Panel title="outcomes" note="every run" delay={0.15}>
            <StackBar
              total={counts.total}
              empty="no sessions yet"
              segments={[
                { label: 'completed', value: counts.completed, tone: 'live' },
                { label: 'failed', value: counts.failed, tone: 'coral' },
                { label: 'cancelled', value: counts.cancelled, tone: 'ink-faint' },
                { label: 'running', value: counts.running + counts.starting, tone: 'cyan' },
                { label: 'waiting', value: counts.waitingApproval, tone: 'amber' },
              ]}
            />
            <div className="mt-5">
              <p className="label mb-2">how they were launched</p>
              <StackBar
                empty="no sessions yet"
                segments={[
                  { label: 'gated', value: modes.gated, tone: 'live' },
                  { label: 'unattended', value: modes.unattended, tone: 'amber' },
                ]}
              />
            </div>
          </Panel>

          <Panel title="tool calls" note="last 14 days" delay={0.2}>
            <Bars
              data={activity}
              xKey="day"
              yKey="toolCalls"
              tone="cyan"
              label="Tool calls per day over the last 14 days"
              formatX={shortDay}
              formatValue={(v) => `${v} call${v === 1 ? '' : 's'}`}
            />
          </Panel>

          <Panel title="most-used tools" note="failures in magenta" delay={0.25}>
            <RankedBars
              data={tools}
              nameKey="name"
              valueKey="calls"
              errorKey="errors"
              tone="cyan"
            />
          </Panel>

          <Panel title="risk profile" note="of every tool call" delay={0.3}>
            <StackBar
              empty="no tool calls yet"
              segments={[
                { label: 'safe', value: risk.safe, tone: 'ink-faint' },
                { label: 'normal', value: risk.normal, tone: 'cyan' },
                { label: 'elevated', value: risk.elevated, tone: 'amber' },
                { label: 'destructive', value: risk.destructive, tone: 'coral' },
              ]}
            />
            <div className="mt-5">
              <p className="label mb-2">approval gate</p>
              <StackBar
                empty="the gate has not fired yet — run acc attach"
                segments={[
                  { label: 'auto', value: gate.autoApproved, tone: 'ink-faint' },
                  { label: 'approved', value: gate.approved, tone: 'live' },
                  { label: 'denied', value: gate.denied, tone: 'coral' },
                  { label: 'timed out', value: gate.timedOut, tone: 'amber' },
                ]}
              />
            </div>
          </Panel>
        </div>

        <RecentStrip sessions={recent} onOpen={openControl} />
      </div>
    </div>
  )
}

/* ── Masthead: the one thing that must be impossible to miss ────────────── */

function Masthead({ counts, onOpen, onDispatch }) {
  return (
    <motion.section
      initial={{ opacity: 0, y: -6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
      className="ticked deck flex flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center"
    >
      <div className="min-w-0 flex-1">
        <p className="label">agent control center</p>
        <h1 className="stencil mt-1.5 text-[26px] leading-none text-ink">Overview</h1>
        <p className="mt-2 max-w-lg text-[11px] leading-relaxed text-ink-dim">
          {counts.active > 0
            ? `${counts.active} agent${counts.active === 1 ? '' : 's'} running right now. Open the control center to watch the activity tree live.`
            : 'Every plan, tool call and file write your agents make, as a live tree and call graph.'}
        </p>
      </div>

      <div className="flex shrink-0 items-center gap-2">
        <button
          onClick={onDispatch}
          className="flex items-center gap-2 border border-rule px-4 py-3 text-[11px] text-ink-dim transition-colors hover:border-rule-hot hover:text-ink"
        >
          <Plus size={13} />
          dispatch agent
          <kbd className="ml-1 border border-rule px-1 text-[9px]">⌘K</kbd>
        </button>

        {/* The primary action on the page: big target, accent fill, arrow. */}
        <button
          onClick={onOpen}
          className="group relative flex items-center gap-3 border border-live bg-live/15 px-6 py-3 text-live transition-colors hover:bg-live/25"
        >
          {counts.active > 0 && (
            <span className="pulse-live absolute -top-1 -right-1 h-2 w-2 rounded-full bg-live" />
          )}
          <Zap size={15} />
          <span className="stencil text-[13px]">open control center</span>
          <ArrowRight
            size={15}
            className="transition-transform duration-200 group-hover:translate-x-1"
          />
        </button>
      </div>
    </motion.section>
  )
}

function Panel({ title, note, children, className = '', delay = 0 }) {
  return (
    <motion.section
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, delay, ease: [0.16, 1, 0.3, 1] }}
      className={`ticked deck flex flex-col px-4 py-3 ${className}`}
    >
      <header className="mb-3 flex items-baseline gap-2">
        <h2 className="label text-ink-dim">{title}</h2>
        <span className="h-px flex-1 bg-rule" />
        {note && <span className="text-[9px] text-ink-faint">{note}</span>}
      </header>
      <div className="flex-1">{children}</div>
    </motion.section>
  )
}

function RecentStrip({ sessions, onOpen }) {
  if (sessions.length === 0) return null

  return (
    <motion.section
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ delay: 0.35 }}
      className="mt-3"
    >
      <header className="mb-2 flex items-baseline gap-2">
        <h2 className="label">recent sessions</h2>
        <span className="h-px flex-1 bg-rule" />
      </header>

      <div className="grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-3">
        {sessions.map((session) => {
          const tone = STATUS_TONE[session.status] ?? 'ink-dim'
          return (
            <button
              key={session.id}
              onClick={() => onOpen(session.id)}
              className="deck group flex items-center gap-3 px-3 py-2.5 text-left transition-colors hover:border-rule-hot"
            >
              <span
                className={`h-1.5 w-1.5 shrink-0 rounded-full bg-current ${TONE_TEXT[tone]}`}
              />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[12px] text-ink">{session.name}</span>
                <span className={`label ${TONE_TEXT[tone]}`}>
                  {session.status.toLowerCase().replace('_', ' ')}
                </span>
              </span>
              <span className="tabular shrink-0 text-right text-[10px] text-ink-faint">
                {ago(session.createdAt)}
                <br />
                {cost(session.totalCostUsd)}
              </span>
              <ArrowRight
                size={12}
                className="shrink-0 text-ink-faint transition-transform group-hover:translate-x-0.5"
              />
            </button>
          )
        })}
      </div>
    </motion.section>
  )
}

function shortDay(iso) {
  if (!iso) return ''
  const date = new Date(`${iso}T00:00:00`)
  return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}
