# Project State

## Done
- Backend daemon: Spring Boot 3.3 / Java 21, SQLite (WAL) persistence, REST + WebSocket.
- Agent runner: headless `claude -p --output-format stream-json`, parsed into a typed activity tree.
- Approval gate: PreToolUse hook bridge, per-session auto-approve, retry de-duplication, timeout-denies.
- Hook installer: idempotent, tagged, preserves the user's own hooks; global or project scope.
- PTY terminal: pty4j + xterm.js, streamed over the same WebSocket.
- Dashboard: FLOW (activity tree), GRAPH (@xyflow three-lane call graph), TERM, Inspector with
  LCS diff view, approval dock with live countdown, ⌘K dispatch composer.
- `./acc` CLI: build / start / stop / status / dev / attach / detach / run / logs.
- Hash routing `#/<sessionId>/<view>` — sessions and views are linkable and reload-safe.
- 63 backend tests, all green. Verified end-to-end against Claude Code 2.1.232.

- Claude Code integration: SessionStart hook (offer to start HUD), `acc-hud` skill
  (start/stop/open + ask-at-goodbye rules), `/acc` slash command. All under `~/.claude/`.

- Two themes: DevTheme (instrument panel) and Blackwire (cyberpunk/CRT), switchable
  from the top bar, persisted to localStorage, linkable via `?theme=`.

- Overview dashboard as the landing page: stat tiles, activity columns, outcome and
  risk stacked bars, ranked tool bars, recent-session strip, prominent control-center
  entry. Backed by `GET /api/stats/overview` (SQL aggregation, json_extract for risk).

- Release 0.2.0 cut: version bumped everywhere, CHANGELOG added, all six bundles
  rebuilt from a clean tree and checksummed.

- Published: https://github.com/NexusLayerEU/AgentControlCenter (public, MIT).
  README/INSTALL/GUIDE written, v0.2.0 release cut with all six bundles attached.

- Claude Code integration published in `claude-code/` with cross-platform installers
  and SKILLS.md; local ~/.claude copies kept identical to the repo.

- Conversation capture: adopted sessions now record prompts, replies and token
  usage from the Claude Code transcript, ordered chronologically.

## In Progress
- Nothing.

## Blocked
- Nothing.

## Known gaps / next candidates
- Hooks are not attached globally yet — verified project-scoped only. Run `./acc attach` to wire
  ACC into every Claude Code session on this machine.
- The TERM view opens a plain login shell, not an attached agent session.
- No auth on the daemon; it binds 127.0.0.1 only.
- Frontend bundle is one 786 kB chunk (no code splitting yet).
- Agent stderr goes to `~/.acc/logs/<id>.stderr.log` and surfaces as an ERROR event on
  non-zero exit; it is not streamed live. (The TERM PTY does stream stdout+stderr live.)
- No unit tests for SessionService, the repositories, or PtyRegistry — those are covered
  only by the end-to-end runs.
- Diff view renders Edit/Write inputs; it does not yet reconstruct MultiEdit.
