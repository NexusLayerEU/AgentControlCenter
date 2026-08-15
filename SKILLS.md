# Claude Code integration

Three small pieces that let Claude drive the HUD for you, so you never have to
remember to start it.

| Piece | What it does |
|---|---|
| **skill** `acc-hud` | Teaches Claude how to start, stop, open and check the HUD — and the rules for when to ask |
| **command** `/acc` | A slash command to control the HUD directly |
| **hook** `SessionStart` | Checks whether the daemon is up and tells Claude to offer starting it |

They are optional. ACC works fine without them — this just removes the friction.

---

## Install

```bash
git clone https://github.com/NexusLayerEU/AgentControlCenter.git
cd AgentControlCenter/claude-code
./install-skills.sh
```

**Windows** (PowerShell):

```powershell
cd AgentControlCenter\claude-code
.\install-skills.ps1
```

Then **restart Claude Code** — hooks are read when a session starts.

Everything installs under `~/.claude/`:

```
~/.claude/skills/acc-hud/SKILL.md
~/.claude/commands/acc.md
~/.claude/scripts/acc-hud-session-start.sh    (.ps1 on Windows)
~/.claude/settings.json                       ← one SessionStart entry added
```

> **Your existing hooks are safe.** The installer parses `settings.json`, backs it
> up to `settings.json.acc-backup`, and only ever adds or replaces ACC's own entry
> — matched by its command path. Running it twice does not duplicate anything.

---

## What you get

### `/acc` — the slash command

```
/acc              open the dashboard (starts the daemon if needed)
/acc status       is it running, on which port
/acc start        start the daemon
/acc stop         stop it
/acc restart
/acc attach       register ACC's hooks so your own sessions are recorded
/acc detach       remove them
```

`attach` and `detach` edit Claude Code's settings, so Claude confirms with you
before running those.

> `/hud` is **not** used — that name belongs to other tools. This is `/acc`.

### The startup offer

When a session begins and the daemon is **not** running, Claude asks once, in a
single line:

> *ACC HUD isn't running — want me to start it?*

Say yes and it starts. Say no and it drops the subject for the rest of the
session. It never starts anything unprompted — launching a background server
nobody asked for would be a surprise.

If the daemon **is** already running, Claude says nothing about it.

### The goodbye offer

When you sign off — "bye", "goodbye", "see you", "good night" — and the HUD is
running, Claude asks once whether to stop it, and acts on your answer. Leaving it
running is a perfectly normal choice.

---

## Turning the startup offer off

```bash
touch ~/.acc/no-prompt      # silence it
rm ~/.acc/no-prompt         # bring it back
```

The hook exits immediately when that file exists.

---

## Uninstall

```bash
./install-skills.sh --uninstall     # macOS / Linux
.\install-skills.ps1 -Uninstall     # Windows
```

Removes the skill, the command, the hook script, and ACC's `SessionStart` entry —
leaving every other hook and setting exactly as it was.

---

## How the hook behaves

It is deliberately unobtrusive:

| Situation | What happens |
|---|---|
| ACC not installed at all | **Completely silent.** Exits 0, emits nothing. |
| `~/.acc/no-prompt` exists | Silent. |
| Daemon running | Tells Claude it is up, and to offer stopping it at goodbye. |
| Daemon down | Tells Claude to offer starting it, once. |
| Anything goes wrong | Exits 0 anyway — a broken check must never stop a session from starting. |

It finds the launcher in this order: `$ACC_BIN`, then `acc` on your `PATH`, then
`~/.acc/app/bin/acc`, `~/.local/bin/acc`, `/usr/local/bin/acc`. Set `ACC_BIN` if
you installed somewhere unusual.

---

## Why a hook and not just a skill

A skill only loads when the model decides it is relevant, so a session could
easily start without one ever firing. The harness runs hooks unconditionally —
that is the only way "ask me at startup" can be reliable.

The hook does no work of its own beyond a 2-second health check. It reports state
and hands the decision to you.
