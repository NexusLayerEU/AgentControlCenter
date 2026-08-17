import { useEffect, useMemo, useRef } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ChevronRight } from 'lucide-react'

import { useStore } from '../lib/store'
import { useTimeline, useVisibleTimeline } from '../lib/useTimeline'
import { clock, duration, parsePayload, relativise } from '../lib/format'
import { RISK_TONE, TONE_BORDER, TONE_TEXT, iconFor, styleFor } from '../lib/glyphs'

/**
 * The primary view: a vertical spine of agent actions, with tool results
 * nested under the call that produced them.
 */
export default function FlowTree() {
  const selectedId = useStore((s) => s.selectedId)
  const events = useVisibleTimeline()
  const allEvents = useTimeline()
  const resetFilters = useStore((s) => s.resetFilters)
  const inspectId = useStore((s) => s.inspectId)
  const setInspect = useStore((s) => s.setInspect)
  const cwd = useStore((s) => s.sessions.find((x) => x.id === s.selectedId)?.cwd)
  const bottomRef = useRef(null)
  const scrollRef = useRef(null)
  const pinnedRef = useRef(true)

  const tree = useMemo(() => buildTree(events), [events])

  // Follow the tail only while the user is already at the bottom, so reading
  // back through history is never yanked away by an incoming event.
  useEffect(() => {
    const container = scrollRef.current
    if (!container) return
    const onScroll = () => {
      const distance = container.scrollHeight - container.scrollTop - container.clientHeight
      pinnedRef.current = distance < 120
    }
    container.addEventListener('scroll', onScroll)
    return () => container.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => {
    if (pinnedRef.current) bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [events.length])

  if (events.length === 0) {
    // Distinguish "nothing has happened" from "you filtered it all away" —
    // otherwise an over-filtered session reads as a broken one.
    const filteredAway = allEvents.length > 0
    return (
      <div className="flex h-full items-center justify-center">
        <div className="text-center">
          <div className="mx-auto mb-3 h-8 w-px bg-gradient-to-b from-transparent via-live to-transparent" />
          <p className="label">{filteredAway ? 'everything is filtered out' : 'awaiting first signal'}</p>
          {filteredAway && (
            <button
              onClick={resetFilters}
              className="mt-3 border border-amber/40 bg-amber/10 px-3 py-1 text-[11px] text-amber transition-colors hover:bg-amber/20"
            >
              show all {allEvents.length} events
            </button>
          )}
        </div>
      </div>
    )
  }

  return (
    <div ref={scrollRef} className="h-full overflow-y-auto px-6 py-5" key={selectedId}>
      <div className="relative max-w-6xl">
        {/* The spine */}
        <div className="absolute bottom-2 left-[15px] top-2 w-px bg-gradient-to-b from-rule via-rule to-transparent" />

        <AnimatePresence initial={false}>
          {tree.map((node, index) => (
            <TreeNode
              key={node.id}
              node={node}
              index={index}
              inspectId={inspectId}
              onInspect={setInspect}
              cwd={cwd}
            />
          ))}
        </AnimatePresence>
        <div ref={bottomRef} className="h-4" />
      </div>
    </div>
  )
}

/** Attaches TOOL_RESULT nodes as children of their TOOL_CALL. */
function buildTree(events) {
  const byId = new Map(events.map((e) => [e.id, { ...e, children: [] }]))
  const roots = []
  byId.forEach((node) => {
    const parent = node.parentId ? byId.get(node.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
}

function TreeNode({ node, index, inspectId, onInspect, cwd }) {
  const style = styleFor(node)
  const Icon = iconFor(node)
  const payload = parsePayload(node.payload)
  const running = node.status === 'running'
  const errored = node.status === 'error' || node.type === 'ERROR'
  const tone = errored ? 'coral' : node.type === 'TOOL_CALL' ? riskTone(payload) : style.tone
  const selected = inspectId === node.id

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.22, delay: Math.min(index * 0.012, 0.25) }}
      className="relative pl-10"
    >
      {/* Node marker sitting on the spine */}
      <div
        className={`tone-edge absolute left-[8px] top-[9px] flex h-4 w-4 items-center justify-center border bg-void ${
          TONE_BORDER[tone]
        } ${TONE_TEXT[tone]} ${running ? 'pulse-amber' : ''}`}
      >
        <Icon size={9} className={TONE_TEXT[tone]} strokeWidth={2.2} />
      </div>

      <button
        onClick={() => onInspect(selected ? null : node.id)}
        className={`mb-1.5 block w-full border px-3 py-2 text-left transition-all ${
          selected
            ? 'border-rule-hot bg-deck-3'
            : 'border-transparent hover:border-rule hover:bg-deck-2'
        }`}
      >
        <div className="flex items-baseline gap-2.5">
          <span className={`label shrink-0 ${TONE_TEXT[tone]}`}>
            {node.type === 'TOOL_CALL' ? node.toolName : style.label}
          </span>

          <span
            className={`min-w-0 flex-1 truncate text-[12px] ${
              node.type === 'THINKING' ? 'italic text-ink-dim' : 'text-ink'
            }`}
          >
            {node.title || '—'}
          </span>

          {node.durationMs != null && (
            <span className="tabular shrink-0 text-[10px] text-ink-faint">
              {duration(node.durationMs)}
            </span>
          )}
          <span className="tabular shrink-0 text-[10px] text-ink-faint/60">{clock(node.ts)}</span>
          <ChevronRight
            size={11}
            className={`shrink-0 text-ink-faint transition-transform ${selected ? 'rotate-90' : ''}`}
          />
        </div>

        {/* Body preview, but only when the title actually truncated something —
            otherwise the same sentence would appear twice. */}
        {(node.type === 'ASSISTANT_TEXT' || node.type === 'THINKING') &&
          payload.text &&
          isTruncated(node.title, payload.text) && (
            <p className="mt-1.5 line-clamp-3 whitespace-pre-wrap text-[11px] leading-relaxed text-ink-dim">
              {payload.text}
            </p>
          )}

        {node.type === 'TOOL_CALL' && payload.input?.command && (
          <pre className="mt-1.5 overflow-x-auto border-l border-rule-hot bg-void/60 px-2.5 py-1.5 text-[11px] text-cyan">
            {relativise(payload.input.command, cwd)}
          </pre>
        )}
      </button>

      {/* Children (tool results) */}
      {node.children?.length > 0 && (
        <div className="relative mb-2 ml-4 border-l border-rule pl-4">
          {node.children.map((child) => (
            <ChildNode key={child.id} node={child} cwd={cwd} />
          ))}
        </div>
      )}
    </motion.div>
  )
}

function ChildNode({ node, cwd }) {
  const payload = parsePayload(node.payload)
  const failed = payload.isError
  const output = relativise((payload.output || '').trim(), cwd)

  return (
    <div className="relative py-1">
      <span className="absolute -left-4 top-[13px] h-px w-3 bg-rule" />
      <div className="flex items-baseline gap-2">
        <span className={`label ${failed ? 'text-coral' : 'text-ink-faint'}`}>
          {failed ? 'error' : 'result'}
        </span>
        {node.durationMs != null && (
          <span className="tabular text-[10px] text-ink-faint">{duration(node.durationMs)}</span>
        )}
      </div>
      {output && (
        <pre
          className={`mt-1 max-h-28 overflow-hidden whitespace-pre-wrap text-[11px] leading-relaxed ${
            failed ? 'text-coral/80' : 'text-ink-dim'
          }`}
        >
          {output.length > 400 ? `${output.slice(0, 400)}…` : output}
        </pre>
      )}
    </div>
  )
}

function riskTone(payload) {
  return RISK_TONE[payload.risk] ?? 'cyan'
}

/** True when the one-line title left content unseen, so a preview earns its space. */
function isTruncated(title, text) {
  if (!title) return true
  const collapsed = text.replace(/\s+/g, ' ').trim()
  return collapsed !== title.trim()
}
