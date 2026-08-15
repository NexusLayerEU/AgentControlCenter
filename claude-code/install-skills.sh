#!/usr/bin/env bash
#
# Installs the ACC integration for Claude Code:
#
#   skill    acc-hud   — start / stop / open the HUD, and the rules for when to ask
#   command  /acc      — control the HUD from a slash command
#   hook     SessionStart — offers to start the HUD if it is not running
#
# Everything lands in ~/.claude/. Your existing hooks are preserved: this only
# ever adds or replaces ACC's own entry, matched by its command path.
#
#   ./install-skills.sh              install
#   ./install-skills.sh --uninstall  remove
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLAUDE_DIR="${CLAUDE_CONFIG_DIR:-$HOME/.claude}"
HOOK_DEST="$CLAUDE_DIR/scripts/acc-hud-session-start.sh"
SETTINGS="$CLAUDE_DIR/settings.json"
UNINSTALL=0

for arg in "$@"; do
  case "$arg" in
    --uninstall) UNINSTALL=1 ;;
    -h|--help) sed -n '2,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

info() { printf '\033[38;5;148m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[33mwarning:\033[0m %s\n' "$*"; }
die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

command -v python3 >/dev/null 2>&1 || die "python3 is required to edit settings.json safely"

# ── settings.json surgery ───────────────────────────────────────────────────
# Done in Python rather than sed/jq so the file is parsed and re-serialised
# properly. A corrupted settings.json silently disables ALL of your settings.
merge_hook() {
  python3 - "$SETTINGS" "$HOOK_DEST" "$1" <<'PY'
import json, os, sys, shutil

settings_path, hook_cmd, action = sys.argv[1], sys.argv[2], sys.argv[3]

data = {}
if os.path.exists(settings_path) and os.path.getsize(settings_path) > 0:
    try:
        with open(settings_path) as fh:
            data = json.load(fh)
    except json.JSONDecodeError as e:
        sys.exit(f"settings.json is not valid JSON ({e}); refusing to touch it")
    if not isinstance(data, dict):
        sys.exit("settings.json is not a JSON object; refusing to touch it")
    shutil.copy2(settings_path, settings_path + ".acc-backup")

hooks = data.setdefault("hooks", {})
entries = hooks.get("SessionStart", [])

def is_ours(entry):
    return any("acc-hud-session-start" in h.get("command", "")
               for h in entry.get("hooks", []))

kept = [e for e in entries if not is_ours(e)]

if action == "install":
    kept.append({"hooks": [{"type": "command", "command": hook_cmd, "timeout": 10}]})

if kept:
    hooks["SessionStart"] = kept
else:
    hooks.pop("SessionStart", None)
if not hooks:
    data.pop("hooks", None)

os.makedirs(os.path.dirname(settings_path), exist_ok=True)
with open(settings_path, "w") as fh:
    json.dump(data, fh, indent=2)
    fh.write("\n")

print(f"  SessionStart hooks now: {len(kept)}")
PY
}

if [ "$UNINSTALL" -eq 1 ]; then
  info "removing the ACC integration"
  rm -f "$CLAUDE_DIR/skills/acc-hud/SKILL.md"
  rmdir "$CLAUDE_DIR/skills/acc-hud" 2>/dev/null || true
  rm -f "$CLAUDE_DIR/commands/acc.md"
  rm -f "$HOOK_DEST"
  [ -f "$SETTINGS" ] && merge_hook uninstall
  info "removed. Restart Claude Code to pick up the change."
  exit 0
fi

[ -f "$SRC/skills/acc-hud/SKILL.md" ] || die "run this from inside the claude-code/ directory"

info "installing into $CLAUDE_DIR"
mkdir -p "$CLAUDE_DIR/skills/acc-hud" "$CLAUDE_DIR/commands" "$CLAUDE_DIR/scripts"

cp "$SRC/skills/acc-hud/SKILL.md" "$CLAUDE_DIR/skills/acc-hud/SKILL.md"
info "skill    acc-hud"

cp "$SRC/commands/acc.md" "$CLAUDE_DIR/commands/acc.md"
info "command  /acc"

cp "$SRC/hooks/acc-hud-session-start.sh" "$HOOK_DEST"
chmod +x "$HOOK_DEST"
info "hook     SessionStart"

merge_hook install

# Prove the hook actually runs before claiming success.
if echo '{}' | "$HOOK_DEST" >/dev/null 2>&1; then
  info "hook verified (exits cleanly)"
else
  warn "the hook script did not exit cleanly — check $HOOK_DEST"
fi

command -v acc >/dev/null 2>&1 \
  || warn "'acc' is not on your PATH yet. The hook stays silent until ACC is installed."

cat <<EOF

Installed. Restart Claude Code (hooks are read when a session starts).

Then:
  /acc              open the dashboard
  /acc status       is the daemon up?
  /acc start|stop   control it

At the start of a session, Claude will offer to start the HUD if it is not
running, and offer to stop it when you say goodbye.

Silence the startup offer:  touch ~/.acc/no-prompt
Remove everything:          ./install-skills.sh --uninstall
EOF
