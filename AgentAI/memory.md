# Project Memory

Captures architectural decisions, hard-won lessons, and constraints for ACC
(Agent Control Center). Read before making any significant changes.

---

## Architectural Decisions

### Headless `stream-json`, not the interactive TUI
The agent is spawned as `claude -p "<prompt>" --output-format stream-json --verbose`
via plain `ProcessBuilder`. The TUI paints ANSI that cannot be parsed reliably;
`stream-json` yields exact tool names, inputs and results. The raw-terminal
feature (`TERM` view) is a *separate* PTY via pty4j, not the same channel.

### The approval gate is per-session, driven by permission mode
`StartSessionRequest.resolvedAutoApprove()` is the product's core rule:
`acceptEdits` and `bypassPermissions` mean the session was launched to run
unattended, so hooks are recorded and released immediately. `default` and `plan`
gate. An explicit `autoApprove` flag overrides. An unrecognised mode falls back
to the safe (gated) default.

### Outbound broadcast is split from inbound socket handling
`SocketHub` (outbound, implements `Broadcaster`) and `AccWebSocketHandler`
(inbound, routes PTY keystrokes) are separate beans. They were originally one
class, which created a genuine Spring bean cycle: PtyRegistry → Broadcaster →
handler → PtyRegistry. Do not merge them back.

### Fail open on daemon death, fail closed on human silence
The generated hook script exits 0 when the daemon is unreachable — a dead
dashboard must never brick the agent. But an approval that times out is
*denied* with a reason, because an absent developer must not mean "yes".

---

## Gotchas & Lessons

### Claude Code retries a tool call when a hook blocks
Verified empirically: with a hook that sleeps 15s, Claude Code fires `PreToolUse`
**twice**, ~8.7s apart, with identical input but different `tool_use` ids. With a
fast hook it fires exactly once. `ApprovalService` therefore keys approvals on
`RequestKey` = sha(session + tool + exact input): duplicates join the in-flight
gate, and decisions replay onto repeats for 3 minutes. Without this the developer
is asked to approve the same command repeatedly and the extras time out.

Occasionally a retry carries a *truncated* input (observed: `{"command":"ech"}`
for `echo GATED-OK`). Those legitimately produce a separate card — deliberately,
since auto-approving a different command would be a safety hole.

### The hook wait must stay under Claude Code's own timeout
ACC waits 50s; the installed hook entry declares `timeout: 60`; the curl inside
uses `--max-time 58`. Changing one without the others breaks the gate.

### SQLite hands back Integer or Long depending on magnitude
A nullable INTEGER column read with `(Long) rs.getObject(...)` throws
`ClassCastException`. All nullable numerics go through `repo/Nullable`.

### zustand v5 selectors must be reference-stable
`useStore(s => s.timelines[s.selectedId] ?? [])` returns a fresh array each call,
so the snapshot never compares equal and React loops until it throws error #185
(blank page, no visible cause). Use the shared `EMPTY_EVENTS` constant via the
`useTimeline()` hook. Never return a new object/array literal from a selector.

### pty4j pulls a transitive artifact that is not on Maven Central
`org.jetbrains.pty4j:purejavacomm` is published only to the JetBrains repo. It is
serial-port support and unused on the PTY path, so it is excluded in `pom.xml`.

### Absolute paths destroy readability
Every tool title would otherwise be a full path. `ToolSummary.relative()` strips
the session cwd (and `$HOME`) on the backend for titles; the frontend
`relativise()` does the same for command and result previews. The Inspector
deliberately shows raw values — when reviewing what an agent did, the literal
string matters.

### Themes are role tokens, not hues
`--color-live/amber/coral/cyan/violet` are role slots named after DevTheme's palette,
which is why Blackwire's `--color-cyan` is violet. Change a slot's value per theme;
never rename the slot — the names are load-bearing across glyphs.jsx, every
component, and the runtime palette reader.

Canvas consumers (React Flow, xterm) cannot use CSS classes, so `lib/tones.js`
reads the live custom properties via `getComputedStyle` and re-reads on theme
change (one rAF later — computed styles are not current until after the frame).
Do not reintroduce a hardcoded hex table; that was removed for exactly this reason.

### Headless Chrome does not flush localStorage in one-shot --screenshot mode
A `--screenshot` run exits before the LevelDB write lands, so a persistence test
across two such runs falsely fails. Use `--dump-dom` with a `--virtual-time-budget`,
or inspect `<profile>/Default/Local Storage/leveldb/*.log` directly.

### Chart colour is decided by the data's job, not by the palette
The five role tokens are a STATUS palette, not a categorical series palette. The
dataviz validator confirms CVD separation (ΔE 9.7 dev / 9.0 cyber) and 3:1 contrast
pass in both themes; the lightness-band check fails by design, because status colours
are meant to differ in salience. Consequence: magnitude charts (tool ranking, activity)
use ONE hue; status colours appear only where the colour means a state, always with a
label and legend beside it. Never use the five tokens as a 5-series categorical ramp.

