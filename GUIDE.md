# Using Agent Control Center

Everything ACC does, and why it behaves the way it does.

New here? [INSTALL.md](INSTALL.md) first.

---

## The two pages

### Overview — the landing page

Where you start. Five headline figures across the top:

| Figure | Meaning |
|---|---|
| **active** | Agents running right now. Pulses when something is live. |
| **history** | Finished runs, split into ok / failed / cancelled. |
| **tool calls** | Every command and file operation, with the failure rate. |
| **spend** | Total USD across all runs, plus turns and average duration. |
| **held now** | Tool calls **blocked right now**, waiting on you. Turns red. |

Below: sessions and tool calls per day, run outcomes, how runs were launched,
your most-used tools with failures overlaid, and the risk profile of every call.

The big **open control center** button takes you to the live view.

### Control center — one run at a time

Pick a session from the left rail. Three views of it:

| View | What it is |
|---|---|
| **FLOW** | The activity tree. Results nest under the call that produced them. |
| **GRAPH** | The same run spatially — turns, tools, results, with in-flight edges. |
| **TERM** | A real terminal in that session's working directory. |

Above the timeline is a row of **show** toggles — prompts, replies, thinking,
tools, gate, system — each with a count. Turn off what you do not care about; a
long session is much easier to read with `tools` alone, or with everything but
`system`. The choice persists, and an amber **N hidden** chip appears so a
filtered view never looks like an empty one. Errors are never hidden.

Click any node to open the inspector. For `Edit` and `Write` you get a
**side-by-side diff**; for everything else, the exact input and raw output.

> Titles and previews show paths relative to the session's directory so they stay
> readable. The inspector deliberately shows **raw** values — when you are
> reviewing what an agent actually did, the literal string matters.

---

## Running agents

### Dispatch one from ACC

Press **⌘K** (or `Ctrl+K`), or hit **dispatch agent**.

| Field | Notes |
|---|---|
| **task** | The prompt. ⌘↵ launches. |
| **working directory** | Where the agent runs. Defaults to the daemon's cwd. |
| **model** | Optional. Blank inherits your Claude Code default. |
| **permission mode** | Decides whether the gate is armed — see below. |

Or from the terminal:

```bash
acc run "refactor the auth module and add tests" ~/projects/api
acc run "audit dependencies" ~/projects/api acceptEdits
```

### Permission modes

This is the single most important choice, because it decides whether ACC blocks
the agent or just watches it.

| Mode | Gate | Use when |
|---|---|---|
| `default` | **armed** — every tool call waits for you | You want to supervise |
| `plan` | **armed** — agent plans, makes no changes | Scoping work |
| `acceptEdits` | off — file edits run unattended | You trust the task |
| `bypassPermissions` | off — nothing is gated at all | Throwaway workspace only |

A session launched in `acceptEdits` or `bypassPermissions` was *started to run
unattended*, so ACC records its tool calls but never blocks them. That is the
rule the whole gate turns on.

You can flip an individual session either way while it runs, from the
**gated / auto-approve** toggle in its header.

---

## The approval gate

When a gated session tries to run a tool, the daemon **holds Claude Code's hook
open** — the agent is genuinely stopped — and a card appears at the bottom of the
dashboard showing the exact command, its risk band, and a live countdown.

**Approve** and the agent proceeds. **Deny** and it is told why, so it can adapt
rather than silently fail.

### Risk bands

| Band | Colour | What lands here |
|---|---|---|
| `safe` | grey | `Read`, `Grep`, `Glob`, web search |
| `normal` | blue | Everything else |
| `elevated` | amber | `Write`, `Edit`, any shell command |
| `destructive` | red | `rm -rf`, `mkfs`, `dd`, `DROP TABLE`, `git push --force`… |

### Rules the gate follows

**Silence is never consent.** Claude Code kills a hook after its own timeout, so
ACC waits ~50 seconds and then **denies** with a reason. It never assumes yes
because you were away from the keyboard.

**One click satisfies every retry.** Claude Code re-fires a blocked hook a few
seconds later with a new tool-use id. ACC keys approvals on session + tool +
*exact input*, so duplicates join the original wait and your decision replays onto
repeats for three minutes. A *different* command always gets its own card — an
approval is never inherited.

**A dead daemon never blocks you.** If the hook cannot reach ACC it exits
successfully and the tool runs. Stopping ACC can never wedge your agent.

---

## Recording your own sessions

By default ACC only builds trees for agents it launched. To capture the Claude
Code you run in your own terminal:

```bash
acc attach              # globally, in ~/.claude/settings.json
acc attach ~/project    # or just one project
acc detach              # undo
```

**Restart any Claude Code session that was already open** — hooks are read at
session start.

Your sessions then appear in the rail, live, with their tool calls and results.
They show `IDLE` between turns and stay that way for as long as the terminal is
open — ACC registers Claude Code's `SessionEnd` hook, so a session closes the
moment you exit and not a second before. Leave a window idle over lunch and it is
still there when you come back.

If a window dies without warning (kill -9, a crash, a reboot) that hook never
arrives, so a backstop closes anything untouched for 24 hours
(`acc.stale-session-minutes`).

### What attaching actually changes

ACC writes a small bridge script to `~/.acc/` and registers it for five hook
events. Your own hooks are preserved — ACC tags its entries and only replaces
those.

