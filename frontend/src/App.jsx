import { useEffect } from 'react'
import { AnimatePresence } from 'framer-motion'

import { useStore } from './lib/store'
import { connectSocket } from './lib/socket'

import TopBar from './components/TopBar'
import SessionRail from './components/SessionRail'
import StageHeader from './components/StageHeader'
import FlowTree from './components/FlowTree'
import CallGraph from './components/CallGraph'
import TerminalPane from './components/TerminalPane'
import Inspector from './components/Inspector'
import ApprovalDock from './components/ApprovalDock'
import Composer from './components/Composer'
import Toast from './components/Toast'
import EmptyStage from './components/EmptyStage'
import Overview from './components/Overview'

export default function App() {
  const hydrate = useStore((s) => s.hydrate)
  const view = useStore((s) => s.view)
  const page = useStore((s) => s.page)
  const selectedId = useStore((s) => s.selectedId)
  const composerOpen = useStore((s) => s.composerOpen)
  const setComposerOpen = useStore((s) => s.setComposerOpen)

  useEffect(() => {
    connectSocket()
    hydrate()
  }, [hydrate])

  // ⌘K opens the dispatch composer from anywhere; Escape closes it.
  useEffect(() => {
    const onKey = (event) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setComposerOpen(true)
      }
      if (event.key === 'Escape') setComposerOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [setComposerOpen])

  return (
    <div className="relative z-10 flex h-full flex-col">
      {/* Theme-owned atmosphere layer; inert in DevTheme. */}
      <div className="atmosphere" aria-hidden="true" />
      <TopBar />

      {page === 'overview' ? (
        <main className="min-h-0 flex-1 overflow-hidden">
          <Overview />
        </main>
      ) : (
      <div className="flex min-h-0 flex-1">
        <SessionRail />

        <main className="flex min-h-0 min-w-0 flex-1 flex-col border-r border-rule">
          {selectedId ? (
            <>
              <StageHeader />
              <div className="min-h-0 flex-1 overflow-hidden">
                {view === 'flow' && <FlowTree />}
                {view === 'graph' && <CallGraph />}
                {view === 'term' && <TerminalPane />}
              </div>
            </>
          ) : (
            <EmptyStage />
          )}
        </main>

        <Inspector />
      </div>
      )}

      <ApprovalDock />

      <AnimatePresence>{composerOpen && <Composer key="composer" />}</AnimatePresence>
      <Toast />
    </div>
  )
}
