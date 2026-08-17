import { useMemo } from 'react'
import {
  Background,
  BackgroundVariant,
  Controls,
  Handle,
  MiniMap,
  Position,
  ReactFlow,
  ReactFlowProvider,
} from '@xyflow/react'

import { useStore } from '../lib/store'
import { useTimeline, useVisibleTimeline } from '../lib/useTimeline'
import { duration, parsePayload } from '../lib/format'
import { RISK_TONE, iconFor, styleFor } from '../lib/glyphs'
import { usePalette } from '../lib/tones'

const NODE_WIDTH = 230
const NODE_HEIGHT = 58
const ROW_HEIGHT = 92
const LANE_X = { turn: 0, tool: 300, result: 600 }

/**
 * Spatial view of the same timeline: turns run down the left lane, the tools
 * each turn invoked branch right, and results terminate the chain.
 */
export default function CallGraph() {
  return (
    <ReactFlowProvider>
      <GraphCanvas />
    </ReactFlowProvider>
  )
}

function GraphCanvas() {
  const events = useVisibleTimeline()
  const allEvents = useTimeline()
  const setInspect = useStore((s) => s.setInspect)
  const { tones, surfaces } = usePalette()

  const { nodes, edges } = useMemo(() => layout(events, tones), [events, tones])

  if (nodes.length === 0) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="label">
          {allEvents.length > 0 ? 'everything is filtered out' : 'no calls to plot yet'}
        </p>
      </div>
    )
  }

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={NODE_TYPES}
      fitView
      fitViewOptions={{ padding: 0.22, maxZoom: 1 }}
      minZoom={0.2}
      maxZoom={1.6}
      proOptions={{ hideAttribution: true }}
      onNodeClick={(_, node) => setInspect(node.id)}
      nodesDraggable={false}
      nodesConnectable={false}
    >
      <Background variant={BackgroundVariant.Dots} gap={26} size={1} color={surfaces.rule} />
      <Controls showInteractive={false} position="bottom-right" />
      <MiniMap
        pannable
        zoomable
        position="bottom-left"
        maskColor="rgb(0 0 0 / 0.8)"
        style={{
          background: surfaces.deck,
          border: `1px solid ${surfaces.rule}`,
          width: 150,
          height: 96,
        }}
        nodeColor={(node) => node.data.hex}
      />
    </ReactFlow>
  )
}

/**
 * Three-lane layout. Turn nodes anchor each block of activity; every tool call
 * after a turn hangs off it until the next turn starts.
 */
function layout(events, tones) {
  const nodes = []
  const edges = []
  const byToolUse = new Map()

  let row = 0
  let currentTurn = null

  const isTurn = (e) =>
    e.type === 'USER_PROMPT' ||
    e.type === 'ASSISTANT_TEXT' ||
    e.type === 'THINKING' ||
    e.type === 'SESSION_END'

  for (const event of events) {
    if (isTurn(event)) {
      const style = styleFor(event)
      nodes.push(makeNode(event, LANE_X.turn, row * ROW_HEIGHT, style.tone, style.label, tones))
      if (currentTurn) edges.push(makeEdge(currentTurn, event.id, tones['ink-faint'], false))
      currentTurn = event.id
      row += 1
      continue
    }

    if (event.type === 'TOOL_CALL') {
      const payload = parsePayload(event.payload)
      const tone = event.status === 'error' ? 'coral' : (RISK_TONE[payload.risk] ?? 'cyan')
      nodes.push(makeNode(event, LANE_X.tool, row * ROW_HEIGHT, tone, event.toolName, tones))
      byToolUse.set(event.toolUseId, { id: event.id, row, tone })
      if (currentTurn) {
        edges.push(makeEdge(currentTurn, event.id, tones[tone], event.status === 'running'))
      }
      row += 1
      continue
    }

    if (event.type === 'TOOL_RESULT') {
      const parent = byToolUse.get(event.toolUseId)
      const payload = parsePayload(event.payload)
      const tone = payload.isError ? 'coral' : 'ink-dim'
      const y = (parent ? parent.row : row) * ROW_HEIGHT
      nodes.push(makeNode(event, LANE_X.result, y, tone, payload.isError ? 'error' : 'result', tones))
      if (parent) edges.push(makeEdge(parent.id, event.id, tones[tone], false))
      if (!parent) row += 1
      continue
    }

    if (event.type === 'APPROVAL_REQUEST' || event.type === 'ERROR') {
      const style = styleFor(event)
      nodes.push(makeNode(event, LANE_X.tool, row * ROW_HEIGHT, style.tone, style.label, tones))
      if (currentTurn) edges.push(makeEdge(currentTurn, event.id, tones[style.tone], true))
      row += 1
    }
  }

  return { nodes, edges }
}

function makeNode(event, x, y, tone, kicker, tones) {
  return {
    id: event.id,
    position: { x, y },
    type: 'call',
    // Explicit dimensions: without them the minimap cannot compute bounds and
    // renders an empty box.
    width: NODE_WIDTH,
    height: NODE_HEIGHT,
    data: {
      event,
      tone,
      kicker,
      hex: tones[tone],
    },
  }
}

function makeEdge(source, target, color, animated) {
  return {
    id: `${source}->${target}`,
    source,
    target,
    type: 'smoothstep',
    animated: false,
    className: animated ? 'in-flight' : '',
    style: { stroke: color, opacity: animated ? 0.95 : 0.5 },
  }
}

function CallNode({ data }) {
  const { event, tone, kicker, hex } = data
  const Icon = iconFor(event)
  const running = event.status === 'running'

  return (
    <div
      className="ticked deck w-[230px] px-2.5 py-2 transition-shadow"
      style={{
        borderColor: hex,
        boxShadow: running ? `0 0 22px -6px ${hex}` : 'none',
      }}
    >
      <Handle type="target" position={Position.Left} style={HANDLE} />
      <div className="flex items-center gap-1.5">
        <Icon size={10} style={{ color: hex }} strokeWidth={2.2} />
        <span className="label" style={{ color: hex }}>
          {kicker}
        </span>
        {running && (
          <span
            className="pulse-amber ml-auto h-1.5 w-1.5 rounded-full"
            style={{ background: hex }}
          />
        )}
        {event.durationMs != null && (
          <span className="tabular ml-auto text-[9px] text-ink-faint">
            {duration(event.durationMs)}
          </span>
        )}
      </div>
      <p className="mt-1 line-clamp-2 text-[11px] leading-snug text-ink">{event.title || '—'}</p>
      <Handle type="source" position={Position.Right} style={HANDLE} />
    </div>
  )
}

const HANDLE = { width: 5, height: 5, background: 'var(--color-rule-hot)', border: 'none', borderRadius: 0 }
const NODE_TYPES = { call: CallNode }
