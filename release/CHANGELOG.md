# Changelog

## Unreleased

### Added

- **Conversations, not just tools.** Adopted sessions now record your prompts and
  the model's replies, read from Claude Code's transcript, with the model name and
  per-message token usage (input, output, cache read/write). Previously an adopted
  session showed tool calls with no idea what was asked or answered.
- **Filter switches above the timeline** — prompts, replies, thinking, tools, gate
  and system, each a two-position slider with a count, persisted between sessions
  and linkable with `?filters=tools,prompts`. Errors are never filtered out. Both
  the tree and the graph respect them. State and identity are separate channels:
  the track is one accent so on/off is unmistakable, and a small square carries
  the category — tinting the track by category made switches for the neutral
  categories look off while they were on.
- `acc.capture-transcript` (default `true`) and `acc.transcript-text-limit` to
  control or disable it — transcripts contain your full conversation.

### Fixed

- **Events are ordered by when they happened**, not when ACC read them. Transcript
  records arrive after the tool calls they preceded, so the tree used to show the
  model's "I'll read the file" *after* the read.

- **framer-motion now honours `prefers-reduced-motion`.** The CSS media query
  never reached it, because it animates in JavaScript — so the staggered reveals
  kept moving for anyone who had asked the system to stop.

### Fixed

- **The terminal never worked.** Opening TERM returned a 500 with
  `ClassNotFoundException: jtermios.JTermios`. pty4j's `purejavacomm` dependency
  was excluded during the very first build, on the incorrect assumption that it
  was serial-port support unused by the PTY path — in fact `PtyHelpers` loads it
  to open a PTY master on macOS and Linux. Excluding it compiles cleanly and fails
  only at runtime, the first time anyone opens a terminal. It is published to the
  JetBrains repo rather than Central, which is now declared in the pom.

  **This affected every 0.2.0 bundle.** Re-download, or rebuild from source.

### Fixed (a bug-hunt pass)

- **Adopted sessions stayed "active" forever.** Nothing moved them out of IDLE, so
  terminals shut two days earlier were still inflating the active count. ACC now
  registers Claude Code's **`SessionEnd`** hook, which fires the moment a session
  really ends — so a session stays live for exactly as long as you keep the
  terminal open, and closes the instant you exit. `acc.stale-session-minutes`
  (default 1440 = 24h) remains only as a backstop for windows that die without
  warning: kill -9, a crash, a reboot.

  **Existing installs must re-run `acc attach`** to pick up the new hook.
- **Restarting the daemon marked your own sessions FAILED.** Nothing failed; ACC
  merely stopped watching. Adopted sessions now close as COMPLETED, while
  dispatched agents — which genuinely die with the daemon — still fail.
- **Closing the dashboard leaked a shell per terminal opened.** PTYs exist only to
  feed a browser pane, so they are now reaped when the last client disconnects.
- **Three unbounded in-memory maps** in a process meant to run for weeks: open
  tool calls that never received a PostToolUse, one lock per transcript, and
  per-session sequence counters. All evicted when a session ends.
- **Deleting a session orphaned its transcript cursor**, so a re-adopted Claude
  session would resume mid-file instead of from the start.

### Known limits

- The agent's **thinking** is not recoverable for adopted sessions: Claude Code
  writes thinking blocks to the transcript with empty content and a signature only.
- The literal API request (system prompt, tool schemas, full message array) is not
  exposed by hooks or transcripts, so ACC cannot show it.

## 0.2.0

### Added

- **Your own Claude Code sessions now appear in ACC.** Previously only sessions
  ACC dispatched produced a tree; a session you ran in your terminal fired hooks
  that were observed and discarded. The daemon now adopts any session it hears a
  hook from, pairing `PreToolUse` and `PostToolUse` by `tool_use_id` into the same
  TOOL_CALL → TOOL_RESULT tree (minus the assistant's prose, which hooks do not
  carry). Adopted sessions show `IDLE` between turns and are **never gated** —
  you are already answering Claude Code's own prompts in that terminal.

- **Overview dashboard** as the landing page (`#/overview`). Headline figures for
  active sessions, history, tool calls, spend and held approvals; sessions and tool
  calls per day; run outcomes; gated vs unattended; most-used tools with failures;
  risk profile of every tool call; approval-gate results; recent sessions. A
  prominent **open control center** button leads into the live view.
- **`GET /api/stats/overview?tz=`** — aggregates the above in SQL rather than
  pulling every session's timeline. `tz` buckets "per day" by the developer's zone.
- **Two themes**, switchable from the top bar and persisted: `DevTheme` (the
  original instrument panel) and `Blackwire` (cyberpunk — CRT scanlines, neon,
  glitch). Linkable with `?theme=dev` / `?theme=cyber`.
- **Windows support.** A PowerShell hook bridge (`acc-hook.ps1`) — Claude Code
  cannot execute a `.sh`, so the approval gate previously did nothing on Windows.
  Also a platform-appropriate terminal shell and `.cmd`/`.exe` launcher resolution
  for npm-installed CLIs.
- **Hash routing** — `#/overview` and `#/<sessionId>/<view>` are linkable and
  survive a reload.
- `acc open` / `acc version` in the bundled launcher.

### Fixed

- **One approval now satisfies every retry.** Claude Code re-fires a blocked
  `PreToolUse` hook (measured: ~8.7s later, identical input, new tool-use id), so a
  single click used to leave the retries to time out and fail the tool. Approvals
  are now keyed on session + tool + exact input; duplicates join the original wait
  and a decision replays onto repeats for three minutes.
- **A stale `JAVA_HOME` no longer blocks startup.** The launcher version-checks
  every candidate instead of trusting `JAVA_HOME`, which very often points at an
  older JDK while a newer one is installed and discoverable.
- **Absolute paths no longer swamp the UI.** Tool titles, command previews and
  result bodies are shown relative to the session's working directory. The detail
  pane still shows raw values — when reviewing what an agent did, the literal
  string matters.
- The installer now ships its own uninstaller into the install prefix, so removing
  the download does not strip your ability to uninstall.
- Session and event rows read nullable numeric columns through a coercion helper;
  SQLite returns `Integer` or `Long` depending on magnitude, which used to throw.

### Notes

- Tests: **69** (was 31 at 0.1.0), covering the CLI argument mapping, auto-approve
  resolution, the approval gate under real concurrent waits, retry de-duplication,
  risk banding, path display, the stream-json parser, platform selection, and the
  `settings.json` merge.
- No migration needed — the SQLite schema is unchanged and existing history in
  `~/.acc/acc.db` is picked up as-is.

## 0.1.0

First release. Daemon, activity tree, call graph, terminal, approval gate, hook
installer, `acc` CLI, and self-contained bundles for macOS, Linux and Windows.
