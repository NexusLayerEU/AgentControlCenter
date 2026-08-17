# Agent Control Center 0.3.0

A local daemon and dashboard for watching, understanding and gating headless
Claude Code agents.

**What you get:** an overview dashboard with live counts and charts, a control
centre showing each run as an activity tree and call graph, a real terminal, and
an approval gate that can hold a tool call until you say yes. Two themes.

## Install

**macOS / Linux**

```bash
tar -xzf acc-0.3.0-<platform>.tar.gz
cd acc-0.3.0
./install.sh
```

**Windows** (PowerShell or cmd, no admin needed)

```
Expand-Archive acc-0.3.0-windows-x64.zip
cd acc-0.3.0
.\install.bat
```

Then open a new terminal:

```
acc start      # daemon on http://127.0.0.1:4000
acc attach     # register ACC's hooks in Claude Code
acc open       # open the dashboard
```

## Requirements

- **Claude Code** on your PATH — ACC launches it; it does not replace it.
- **Java**: none, if you downloaded a platform bundle (a Temurin JRE 21 is
  included in `runtime/`). The `universal` package needs a JDK 17+ instead.

## Commands

```
acc start | stop | restart | status | open
acc attach [dir]     register hooks globally, or project-scoped in dir
acc detach [dir]     remove them
acc run "<task>" [dir] [default|acceptEdits|plan|bypassPermissions]
acc logs | version
```

`⌘K` / `Ctrl+K` opens the dispatch composer from anywhere.

## The two pages

**Overview** (the landing page) — active vs history counts, tool calls, spend and
held approvals as headline figures, then sessions and tool calls per day, run
outcomes, most-used tools, and the risk profile of every tool call. The big
**open control center** button leads into the live view.

**Control center** — one session at a time, in three views:

| View | What it is |
|------|------------|
| `FLOW` | Vertical activity tree, results nested under the call that produced them |
| `GRAPH`| Three-lane call graph — turns, the tools they invoked, the results |
| `TERM` | A real terminal in the session's working directory |

Click any node for the detail pane, including a side-by-side diff for file edits.

## Themes

Switch from the top bar; the choice sticks.

| Theme | Look |
|---|---|
| `DevTheme` | Instrument panel — warm near-black, phosphor lime, hairline rules |
| `Blackwire` | Cyberpunk — violet ink, magenta and cyan, CRT scanlines, glitch |

Append `?theme=dev` or `?theme=cyber` to a URL to link a specific one.

## What "attach" does

It writes a small bridge script to `~/.acc/` (`acc-hook.sh`, or `acc-hook.ps1`
on Windows) and registers it in Claude Code's `settings.json` for the
`PreToolUse`, `PostToolUse`, `Stop`, `SessionStart` and `Notification` events.

Your own hooks are preserved — ACC tags its entries and only ever replaces its
own. `acc detach` removes them again.

If the daemon is not running, the bridge exits successfully and your agent
proceeds as normal. ACC is never a single point of failure.

## The approval gate

Sessions started in `default` or `plan` mode pause on every tool call and wait
for you in the dashboard. Sessions started in `acceptEdits` or
`bypassPermissions` were launched to run unattended, so they are recorded but
never blocked. You can flip an individual session either way while it runs.

If nobody answers within 50 seconds the call is **denied** with a reason, not
allowed — an absent developer never means "yes".

## Where things live

| Path | What |
|------|------|
| `~/.acc/acc.db` | Session history, activity trees, approvals (SQLite) |
| `~/.acc/logs/` | Per-session stderr |
| `~/.acc/daemon.log` | Daemon log |
| `~/.acc/acc-hook.*` | The generated hook bridge |

The daemon binds `127.0.0.1` only and has no authentication — it is a local
tool, not a server.

## Uninstall

```bash
./uninstall.sh          # keeps ~/.acc history
./uninstall.sh --purge  # removes it too
```

On Windows: `uninstall.bat` (add `--purge` to remove history).