**Cost:** about **21 ms per tool call**. That is bash + curl process startup, not
the network, so it is the same whether the daemon is running or not. At a handful
of tool calls per session it is under a tenth of a second.

**Your own sessions are never gated by default.** You are already answering Claude
Code's permission prompts in that terminal; a second gate in a browser tab you may
not be watching would hang your work for the full timeout. Arm it per session from
the header if you actually want it.

### What you do and don't see

| | Dispatched by ACC | Your own session |
|---|---|---|
| Tool calls, inputs, results | ✅ | ✅ |
| Diffs, durations, risk bands | ✅ | ✅ |
| Your prompts | ✅ | ✅ from the transcript |
| The model's replies | ✅ | ✅ from the transcript |
| Per-message token usage | ❌ | ✅ from the transcript |
| The agent's thinking | ✅ | ❌ — transcripts store thinking blocks empty |
| Cost in USD, turn count | ✅ | ❌ |

### Where the conversation comes from

Hooks carry tool activity but nothing about the conversation. Each hook payload
does include a `transcript_path`, so ACC tails that JSONL file and picks up your
prompts, the model's replies, the model name and its token counts — skipping the
tool blocks, which the hooks already recorded with better timing.

Progress is stored as a byte offset per session, so a restart resumes mid-file
rather than replaying. The last reply of a turn is written *after* the Stop hook
runs, so ACC re-reads once, two seconds later, to catch it.

**This means your conversations are stored in `~/.acc/acc.db`.** If you only want
tool activity recorded, turn it off:

```yaml
acc:
  capture-transcript: false
```

What is *not* available anywhere: the literal API request — the system prompt,
tool schemas and full message array Claude Code sends. Neither hooks nor the
transcript expose it.

---

## Themes

Switch from the top bar; the choice persists.

| Theme | Look |
|---|---|
| **DevTheme** | Warm near-black instrument panel, phosphor lime, hairline rules |
| **Blackwire** | Cyberpunk — violet ink, magenta and cyan, CRT scanlines, glitch |

Append `?theme=dev` or `?theme=cyber` to link a specific one.

---

## Letting Claude drive the HUD

Install the optional integration and Claude gains a `/acc` command, plus the
manners to offer starting the HUD when a session begins and stopping it when you
say goodbye:

```bash
cd claude-code && ./install-skills.sh
```

Details, including how to silence the startup offer: [SKILLS.md](SKILLS.md).

---

## The CLI

```
acc start | stop | restart | status | open
acc attach [dir]     register ACC's hooks in Claude Code
acc detach [dir]     remove them
acc run "<task>" [dir] [default|acceptEdits|plan|bypassPermissions]
acc logs | version
```

`acc status` exits non-zero when the daemon is down, so it works in scripts.

---

## Linking and sharing

The dashboard routes in the URL hash, so any view is linkable and survives a
reload:

```
#/overview                       the dashboard
#/<sessionId>/flow               a session's activity tree
#/<sessionId>/graph              the same session as a call graph
?theme=cyber#/overview           a specific theme
```

---

## The API

Everything the dashboard does is available over REST on `127.0.0.1:4000`.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/system/status` | Daemon, paths, Claude Code probe |
| `GET` | `/api/stats/overview?tz=` | Every figure on the dashboard |
| `GET` `POST` | `/api/sessions` | List / dispatch |
| `GET` | `/api/sessions/{id}/events` | The activity tree |
| `POST` | `/api/sessions/{id}/cancel` | Kill a running agent |
| `POST` | `/api/sessions/{id}/auto-approve` | Arm or disarm the gate |
| `GET` | `/api/approvals/pending` | Tool calls held right now |
| `POST` | `/api/approvals/{id}/approve` \| `/deny` | Decide one |
| `POST` | `/api/hooks/install` \| `/uninstall` | Attach / detach |
| `WS` | `/ws` | Live sessions, events, approvals, terminal |

```bash
# What is blocked right now?
curl -s localhost:4000/api/approvals/pending | jq

# Today's spend
curl -s localhost:4000/api/stats/overview | jq .totals.costUsd
```

---

## Privacy

The daemon binds `127.0.0.1` only and has **no authentication** — it is a local
tool, not a server. Do not expose the port.

Everything stays on your machine: `~/.acc/acc.db` holds your session history,
`~/.acc/logs/` holds per-session stderr. No account, no cloud, no telemetry.
Deleting `~/.acc` removes all of it.

Note that activity trees contain **file contents, command output, your prompts
and the model's replies**, because that is the point. Treat `~/.acc/acc.db` as at
least as sensitive as the projects you point agents at. Set
`capture-transcript: false` to keep conversations out of it.

---

## Troubleshooting

**Nothing appears when I use Claude Code** — run `acc attach`, then restart the
session. Hooks are read at session start.

**My agent hangs for ~50 seconds on every tool call** — a gated session with
nobody watching the dashboard. Switch it to auto-approve in the header, or
dispatch with `acceptEdits`.

**I approved but the tool still failed** — the hook had already timed out before
the click landed. The dashboard tells you when a decision was recorded but not
delivered.

**A session says FAILED that I know finished** — the daemon restarted while it was
running. Sessions still marked running at boot are reconciled to failed, because
nothing is attached to those processes any more.

**Duplicate tool calls in the tree** — ACC hooks registered at *both* global and
project scope. Harmless (they are de-duplicated), but `acc detach` in one scope
tidies it.
