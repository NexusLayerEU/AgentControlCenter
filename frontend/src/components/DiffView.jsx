import { useMemo } from 'react'

/**
 * Line-level diff for Edit/Write tool inputs.
 *
 * Edit gives old_string/new_string; Write gives whole content, which is shown
 * as pure additions. A longest-common-subsequence pass keeps unchanged lines
 * aligned so the eye can scan the change rather than the whole file.
 */
export default function DiffView({ toolName, input }) {
  const rows = useMemo(() => {
    if (toolName === 'Write') {
      return String(input.content ?? '')
        .split('\n')
        .map((text, index) => ({ kind: 'add', text, right: index + 1 }))
    }
    return diffLines(String(input.old_string ?? ''), String(input.new_string ?? ''))
  }, [toolName, input])

  const added = rows.filter((r) => r.kind === 'add').length
  const removed = rows.filter((r) => r.kind === 'del').length

  return (
    <div>
      <div className="mb-1.5 flex items-center gap-3">
        <p className="label">{toolName === 'Write' ? 'new file' : 'diff'}</p>
        <span className="tabular text-[10px] text-live">+{added}</span>
        <span className="tabular text-[10px] text-coral">−{removed}</span>
        {input.file_path && (
          <span className="ml-auto truncate text-[10px] text-ink-faint">
            {input.file_path.split('/').slice(-2).join('/')}
          </span>
        )}
      </div>

      <div className="max-h-[60vh] overflow-auto border border-rule bg-void">
        {rows.map((row, index) => (
          <div
            key={index}
            className={`flex gap-2 px-2 py-px font-mono text-[11px] leading-[1.55] ${
              row.kind === 'add'
                ? 'bg-live/[0.07] text-live'
                : row.kind === 'del'
                  ? 'bg-coral/[0.07] text-coral'
                  : 'text-ink-dim'
            }`}
          >
            <span className="tabular w-7 shrink-0 select-none text-right text-ink-faint/50">
              {row.left ?? ''}
            </span>
            <span className="tabular w-7 shrink-0 select-none text-right text-ink-faint/50">
              {row.right ?? ''}
            </span>
            <span className="w-2 shrink-0 select-none">
              {row.kind === 'add' ? '+' : row.kind === 'del' ? '−' : ' '}
            </span>
            <span className="whitespace-pre-wrap break-all">{row.text || ' '}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/** Classic LCS table, then a walk back through it to emit the row list. */
function diffLines(before, after) {
  const a = before.split('\n')
  const b = after.split('\n')

  const lcs = Array.from({ length: a.length + 1 }, () => new Array(b.length + 1).fill(0))
  for (let i = a.length - 1; i >= 0; i -= 1) {
    for (let j = b.length - 1; j >= 0; j -= 1) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1])
    }
  }

  const rows = []
  let i = 0
  let j = 0
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      rows.push({ kind: 'same', text: a[i], left: i + 1, right: j + 1 })
      i += 1
      j += 1
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      rows.push({ kind: 'del', text: a[i], left: i + 1 })
      i += 1
    } else {
      rows.push({ kind: 'add', text: b[j], right: j + 1 })
      j += 1
    }
  }
  while (i < a.length) rows.push({ kind: 'del', text: a[i], left: ++i })
  while (j < b.length) rows.push({ kind: 'add', text: b[j], right: ++j })

  return rows
}
