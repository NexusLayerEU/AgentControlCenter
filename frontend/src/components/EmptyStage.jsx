import { motion } from 'framer-motion'

import { useStore } from '../lib/store'

export default function EmptyStage() {
  const setComposerOpen = useStore((s) => s.setComposerOpen)

  return (
    <div className="flex h-full flex-col items-center justify-center gap-6 px-8 text-center">
      <motion.div
        initial={{ opacity: 0, scale: 0.94 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        className="ticked deck relative px-10 py-8"
      >
        <div className="mx-auto mb-4 h-10 w-px bg-gradient-to-b from-transparent via-live to-transparent" />
        <p className="stencil text-[13px] text-ink">no session selected</p>
        <p className="mt-2 max-w-xs text-[11px] leading-relaxed text-ink-dim">
          Dispatch an agent and every plan, tool call and file write lands here as a live tree.
        </p>
        <button
          onClick={() => setComposerOpen(true)}
          className="mt-5 border border-live/50 bg-live/15 px-4 py-1.5 text-[11px] text-live transition-colors hover:bg-live/25"
        >
          dispatch agent · ⌘K
        </button>
      </motion.div>
    </div>
  )
}
