---
name: acc-hud
description: Start, stop, open or check the ACC HUD (Agent Control Center) — the local dashboard at 127.0.0.1:4000 that shows agent activity trees and gates tool calls. Use when the user says "start the hud", "stop the hud", "open the hud", "is the hud running", "acc status", when a SessionStart hook reports the HUD is not running and you should offer to start it, or when the user says goodbye while the HUD is running and you should offer to stop it.
---

# ACC HUD

The ACC HUD is a local daemon plus web dashboard that watches headless Claude Code
agents: it renders their activity as a live tree and call graph, and can hold tool
calls at an approval gate. Dashboard: **http://127.0.0.1:4000**.

## Finding the launcher

Always resolve the launcher before running anything — do not hardcode a path:

```bash
ACC=$(command -v acc || ls "$ACC_BIN" ~/.acc/app/bin/acc ~/.local/bin/acc \
      /usr/local/bin/acc 2>/dev/null | head -1)
```

If nothing is found, ACC is not installed. Say so plainly and stop — do not try to
build or install it unless the user asks.

## Commands

| Intent | Command |
|---|---|
| Is it running? | `"$ACC" status` |
| Start it | `"$ACC" start` |
| Stop it | `"$ACC" stop` |
| Open the dashboard | `"$ACC" open` |
| Wire into Claude Code | `"$ACC" attach` |
| Remove the hooks | `"$ACC" detach` |

`status` exits non-zero when the daemon is down, so it doubles as the check.

## Asking at session start

A `SessionStart` hook reports whether the HUD is running. When it says the HUD is
**not** running:

- Ask **once**, in a single short line, near the top of your first reply. Something
  like: *"ACC HUD isn't running — want me to start it?"*
- Do not start it unless the user says yes. Starting a background server nobody
  asked for is exactly the kind of surprise this design avoids.
- If they decline, drop it. Do not raise it again for the rest of the session.
- Never let this displace the user's actual request. Answer what they asked first
  or alongside; the HUD offer is a one-line aside, not the response.

When the hook says the HUD **is** running, say nothing about starting it.

## Asking at goodbye

When the user signs off — "bye", "goodbye", "ciao", "see you", "good night",
"talk later", "have a nice day" — and the HUD is running:

- Ask once whether to stop it, then act on the answer.
- Check first with `"$ACC" status`; if it is already down, say nothing.
- Only run `"$ACC" stop` if they say yes. Leaving it running is a perfectly normal
  choice — it is a background dashboard, not a leak.

Fold this into the existing session-end routine rather than making it a separate
exchange: save memory, then ask about the HUD in the same message.

## Opening the dashboard

`"$ACC" open` launches the browser. If the daemon is down it will say so rather
than opening a dead page — offer to start it first.

The dashboard routes in the URL hash as `#/<sessionId>/<view>` where view is
`flow`, `graph` or `term`, so you can deep-link to a specific session and view.

## Turning the prompt off

If the user is tired of being asked at startup:

```bash
touch ~/.acc/no-prompt      # silences the SessionStart offer
rm ~/.acc/no-prompt         # re-enables it
```

Mention this if they decline the offer more than once.

## Notes

- The daemon binds `127.0.0.1` only and has no auth. It is a local tool.
- Starting the HUD does **not** attach it to Claude Code. Attaching (`acc attach`)
  registers hooks in `settings.json`; that is a separate, more invasive step —
  always ask before running it.
- A stopped HUD breaks nothing. Its hook bridge fails open, so agents keep working
  whether or not the daemon is up.
