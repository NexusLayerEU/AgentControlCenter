#!/usr/bin/env bash
#
# SessionStart hook for Agent Control Center.
#
# Tells Claude whether the local ACC daemon is running, so it can offer to start
# it once at the beginning of a session — and knows to offer stopping it again
# when you say goodbye.
#
# Design rules:
#   * Silent unless ACC is actually installed. Nobody without ACC gets nagged.
#   * Never starts anything itself. A hook that launches a background server
#     without being asked would be a surprise; the offer belongs to the user.
#   * Always exits 0. A broken check must never stop a session from starting.
#
# Opt out permanently:  touch ~/.acc/no-prompt
# Override the launcher: export ACC_BIN=/path/to/acc
set -uo pipefail

PORT="${ACC_PORT:-4000}"
BASE="http://127.0.0.1:${PORT}"
ACC_HOME="${ACC_HOME:-$HOME/.acc}"

[ -f "$ACC_HOME/no-prompt" ] && exit 0

# Locate the launcher: an explicit override, then PATH, then the standard
# install locations. Deliberately no repo-relative guesses.
# Read the override BEFORE reusing the variable, or it is wiped.
ACC_OVERRIDE="${ACC_BIN:-}"
ACC_BIN=""
for candidate in \
  "$ACC_OVERRIDE" \
  "$(command -v acc 2>/dev/null || true)" \
  "$ACC_HOME/app/bin/acc" \
  "$HOME/.local/bin/acc" \
  "/usr/local/bin/acc"
do
  if [ -n "$candidate" ] && [ -x "$candidate" ]; then
    ACC_BIN="$candidate"
    break
  fi
done

# ACC is not installed on this machine — stay completely quiet.
[ -z "$ACC_BIN" ] && exit 0

emit() {
  # additionalContext is injected into the model's context for this session.
  python3 -c '
import json, sys
print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "SessionStart",
        "additionalContext": sys.argv[1],
    }
}))' "$1" 2>/dev/null || true
}

if curl -s --max-time 2 "$BASE/api/system/status" >/dev/null 2>&1; then
  emit "ACC HUD is RUNNING at ${BASE} (launcher: ${ACC_BIN}).
Do not offer to start it. If the user says goodbye or otherwise ends the session,
ask once whether they want to stop the HUD, and run '${ACC_BIN} stop' only if they say yes."
else
  emit "ACC HUD is NOT running (launcher: ${ACC_BIN}, would serve ${BASE}).
At the start of your next reply, ask the user — briefly, in one line — whether they
want to start the HUD. Only run '${ACC_BIN} start' if they say yes; never start it
unprompted. If they decline, do not ask again this session.
If the user later says goodbye, ask once whether to stop it (only if it is running)."
fi

exit 0
