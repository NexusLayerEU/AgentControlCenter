import { useState } from 'react'
import { LayoutDashboard, Plug, PlugZap, Plus, ShieldCheck, ShieldAlert, Terminal, Zap } from 'lucide-react'

import { useStore } from '../lib/store'
import { api } from '../lib/api'
import { shortPath } from '../lib/format'
import ThemeSwitch from './ThemeSwitch'

export default function TopBar() {
  const connected = useStore((s) => s.connected)
  const system = useStore((s) => s.system)
  const hooks = useStore((s) => s.hooks)
  const pending = useStore((s) => s.approvals.length)
  const setComposerOpen = useStore((s) => s.setComposerOpen)
  const page = useStore((s) => s.page)
  const showOverview = useStore((s) => s.showOverview)
  const openControl = useStore((s) => s.openControl)
  const refreshHooks = useStore((s) => s.refreshHooks)
  const notify = useStore((s) => s.notify)
  const [busy, setBusy] = useState(false)

  const attached = hooks?.global || hooks?.project

  async function toggleHooks() {
    setBusy(true)
    try {
      if (attached) {
        await api.uninstallHooks({ projectScope: false })
        notify('ACC detached from Claude Code', 'warn')
      } else {
        const result = await api.installHooks({ projectScope: false })
        notify(`Attached — ${result.installed.length} hooks registered`, 'ok')
      }
      await refreshHooks()
    } catch (error) {
      notify(error.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <header className="flex h-12 shrink-0 items-center gap-4 border-b border-rule bg-deck px-4">
      {/* Wordmark doubles as the way home. In Blackwire the glitch layers key
          off data-text; in DevTheme they never render. */}
      <button
        onClick={showOverview}
        title="Back to the overview"
        className="flex items-baseline gap-2.5"
      >
        <span className="stencil glitch text-[15px] leading-none text-live" data-text="ACC">
          ACC
        </span>
        <span className="label hidden sm:block">Agent Control Center</span>
      </button>

      {/* Primary navigation — two destinations, both always visible. */}
      <nav className="flex gap-px border border-rule">
        <NavTab
          active={page === 'overview'}
          onClick={showOverview}
          Icon={LayoutDashboard}
          label="overview"
        />
        <NavTab
          active={page === 'control'}
          onClick={() => openControl()}
          Icon={Zap}
          label="control"
        />
      </nav>

      <div className="h-5 w-px bg-rule" />

      <Readout
        icon={connected ? PlugZap : Plug}
        tone={connected ? 'live' : 'coral'}
        label="daemon"
        value={connected ? `:${system?.port ?? 4000}` : 'offline'}
        pulse={connected}
      />

      <Readout
        icon={system?.claude?.available ? Terminal : Terminal}
        tone={system?.claude?.available ? 'ink-dim' : 'coral'}
        label="claude"
        value={
          system?.claude?.available
            ? (system.claude.version || 'ready').split(' ')[0]
            : 'not found'
        }
      />

      {system?.cwd && (
        <Readout
          icon={null}
          tone="ink-faint"
          label="cwd"
          value={shortPath(system.cwd)}
          className="hidden lg:flex"
        />
      )}

      <div className="flex-1" />

      {pending > 0 && (
        <div className="pulse-coral flex items-center gap-2 border border-coral/50 bg-coral/10 px-2.5 py-1">
          <ShieldAlert size={13} className="text-coral" />
          <span className="tabular text-[11px] font-medium text-coral">
            {pending} awaiting approval
          </span>
        </div>
      )}

      <ThemeSwitch />

      <button
        onClick={toggleHooks}
        disabled={busy}
        title={
          attached
            ? 'Remove ACC hooks from ~/.claude/settings.json'
            : 'Register ACC hooks in ~/.claude/settings.json'
        }
        className={`flex items-center gap-2 border px-2.5 py-1 text-[11px] transition-colors ${
          attached
            ? 'border-live/40 bg-live/10 text-live hover:bg-live/20'
            : 'border-rule text-ink-dim hover:border-rule-hot hover:text-ink'
        }`}
      >
        {attached ? <ShieldCheck size={13} /> : <ShieldAlert size={13} />}
        {attached ? 'attached' : 'attach'}
      </button>

      <button
        onClick={() => setComposerOpen(true)}
        className="flex items-center gap-2 border border-live/50 bg-live/15 px-3 py-1 text-[11px] font-medium text-live transition-colors hover:bg-live/25"
      >
        <Plus size={13} />
        dispatch
        <kbd className="ml-1 border border-live/30 px-1 text-[9px] opacity-70">⌘K</kbd>
      </button>
    </header>
  )
}

function NavTab({ active, onClick, Icon, label }) {
  return (
    <button
      onClick={onClick}
      aria-current={active ? 'page' : undefined}
      className={`flex items-center gap-1.5 px-2.5 py-1 text-[10px] uppercase tracking-widest transition-colors ${
        active ? 'border border-live/45 bg-live/10 text-live' : 'text-ink-faint hover:text-ink-dim'
      }`}
    >
      <Icon size={11} />
      {label}
    </button>
  )
}

function Readout({ icon: Icon, tone, label, value, pulse, className = '' }) {
  const color = {
    live: 'text-live',
    coral: 'text-coral',
    'ink-dim': 'text-ink-dim',
    'ink-faint': 'text-ink-faint',
  }[tone]

  return (
    <div className={`flex items-center gap-1.5 ${className}`}>
      {Icon ? (
        <Icon size={12} className={color} />
      ) : (
        <span
          className={`h-1.5 w-1.5 rounded-full bg-current ${color} ${pulse ? 'pulse-live' : ''}`}
        />
      )}
      <span className="label">{label}</span>
      <span className={`tabular text-[11px] ${color}`}>{value}</span>
    </div>
  )
}
