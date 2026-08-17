#!/usr/bin/env bash
#
# Agent Control Center launcher (macOS / Linux).
#
# Prefers the JRE bundled next to this script; falls back to a system Java 17+
# so the universal package works too.
set -uo pipefail

# Resolve symlinks so `acc` can live on the PATH while the app lives elsewhere.
SOURCE="${BASH_SOURCE[0]}"
while [ -L "$SOURCE" ]; do
  DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
APP="$(cd -P "$(dirname "$SOURCE")/.." && pwd)"

JAR="$APP/lib/acc-daemon.jar"
PORT="${ACC_PORT:-4000}"
BASE="http://127.0.0.1:${PORT}"
ACC_HOME="${ACC_HOME:-$HOME/.acc}"
LOG="$ACC_HOME/daemon.log"

info() { printf '\033[38;5;148m%s\033[0m\n' "$*"; }
die()  { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }

# Java 17+ is required. Each candidate is version-checked rather than trusted,
# because an inherited JAVA_HOME very often points at an older JDK while a newer
# one is installed and perfectly discoverable.
java_version() {
  [ -x "$1" ] || [ "$1" = "java" ] || return 1
  "$1" -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9]*\).*/\1/p'
}

usable() {
  local v
  v="$(java_version "$1" 2>/dev/null)" || return 1
  [ -n "$v" ] && [ "$v" -ge 17 ] 2>/dev/null
}

find_java() {
  local candidates=()
  [ -x "$APP/runtime/bin/java" ] && candidates+=("$APP/runtime/bin/java")
  [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && candidates+=("$JAVA_HOME/bin/java")
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local home
    for v in 21 17; do
      home="$(/usr/libexec/java_home -v "$v" 2>/dev/null || true)"
      [ -n "$home" ] && candidates+=("$home/bin/java")
    done
  fi
  command -v java >/dev/null 2>&1 && candidates+=("$(command -v java)")

  for candidate in "${candidates[@]:-}"; do
    [ -n "$candidate" ] && usable "$candidate" && { echo "$candidate"; return; }
  done
  echo ""
}

JAVA="$(find_java)"
if [ -z "$JAVA" ]; then
  FOUND=""
  [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] \
    && FOUND=" (JAVA_HOME points at Java $(java_version "$JAVA_HOME/bin/java"))"
  die "No Java 17+ found${FOUND}. Install a JDK 17+, or use a platform bundle that includes a runtime."
fi

is_up() { curl -s --max-time 2 "$BASE/api/system/status" >/dev/null 2>&1; }

case "${1:-status}" in
  start)
    [ -f "$JAR" ] || die "missing $JAR"
    if is_up; then info "already running on :$PORT"; exit 0; fi
    mkdir -p "$ACC_HOME"
    nohup "$JAVA" -jar "$JAR" >"$LOG" 2>&1 &
    for _ in $(seq 1 45); do
      if is_up; then info "ACC up on $BASE"; exit 0; fi
      sleep 1
    done
    die "daemon did not start; see $LOG"
    ;;
  stop)
    pkill -f 'acc-daemon\.jar' >/dev/null 2>&1 && info "stopped" || info "not running"
    ;;
  restart) "$0" stop; sleep 1; exec "$0" start ;;
  status)
    is_up || { info "daemon: down"; exit 1; }
    curl -s "$BASE/api/system/status"; echo
    ;;
  open)
    is_up || die "daemon is not running — try: acc start"
    command -v open >/dev/null 2>&1 && open "$BASE" || xdg-open "$BASE" >/dev/null 2>&1 || info "$BASE"
    ;;
  attach)
    curl -s -X POST "$BASE/api/hooks/install" -H 'Content-Type: application/json' \
      -d "{\"projectScope\":$([ -n "${2:-}" ] && echo true || echo false),\"projectDir\":\"${2:-}\"}"; echo
    ;;
  detach)
    curl -s -X POST "$BASE/api/hooks/uninstall" -H 'Content-Type: application/json' \
      -d "{\"projectScope\":$([ -n "${2:-}" ] && echo true || echo false),\"projectDir\":\"${2:-}\"}"; echo
    ;;
  run)
    [ -n "${2:-}" ] || die 'usage: acc run "<task>" [dir] [default|acceptEdits|plan|bypassPermissions]'
    python3 - "$BASE" "$2" "${3:-$(pwd)}" "${4:-default}" <<'PY'
import json, sys, urllib.request
base, prompt, cwd, mode = sys.argv[1:5]
body = json.dumps({"prompt": prompt, "cwd": cwd, "permissionMode": mode}).encode()
req = urllib.request.Request(f"{base}/api/sessions", data=body,
                             headers={"Content-Type": "application/json"}, method="POST")
with urllib.request.urlopen(req, timeout=15) as r:
    s = json.load(r)
print(f"session {s['id']}  mode={s['permissionMode']}  auto-approve={s['autoApprove']}")
print(f"watch it at {base}")
PY
    ;;
  logs) tail -f "$LOG" ;;
  version) info "Agent Control Center 0.3.0"; "$JAVA" -version 2>&1 | head -1 ;;
  *)
    cat <<EOF
Agent Control Center

  acc start | stop | restart | status | open
  acc attach [dir]     register ACC's hooks in Claude Code
  acc detach [dir]     remove them
  acc run "<task>" [dir] [mode]
  acc logs | version

Dashboard: $BASE
EOF
    ;;
esac
