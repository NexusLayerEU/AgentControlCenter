import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Check, ShieldAlert, X } from 'lucide-react'

import { useStore } from '../lib/store'
import { parsePayload } from '../lib/format'
import { RISK_TONE, TONE_BORDER, TONE_TEXT } from '../lib/glyphs'

/**
 * Bottom dock holding every tool call currently blocked on a human. It only
 * exists when something is actually waiting, so an unattended run never shows it.
 */
export default function ApprovalDock() {
  const approvals = useStore((s) => s.approvals)

  return (
    <AnimatePresence>
      {approvals.length > 0 && (
        <motion.div
          initial={{ y: 140, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: 140, opacity: 0 }}
          transition={{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
          className="shrink-0 border-t border-coral/35 bg-deck"
        >
          <div className="flex items-center gap-2 border-b border-rule px-5 py-1.5">
            <ShieldAlert size={12} className="text-coral" />
            <span className="label text-coral">approval gate</span>
            <span className="tabular text-[10px] text-ink-faint">
              {approvals.length} held
            </span>
          </div>

          <div className="flex max-h-[35vh] gap-3 overflow-x-auto px-5 py-3">
            {approvals.map((approval) => (
              <ApprovalCard key={approval.id} approval={approval} />
            ))}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

function ApprovalCard({ approval }) {
  const decide = useStore((s) => s.decide)
  const timeout = useStore((s) => s.system?.approvalTimeoutSeconds ?? 50)
  const [busy, setBusy] = useState(false)
  const [remaining, setRemaining] = useState(() => secondsLeft(approval.createdAt, timeout))

  // The agent's hook is genuinely blocked for a bounded window; showing the
  // real countdown is the difference between an informed click and a guess.
  useEffect(() => {
    const tick = setInterval(() => setRemaining(secondsLeft(approval.createdAt, timeout)), 500)
    return () => clearInterval(tick)
  }, [approval.createdAt, timeout])

  const tone = RISK_TONE[approval.risk] ?? 'cyan'
  const input = parsePayload(approval.toolInput)
  const urgent = remaining <= 12

  async function act(allow) {
    setBusy(true)
    try {
      await decide(approval.id, allow, allow ? 'approved in ACC' : 'denied in ACC')
    } finally {
      setBusy(false)
    }
  }

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.97 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.97 }}
      className={`ticked deck flex w-[420px] shrink-0 flex-col ${TONE_BORDER[tone]}`}
    >
      <div className="flex items-center gap-2 border-b border-rule px-3 py-1.5">
        <span className={`label ${TONE_TEXT[tone]}`}>{approval.toolName}</span>
        <span className={`label ${TONE_TEXT[tone]}`}>· {approval.risk}</span>
        <span
          className={`tabular ml-auto text-[11px] ${urgent ? 'text-coral' : 'text-ink-faint'}`}
        >
          {remaining > 0 ? `${remaining}s to decide` : 'expired'}
        </span>
      </div>

      {/* Countdown bar — the agent is blocked for exactly this long */}
      <div className="h-[2px] w-full bg-rule">
        <motion.div
          className={urgent ? 'h-full bg-coral' : 'h-full bg-amber'}
          animate={{ width: `${Math.max(0, (remaining / timeout) * 100)}%` }}
          transition={{ duration: 0.5, ease: 'linear' }}
        />
      </div>

      <div className="min-h-[64px] flex-1 px-3 py-2">
        <pre className="max-h-24 overflow-auto whitespace-pre-wrap break-all text-[11px] leading-relaxed text-ink">
          {summarise(approval.toolName, input)}
        </pre>
      </div>

      <div className="flex gap-px border-t border-rule">
        <button
          onClick={() => act(true)}
          disabled={busy || remaining <= 0}
          className="flex flex-1 items-center justify-center gap-1.5 py-2 text-[11px] uppercase tracking-widest text-live transition-colors hover:bg-live/15 disabled:opacity-35"
        >
          <Check size={12} />
          approve
        </button>
        <div className="w-px bg-rule" />
        <button
          onClick={() => act(false)}
          disabled={busy || remaining <= 0}
          className="flex flex-1 items-center justify-center gap-1.5 py-2 text-[11px] uppercase tracking-widest text-coral transition-colors hover:bg-coral/15 disabled:opacity-35"
        >
          <X size={12} />
          deny
        </button>
      </div>
    </motion.div>
  )
}

function secondsLeft(createdAt, timeout) {
  return Math.max(0, Math.ceil(timeout - (Date.now() - createdAt) / 1000))
}

function summarise(toolName, input) {
  if (!input || typeof input !== 'object') return String(input ?? '')
  if (input.command) return input.command
  if (input.file_path) return `${input.file_path}\n\n${(input.new_string ?? input.content ?? '').slice(0, 500)}`
  return JSON.stringify(input, null, 2).slice(0, 800)
}
