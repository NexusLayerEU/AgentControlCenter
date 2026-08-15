# Original specification

The three documents ACC was built from, unedited. Kept for provenance — the
shipped product deviates from them in a few places, and those deviations are
explained in [`../../release/CHANGELOG.md`](../../release/CHANGELOG.md) and
[`../../AgentAI/memory.md`](../../AgentAI/memory.md).

The most significant deviation: the architecture doc assumed the Agent Client
Protocol and a PTY-scraped TUI. Claude Code does not speak ACP natively, and its
interactive TUI paints ANSI that cannot be parsed reliably — so ACC uses
`--output-format stream-json` for dispatched runs and lifecycle hooks for the
sessions you run yourself. The terminal is a separate feature with its own PTY.
