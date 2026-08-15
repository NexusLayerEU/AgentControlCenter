# Agent Control Center (ACC)

A local daemon and dashboard for watching, understanding and gating headless
Claude Code agents. Dispatch an agent, then see every plan, tool call, diff and
result land in a live activity tree — and hold dangerous calls at a gate until
you approve them.

```
./acc build      # dashboard + daemon
./acc start      # http://127.0.0.1:4000
./acc attach     # register ACC's hooks in Claude Code
./acc run "refactor the auth module" ~/projects/thing
```

---

## What it does

**Overview dashboard.** The landing page (`#/overview`): active vs history counts,
tool calls, spend and held-approvals as headline figures, then sessions and tool
calls per day, run outcomes, most-used tools, and the risk profile of every tool
call. One prominent **open control center** button leads into the live view.

**Structured activity tree.** ACC runs `claude` headless with
`--output-format stream-json` and parses the stream into typed nodes: prompts,
assistant text, thinking, tool calls, tool results, hooks, errors. Results are
parented to the call that produced them, so the tree shows real causality
rather than a scrollback.

**Two themes.** Switch from the top bar; the choice persists.

| Theme | Look |
|---|---|
| `DevTheme` | 1970s instrument panel — warm near-black, phosphor lime, hairline rules |
| `Blackwire` | Cyberpunk — violet ink, hot magenta and electric cyan, CRT scanlines, glitch |

Both drive one set of role tokens (`live` = running, `amber` = waiting, `coral` =
danger, `cyan` = tool call, `violet` = thinking), so a theme is a palette swap, not
a fork. Append `?theme=dev` or `?theme=cyber` to link a specific deck.

**Three views of the same run.**

| View | What it is |
|------|------------|
| `FLOW` | Vertical spine of actions, results nested under their call |
| `GRAPH` | Three-lane node graph — turns, the tools they invoked, the results |
| `TERM` | A real PTY in the session's directory, streamed to xterm.js |

**Approval gate.** ACC registers a `PreToolUse` hook. When a gated session
tries to run a tool, the daemon holds the hook open and raises a card in the
dashboard with the exact command and a live countdown. Approve and the agent
proceeds; deny and it is told why.

**The gate follows the agent, not a global switch.** A session launched in
`acceptEdits` or `bypassPermissions` was started to run unattended, so its tool
calls are recorded but never blocked. Only `default` and `plan` sessions
actually wait for you. You can flip an individual session either way from its
header while it runs.

---

## Architecture

```
┌─────────────┐   stream-json (stdout)   ┌──────────────────┐
│  claude -p  │ ───────────────────────► │                  │
│  (headless) │                          │   ACC daemon     │   WebSocket   ┌───────────┐
│             │ ◄─── allow / deny ─────  │  (Spring Boot)   │ ────────────► │ dashboard │
└─────────────┘   PreToolUse hook        │                  │ ◄──────────── │  (React)  │
                                         │  SQLite: history │    REST       └───────────┘
                                         └──────────────────┘
```

- **Backend** — Java 21 / Spring Boot 3.3, SQLite (WAL) for history, pty4j for
  the terminal, plain `ProcessBuilder` for the agent itself.
- **Frontend** — React 18, Vite, Tailwind v4, framer-motion, @xyflow/react,
  xterm.js. Built into the daemon's static resources, so one jar serves both.
- **Hook bridge** — `~/.acc/acc-hook.sh`, a generated script registered in
  `settings.json`. It pipes Claude Code's hook JSON to the daemon and echoes the
  response back.

### Why headless instead of driving the TUI

The interactive TUI paints ANSI that cannot be parsed reliably. `stream-json`
gives exact tool names, inputs and results, which is what the tree and graph are
built from. The raw-terminal experience is a separate feature (`TERM`) backed by
its own PTY, rather than an attempt to make one channel do both jobs.

---

## Behaviour worth knowing

**A dead daemon never blocks your agent.** If the hook cannot reach ACC it exits
0 and the tool proceeds. ACC's value is visibility; it must not become a single
point of failure in your editor.

**An unattended developer never means "yes".** Claude Code kills a hook after
its own timeout, so ACC caps its wait at 50s and then *denies* with an explicit
reason. Silence is not consent.

**One decision satisfies every retry.** When a hook blocks, Claude Code retries
the same tool call several seconds later with a new tool-use id. ACC keys
approvals on `session + tool + exact input`, so duplicates join the original
wait, and a decision keeps applying to repeats for three minutes. Without this
you would be asked to approve the same command two or three times.

**Restarts are reconciled.** Sessions still marked running at boot are marked
failed, and pending approvals are expired — nothing is attached to those
processes any more.

**Your own hooks survive.** The installer tags its entries and only replaces its
own, so reinstalling is idempotent and unrelated hooks in `settings.json` are
left alone.

---

## Commands

```
./acc build              build dashboard + daemon
./acc start | stop | restart | status
./acc dev                Vite dev server (proxies to a running daemon)
./acc attach [dir]       register hooks globally, or project-scoped in dir
./acc detach [dir]       remove them
./acc run "<task>" [dir] [default|acceptEdits|plan|bypassPermissions]
./acc logs               tail the daemon log
```

`⌘K` in the dashboard opens the dispatch composer.

The dashboard routes in the URL hash as `#/<sessionId>/<view>`, so a specific
session and view can be linked and survives a reload.

## Claude Code integration

Installed under `~/.claude/`, outside this repo:

| Piece | Path | What it does |
|---|---|---|
| SessionStart hook | `scripts/acc-hud-session-start.sh` | Reports whether the HUD is up so Claude offers to start it once — silent if ACC isn't installed |
| Skill | `skills/acc-hud/SKILL.md` | How to start/stop/open the HUD, and the rules for asking at startup and at goodbye |
| Command | `commands/acc.md` | `/acc [open\|start\|stop\|status\|attach\|detach]` — defaults to open |

The hook never starts anything itself; it only tells Claude to ask. Silence it with
`touch ~/.acc/no-prompt`.

Note `/hud` is *not* used — that name belongs to oh-my-claudecode's statusline.

## API

| Method | Path | Purpose |
|--------|------|---------|
| `GET`  | `/api/system/status` | daemon, paths, Claude Code probe |
| `GET/POST` | `/api/sessions` | list / dispatch |
| `GET` | `/api/stats/overview?tz=` | aggregates behind the dashboard |
| `GET` | `/api/sessions/{id}/events` | the activity tree |
| `POST` | `/api/sessions/{id}/cancel` | kill the agent |
| `POST` | `/api/sessions/{id}/auto-approve` | arm or disarm the gate |
| `GET` | `/api/approvals/pending` | held tool calls |
| `POST` | `/api/approvals/{id}/approve\|deny` | decide |
| `POST` | `/api/hooks/install\|uninstall` | wire into Claude Code |
| `WS` | `/ws` | live sessions, events, approvals, PTY |

Config lives in `backend/src/main/resources/application.yml`; `ACC_PORT`,
`ACC_HOME` and `ACC_CLAUDE_BIN` override the important bits.

## Requirements

JDK 17+ (21 recommended — `./acc` finds it via `java_home`), Node 18+, Maven,
and Claude Code on `PATH`.

## Tests

```
cd backend && mvn test
```

63 tests covering the CLI argument mapping, auto-approve resolution, risk
banding, path display, the stream-json parser, the approval gate (blocking,
denial, timeout, retry de-duplication, decision replay), and the settings.json
merge. The gate tests exercise real concurrent waits, not mocks.
