import { AnimatePresence, motion } from 'framer-motion'

import { useStore } from '../lib/store'

const TONE = {
  ok: 'border-live/50 text-live',
  warn: 'border-amber/50 text-amber',
  error: 'border-coral/50 text-coral',
  info: 'border-rule-hot text-ink',
}

export default function Toast() {
  const toast = useStore((s) => s.toast)

  return (
    <AnimatePresence>
      {toast && (
        <motion.div
          key={toast.at}
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 12 }}
          transition={{ duration: 0.2 }}
          className={`ticked deck fixed bottom-5 left-1/2 z-[60] -translate-x-1/2 border px-4 py-2 text-[11px] ${
            TONE[toast.tone] ?? TONE.info
          }`}
        >
          {toast.message}
        </motion.div>
      )}
    </AnimatePresence>
  )
}
