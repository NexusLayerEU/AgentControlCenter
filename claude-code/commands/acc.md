---
description: Open, start, stop or check the ACC HUD (Agent Control Center dashboard)
argument-hint: "[open|start|stop|restart|status|attach|detach]"
allowed-tools: Bash(*), Read
---

# ACC HUD

Control the Agent Control Center dashboard at http://127.0.0.1:4000.

**Requested action:** `$ARGUMENTS` — if empty, treat it as **open**.

## Do this

1. Resolve the launcher (do not hardcode a path):

```bash
ACC=$(command -v acc || ls "$ACC_BIN" ~/.acc/app/bin/acc ~/.local/bin/acc \
      /usr/local/bin/acc 2>/dev/null | head -1)
echo "launcher: ${ACC:-NOT FOUND}"
```

   If nothing is found, tell the user ACC is not installed and stop.

2. Run the action:

   - **open** (default) — check `"$ACC" status` first. If the daemon is down, start
     it, then open. If it is up, just open:
     ```bash
     "$ACC" status >/dev/null 2>&1 || "$ACC" start
     "$ACC" open
     ```
   - **start** / **stop** / **restart** / **status** — run `"$ACC" <action>` directly.
   - **attach** / **detach** — this edits Claude Code's `settings.json`. **Confirm
     with the user before running it**, and say which scope it will touch (global by
     default; pass a directory for project scope).

3. Report the outcome in one or two lines: whether the daemon is up, on which port,
   and the dashboard URL. On failure, show the real error rather than a summary —
   `"$ACC" logs` tails the daemon log if more detail is needed.

Never leave the user guessing whether the action actually worked; confirm from
`status`, not from the fact that a command exited.
