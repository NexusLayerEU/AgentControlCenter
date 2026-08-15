import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { CornerDownLeft, X } from 'lucide-react'

import { useStore } from '../lib/store'
import { api } from '../lib/api'

const MODES = [
  {
    id: 'default',
    label: 'gated',
    hint: 'Every tool call pauses here for your approval.',
  },
  {
    id: 'acceptEdits',
    label: 'accept edits',
    hint: 'File edits run unattended. ACC records but never blocks.',
  },
  {
    id: 'plan',
    label: 'plan',
    hint: 'Agent plans first and makes no changes.',
  },
  {
    id: 'bypassPermissions',
    label: 'bypass',
    hint: 'Nothing is gated at all. Use only in a throwaway workspace.',
  },
]

export default function Composer() {
  const setComposerOpen = useStore((s) => s.setComposerOpen)
  const system = useStore((s) => s.system)
  const select = useStore((s) => s.select)
  const notify = useStore((s) => s.notify)

  const [prompt, setPrompt] = useState('')
  const [cwd, setCwd] = useState('')
  const [mode, setMode] = useState('default')
  const [model, setModel] = useState('')
  const [busy, setBusy] = useState(false)
  const inputRef = useRef(null)

  useEffect(() => {
    inputRef.current?.focus()
    if (system?.cwd) setCwd((current) => current || system.cwd)
  }, [system])

  async function dispatch() {
    if (!prompt.trim() || busy) return
    setBusy(true)
    try {
      const session = await api.startSession({
        prompt: prompt.trim(),
        cwd: cwd.trim() || undefined,
        permissionMode: mode,
        model: model.trim() || undefined,
      })
      await select(session.id)
      setComposerOpen(false)
      notify('Agent dispatched', 'ok')
    } catch (error) {
      notify(error.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  const active = MODES.find((m) => m.id === mode)

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.16 }}
      className="fixed inset-0 z-50 flex items-start justify-center bg-void/80 px-6 pt-[12vh] backdrop-blur-sm"
      onClick={() => setComposerOpen(false)}
    >
      <motion.div
        initial={{ y: -14, scale: 0.985 }}
        animate={{ y: 0, scale: 1 }}
        exit={{ y: -14, scale: 0.985 }}
        transition={{ duration: 0.24, ease: [0.16, 1, 0.3, 1] }}
        onClick={(event) => event.stopPropagation()}
        className="ticked deck w-full max-w-2xl"
      >
        <div className="flex items-center gap-2 border-b border-rule px-4 py-2">
          <span className="stencil text-[11px] text-live">dispatch agent</span>
          <button
            onClick={() => setComposerOpen(false)}
            className="ml-auto text-ink-faint hover:text-ink"
          >
            <X size={14} />
          </button>
        </div>

        <div className="space-y-3 px-4 py-4">
          <div>
            <label className="label mb-1.5 block">task</label>
            <textarea
              ref={inputRef}
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              onKeyDown={(event) => {
                if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') dispatch()
              }}
              rows={4}
              placeholder="refactor the auth module and add tests for the token refresh path"
              className="w-full resize-none text-[12px] leading-relaxed"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label mb-1.5 block">working directory</label>
              <input
                value={cwd}
                onChange={(event) => setCwd(event.target.value)}
                placeholder="~/projects/thing"
                className="w-full text-[11px]"
              />
            </div>
            <div>
              <label className="label mb-1.5 block">model (optional)</label>
              <input
                value={model}
                onChange={(event) => setModel(event.target.value)}
                placeholder="inherit"
                className="w-full text-[11px]"
              />
            </div>
          </div>

          <div>
            <label className="label mb-1.5 block">permission mode</label>
            <div className="flex gap-px border border-rule">
              {MODES.map((option) => (
                <button
                  key={option.id}
                  onClick={() => setMode(option.id)}
                  className={`flex-1 px-2 py-1.5 text-[10px] uppercase tracking-wider transition-colors ${
                    mode === option.id
                      ? option.id === 'bypassPermissions'
                        ? 'bg-coral/15 text-coral'
                        : 'bg-live/15 text-live'
                      : 'text-ink-faint hover:text-ink-dim'
                  }`}
                >
                  {option.label}
                </button>
              ))}
            </div>
            <p
              className={`mt-1.5 text-[10px] leading-relaxed ${
                mode === 'bypassPermissions' ? 'text-coral' : 'text-ink-faint'
              }`}
            >
              {active.hint}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3 border-t border-rule px-4 py-2.5">
          <span className="text-[10px] text-ink-faint">
            approval gate is {mode === 'default' || mode === 'plan' ? 'armed' : 'off for this run'}
          </span>
          <button
            onClick={dispatch}
            disabled={!prompt.trim() || busy}
            className="ml-auto flex items-center gap-2 border border-live/50 bg-live/15 px-4 py-1.5 text-[11px] text-live transition-colors hover:bg-live/25 disabled:opacity-35"
          >
            {busy ? 'launching…' : 'launch'}
            <CornerDownLeft size={11} />
          </button>
        </div>
      </motion.div>
    </motion.div>
  )
}
