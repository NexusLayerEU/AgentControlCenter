#!/usr/bin/env bash
#
# Installs Agent Control Center for the current user.
#
#   ./install.sh              install to ~/.acc/app, link into ~/.local/bin
#   ./install.sh --prefix DIR install somewhere else
#   ./install.sh --system     install to /usr/local (needs sudo)
#
# Nothing is written outside the prefix and the bin directory, and no service is
# registered — ACC is started on demand with `acc start`.
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PREFIX="$HOME/.acc/app"
BINDIR="$HOME/.local/bin"

while [ $# -gt 0 ]; do
  case "$1" in
    --prefix) PREFIX="${2:?--prefix needs a directory}"; shift 2 ;;
    --system) PREFIX="/usr/local/lib/acc"; BINDIR="/usr/local/bin"; shift ;;
    --bindir) BINDIR="${2:?--bindir needs a directory}"; shift 2 ;;
    -h|--help) sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

info() { printf '\033[38;5;148m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[33mwarning:\033[0m %s\n' "$*"; }
die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

[ -f "$SRC/lib/acc-daemon.jar" ] || die "run this from inside the unpacked ACC directory"

# Refuse to clobber a directory that is not a previous ACC install.
if [ -e "$PREFIX" ] && [ ! -f "$PREFIX/lib/acc-daemon.jar" ]; then
  die "$PREFIX exists and does not look like an ACC install — remove it or pass --prefix"
fi

if [ -d "$PREFIX" ]; then
  info "upgrading existing install at $PREFIX"
  "$PREFIX/bin/acc" stop >/dev/null 2>&1 || true
fi

info "installing to $PREFIX"
mkdir -p "$PREFIX"
rm -rf "$PREFIX/lib" "$PREFIX/bin" "$PREFIX/runtime"
mkdir -p "$PREFIX/lib" "$PREFIX/bin"
cp "$SRC/lib/acc-daemon.jar" "$PREFIX/lib/"
cp "$SRC/bin/acc" "$PREFIX/bin/acc"
chmod +x "$PREFIX/bin/acc"
[ -f "$SRC/README.md" ] && cp "$SRC/README.md" "$PREFIX/"
# Ship the uninstaller with the install so it survives deleting the download.
cp "$SRC/uninstall.sh" "$PREFIX/uninstall.sh"
chmod +x "$PREFIX/uninstall.sh"

if [ -d "$SRC/runtime" ]; then
  info "installing bundled Java runtime"
  cp -R "$SRC/runtime" "$PREFIX/runtime"
  chmod +x "$PREFIX/runtime/bin/"* 2>/dev/null || true
  # macOS quarantines everything from a downloaded archive; strip it so the
  # bundled runtime is allowed to execute without a Gatekeeper prompt.
  if [ "$(uname -s)" = "Darwin" ] && command -v xattr >/dev/null 2>&1; then
    xattr -dr com.apple.quarantine "$PREFIX" 2>/dev/null || true
  fi
else
  info "no bundled runtime in this package — ACC will use your system Java 17+"
fi

mkdir -p "$BINDIR"
ln -sf "$PREFIX/bin/acc" "$BINDIR/acc"
info "linked $BINDIR/acc"

case ":${PATH}:" in
  *":$BINDIR:"*) ;;
  *) warn "$BINDIR is not on your PATH. Add this to your shell profile:"
     printf '\n    export PATH="%s:$PATH"\n\n' "$BINDIR" ;;
esac

if ! command -v claude >/dev/null 2>&1; then
  warn "Claude Code was not found on PATH. ACC needs it to launch agents."
fi

cat <<EOF

Installed. Next:

    acc start          # daemon on http://127.0.0.1:4000
    acc attach         # register ACC's hooks in Claude Code
    acc open           # open the dashboard

Uninstall with:

    $PREFIX/uninstall.sh --prefix "$PREFIX" --bindir "$BINDIR"
EOF