### Never interpolate Tailwind class names
`text-${tone}` / `bg-${tone}` are purged — Tailwind only sees literal strings. Chart
colour is applied as `style={{ color: 'var(--color-'+tone+')' }}`, which also makes it
theme-reactive for free.

### A stretched SVG viewBox breaks the bar-width cap
`preserveAspectRatio="none"` scales bar width with container width, so a 24px-capped
bar renders ~60px on a wide panel. The activity chart is laid out in HTML flex with
`maxWidth: 24` so the cap is in real pixels.

### Release builds MUST use `mvn clean package`
Maven copies resources into `target/classes` but never deletes ones removed from
source. Vite empties `src/main/resources/static` on each dashboard build, so without
`clean` every superseded JS/CSS bundle stays in the jar — 7.9 MB of dead assets
shipped in all six 0.2.0 artifacts before this was caught. `build-release.sh` now
runs `clean` and fails if the packaged asset count differs from what Vite produced.

### `local` outside a function aborts a `set -e` script
A guard added at the top level of build-release.sh used `local` and silently killed
the run before packaging. Use plain variables outside functions.

### Hooks can fire twice for one tool call
If ACC's hooks are installed at BOTH global and project scope, Claude Code runs
each one, so every PreToolUse arrives twice. Combined with the slow-hook retry,
`AdoptionService` de-duplicates on `tool_use_id` — the first node wins.

### `mvn test` does not produce a jar
Running `mvn test` then restarting the daemon from `target/*.jar` silently runs the
PREVIOUS build. Use `clean package` before restarting, or you will debug code that
is not running.

### Claude Code transcripts are the only source of the conversation
Hooks carry tool activity only. `transcript_path` in every hook payload points at
a JSONL with `user` (prompts) and `assistant` (text + usage) records — that is how
adopted sessions get prompts and replies. Skip the tool_use/tool_result blocks in
there; the hooks already have them with better timing.

Two hard limits, both verified: **thinking blocks are written with empty content**
(signature only), so agent reasoning is unrecoverable for adopted sessions; and the
literal API request is not exposed anywhere.

### Stop fires before the final assistant message is flushed
A transcript read triggered by the Stop hook always stops exactly one record short
— the last reply lands after. TranscriptReader re-reads once, 2s later. Verified by
mapping record byte offsets against the stored cursor.

### Order events by ts, not seq
Transcript records are ingested late but carry a real timestamp. Sequence numbers
reflect insertion order, so ordering by seq put the model's "I'll read the file"
after the read. `findBySession` orders by `ts, seq`, and the frontend store sorts
the same way.

### framer-motion ignores the CSS reduced-motion query
It animates via JS, so `@media (prefers-reduced-motion: reduce)` never applied.
`<MotionConfig reducedMotion="user">` in App.jsx makes every motion component
respect the OS setting. This also makes headless screenshots deterministic.

### Screenshotting the dashboard reliably
`--headless=new --screenshot` fires on load, before React has fetched anything, so
the UI looks empty. Use `--virtual-time-budget=10000 --force-prefers-reduced-motion`
together: the first waits for the fetches, the second stops motion components
being captured mid-fade. Verify with `--dump-dom` before assuming a UI bug.

### pty4j needs purejavacomm — do not exclude it
`com.pty4j.unix.PtyHelpers` loads `jtermios.JTermios` to open a PTY master on
macOS and Linux. Excluding the dependency compiles fine and throws
`NoClassDefFoundError` the first time a terminal is opened. It lives in the
JetBrains repo (`cache-redirector.jetbrains.com/intellij-dependencies`), not
Central, which is why it was excluded in the first place — the exclusion shipped a
permanently broken TERM view for two releases.

The wider lesson: a dependency exclusion made to fix a *build* failure needs the
runtime path exercised before it is called safe. "Compiles" is not "works".

### SessionEnd is the real "window closed" signal
Verified empirically: Claude Code fires `SessionEnd` with a `reason` field when a
session ends (headless gives `reason=other`). Register it rather than inferring a
closed terminal from inactivity — a timeout either kills live sessions or keeps
dead ones. The stale sweep stays as a 24h backstop for kill -9 / crash / reboot.

### Long-lived daemons need an eviction story
Every `ConcurrentHashMap` keyed by session id is a leak unless something removes
entries. `SessionJanitor` sweeps on a schedule and is the single place that calls
`forget()` on EventService, AdoptionService and TranscriptReader. Add a `forget`
whenever you add per-session state.

### `@Scheduled` silently does nothing without `@EnableScheduling`
The bean registers, the method never runs, and there is no warning. Always verify a
scheduled task by observing its effect, never by reading the annotation.

### The default JDK on this machine is 11
Spring Boot 3 needs 17+. The `./acc` script resolves 21 via `/usr/libexec/java_home`.

### Headless-Chrome screenshots need real time
`--virtual-time-budget` freezes framer-motion mid-animation, so elements render
at `opacity: 0` and look like a bug. Use `--headless=new` without it.
