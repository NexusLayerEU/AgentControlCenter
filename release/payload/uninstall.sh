#!/usr/bin/env bash
#
# Removes Agent Control Center. Session history in ~/.acc is kept unless --purge
# is given, because it is the only record of what your agents did.
set -euo pipefail

PREFIX="$HOME/.acc/app"
BINDIR="$HOME/.local/bin"
PURGE=0

while [ $# -gt 0 ]; do
  case "$1" in
    --prefix) PREFIX="${2:?}"; shift 2 ;;
    --bindir) BINDIR="${2:?}"; shift 2 ;;
    --system) PREFIX="/usr/local/lib/acc"; BINDIR="/usr/local/bin"; shift ;;
    --purge)  PURGE=1; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

info() { printf '\033[38;5;148m==>\033[0m %s\n' "$*"; }

if [ -x "$PREFIX/bin/acc" ]; then
  info "stopping daemon"
  "$PREFIX/bin/acc" stop >/dev/null 2>&1 || true
  info "removing ACC's hooks from Claude Code"
  curl -s --max-time 3 -X POST "http://127.0.0.1:${ACC_PORT:-4000}/api/hooks/uninstall" \
    -H 'Content-Type: application/json' -d '{"projectScope":false}' >/dev/null 2>&1 || true
fi

[ -L "$BINDIR/acc" ] && rm -f "$BINDIR/acc" && info "removed $BINDIR/acc"
[ -d "$PREFIX" ] && rm -rf "$PREFIX" && info "removed $PREFIX"

if [ "$PURGE" -eq 1 ]; then
  rm -rf "$HOME/.acc"
  info "purged ~/.acc (history, logs and the hook bridge)"
else
  echo
  echo "Kept ~/.acc (session history and logs). Remove it with --purge."
  echo "Note: if the hook bridge is still referenced in ~/.claude/settings.json,"
  echo "the entries fail open and are harmless, but you can delete them by hand."
fi
