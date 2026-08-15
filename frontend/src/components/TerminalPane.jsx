import { useEffect, useRef, useState } from 'react'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'

import { useStore } from '../lib/store'
import { api } from '../lib/api'
import { onPtyData, sendSocket } from '../lib/socket'
import { terminalFont, terminalTheme, usePalette } from '../lib/tones'


export default function TerminalPane() {
  const session = useStore((s) => s.sessions.find((x) => x.id === s.selectedId))
  const notify = useStore((s) => s.notify)
  const hostRef = useRef(null)
  const termRef = useRef(null)
  const [status, setStatus] = useState('opening')
  const { tones, surfaces } = usePalette()

  const terminalId = session?.id

  useEffect(() => {
    if (!hostRef.current || !terminalId) return

    const term = new Terminal({
      fontFamily: terminalFont(),
      fontSize: 12,
      lineHeight: 1.45,
      letterSpacing: 0.2,
      cursorBlink: true,
      theme: terminalTheme(tones, surfaces),
      scrollback: 8000,
allowProposedApi: true,
    })
    const fit = new FitAddon()
    term.loadAddon(fit)
    term.open(hostRef.current)
    fit.fit()
    termRef.current = term

    term.onData((data) => sendSocket({ channel: 'pty:input', sessionId: terminalId, data }))

    const unsubscribe = onPtyData((channel, payload) => {
      if (payload.sessionId !== terminalId) return
      if (channel === 'pty:data') term.write(payload.data)
      if (channel === 'pty:exit') {
        setStatus('closed')
        term.write('\r\n\x1b[38;5;244m── shell exited ──\x1b[0m\r\n')
      }
      if (channel === 'pty:error') {
        setStatus('error')
        term.write(`\r\n\x1b[31m${payload.message}\x1b[0m\r\n`)
      }
    })

    const observer = new ResizeObserver(() => {
      try {
        fit.fit()
        sendSocket({
          channel: 'pty:resize',
          sessionId: terminalId,
          cols: term.cols,
          rows: term.rows,
        })
      } catch {
        /* pane not measurable yet */
      }
    })
    observer.observe(hostRef.current)

    api
      .openTerminal(terminalId, {
        cwd: session.cwd,
        cols: term.cols,
        rows: term.rows,
      })
      .then((result) => setStatus(result.opened ? 'live' : 'error'))
      .catch((error) => {
        setStatus('error')
        notify(error.message, 'error')
      })

    return () => {
      unsubscribe()
      observer.disconnect()
      term.dispose()
      api.closeTerminal(terminalId).catch(() => {})
    }
  }, [terminalId, session?.cwd, notify])

  // Recolour in place: rebuilding the terminal would discard scrollback.
  useEffect(() => {
    if (termRef.current) {
      termRef.current.options.theme = terminalTheme(tones, surfaces)
      termRef.current.options.fontFamily = terminalFont()
    }
  }, [tones, surfaces])

  if (!session) return null

  return (
    <div className="flex h-full flex-col">
      <div className="flex shrink-0 items-center gap-2 border-b border-rule px-4 py-1.5">
        <span
          className={`h-1.5 w-1.5 rounded-full ${
            status === 'live' ? 'bg-live pulse-live' : status === 'error' ? 'bg-coral' : 'bg-ink-faint'
          }`}
        />
        <span className="label">pty · {status}</span>
        <span className="ml-auto truncate text-[10px] text-ink-faint">{session.cwd}</span>
      </div>
      <div ref={hostRef} className="min-h-0 flex-1 px-3 py-2" />
    </div>
  )
}
