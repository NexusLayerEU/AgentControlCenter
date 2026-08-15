import {
  AlertTriangle,
  Brain,
  CheckCircle2,
  CircleDot,
  Cpu,
  FileCode2,
  FilePen,
  Flag,
  Globe,
  Hammer,
  MessageSquare,
  Search,
  ShieldQuestion,
  Sparkles,
  SquareTerminal,
  Webhook,
} from 'lucide-react'

/** Per-event-type colour + icon. One table so every view stays consistent. */
export const EVENT_STYLE = {
  SESSION_START: { tone: 'ink-dim', Icon: Flag, label: 'start' },
  USER_PROMPT: { tone: 'ink', Icon: MessageSquare, label: 'prompt' },
  ASSISTANT_TEXT: { tone: 'ink', Icon: Sparkles, label: 'says' },
  THINKING: { tone: 'violet', Icon: Brain, label: 'thinks' },
  TOOL_CALL: { tone: 'cyan', Icon: Hammer, label: 'calls' },
  TOOL_RESULT: { tone: 'ink-dim', Icon: CheckCircle2, label: 'returns' },
  APPROVAL_REQUEST: { tone: 'amber', Icon: ShieldQuestion, label: 'asks' },
  APPROVAL_DECISION: { tone: 'live', Icon: CheckCircle2, label: 'decided' },
  HOOK: { tone: 'ink-faint', Icon: Webhook, label: 'hook' },
  SYSTEM: { tone: 'ink-faint', Icon: Cpu, label: 'system' },
  ERROR: { tone: 'coral', Icon: AlertTriangle, label: 'error' },
  SESSION_END: { tone: 'live', Icon: Flag, label: 'end' },
}

/** Tool-specific icon, falling back to the generic hammer. */
export const TOOL_ICON = {
  Bash: SquareTerminal,
  Read: FileCode2,
  Write: FilePen,
  Edit: FilePen,
  Glob: Search,
  Grep: Search,
  WebFetch: Globe,
  WebSearch: Globe,
  Task: Cpu,
  Agent: Cpu,
  TodoWrite: CircleDot,
}

export function styleFor(event) {
  return EVENT_STYLE[event.type] ?? EVENT_STYLE.SYSTEM
}

export function iconFor(event) {
  if (event.type === 'TOOL_CALL' && TOOL_ICON[event.toolName]) return TOOL_ICON[event.toolName]
  return styleFor(event).Icon
}

/** Tailwind class fragments keyed by tone, since tone is dynamic. */
export const TONE_TEXT = {
  ink: 'text-ink',
  'ink-dim': 'text-ink-dim',
  'ink-faint': 'text-ink-faint',
  live: 'text-live',
  amber: 'text-amber',
  coral: 'text-coral',
  cyan: 'text-cyan',
  violet: 'text-violet',
}

export const TONE_BORDER = {
  ink: 'border-rule-hot',
  'ink-dim': 'border-rule',
  'ink-faint': 'border-rule',
  live: 'border-live/40',
  amber: 'border-amber/45',
  coral: 'border-coral/50',
  cyan: 'border-cyan/40',
  violet: 'border-violet/40',
}

export const RISK_TONE = {
  safe: 'ink-dim',
  normal: 'cyan',
  elevated: 'amber',
  destructive: 'coral',
}

export const STATUS_TONE = {
  STARTING: 'amber',
  RUNNING: 'live',
  WAITING_APPROVAL: 'amber',
  IDLE: 'cyan',
  COMPLETED: 'ink-dim',
  FAILED: 'coral',
  CANCELLED: 'ink-faint',
}
