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

## Session 2026-08-17
- Did: Recorded the conversation for adopted sessions (prompts, replies, model,
  per-message tokens) by tailing Claude Code's transcript; added filter switches
  above the timeline; replaced the 30-min staleness guess with the SessionEnd hook
  so a session lives exactly as long as its terminal; ran a deliberate bug-hunt
  over the daemon; fixed the terminal; cut v0.3.0.
- Changed: TranscriptReader, AdoptionService, SessionJanitor, HookController,
  HookInstaller (+SessionEnd), SessionRepository, PtyRegistry, SocketHub,
  AccApplication (@EnableScheduling), Toggle/FilterBar/StageHeader, filters.js,
  pom.xml (purejavacomm restored), docs, CHANGELOG.
- Fixed: 8 real defects — terminal never worked (excluded pty4j dependency);
  adopted sessions active forever; restarts marking user sessions FAILED; leaked
  shell per terminal; three unbounded maps; orphaned transcript cursors; events
  ordered by ingest time rather than when they happened; framer-motion ignoring
  prefers-reduced-motion.
- Decided: SessionEnd over a timeout; transcript over hooks for the conversation;
  uniform switch colour with a separate identity dot; minor version bump for 0.3.0.
- Next: tests for TranscriptReader / AdoptionService / SessionJanitor — the
  lifecycle logic is verified by observation only. Windows and Linux bundles have
  still never been run on their own OS. Optional: JVM tuning to halve the ~150 MB
  footprint (measurement was interrupted).
