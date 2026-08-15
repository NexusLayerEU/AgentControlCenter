# Session Log

## Session 2026-08-14
- Did: Built ACC end-to-end from the three spec documents — backend daemon, React
  dashboard, hook bridge, approval gate, CLI, tests, docs.
- Verified: Real Claude Code runs (2.1.232) for both auto-approve and gated modes;
  approval genuinely blocks the agent and releases it on click.
- Fixed during build: Spring bean cycle (split SocketHub from the socket handler);
  SQLite Integer/Long cast; zustand v5 infinite render loop; pty4j transitive dep;
  absolute paths swamping the UI; empty minimap.
- Discovered: Claude Code retries a tool call when a PreToolUse hook blocks
  (~8.7s, identical input, new tool_use id) — drove the RequestKey de-dup design.
- Decided: headless stream-json over TUI scraping; gate follows the session's
  permission mode; fail open on daemon death, fail closed on human silence.
- Next: `./acc attach` for global hooks (only project scope tested); see
  "Known gaps" in state.md.
