#!/usr/bin/env bash
#
# Builds the ACC release artifacts.
#
# Produces one self-contained bundle per platform (application + a bundled
# Temurin JRE, so the target machine needs no Java at all), plus a small
# universal package for anyone who already has a JDK or runs an architecture
# not covered below.
#
# Usage:  ./release/build-release.sh [--skip-build] [--no-runtimes]
set -euo pipefail

VERSION="0.3.0"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/release"
WORK="$OUT/.work"
CACHE="$OUT/.jre-cache"
JAR="$ROOT/backend/target/acc-daemon-${VERSION}.jar"

SKIP_BUILD=0
NO_RUNTIMES=0
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    --no-runtimes) NO_RUNTIMES=1 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

# platform-label : adoptium-os : adoptium-arch : archive-kind
TARGETS=(
  "macos-aarch64:mac:aarch64:tar.gz"
  "macos-x64:mac:x64:tar.gz"
  "linux-x64:linux:x64:tar.gz"
  "linux-aarch64:linux:aarch64:tar.gz"
  "windows-x64:windows:x64:zip"
)

info() { printf '\033[38;5;148m==>\033[0m %s\n' "$*"; }
die()  { printf '\033[31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# ── 1. Build the application ────────────────────────────────────────────────
if [ "$SKIP_BUILD" -eq 0 ]; then
  info "building dashboard"
  (cd "$ROOT/frontend" && npm install --silent && npm run build >/dev/null) \
    || die "frontend build failed"

  info "building daemon (runs the test suite)"
  # An inherited JAVA_HOME may point at a JDK too old for Spring Boot 3, so the
  # version is checked rather than just its presence.
  jdk_ok() {
    [ -n "${1:-}" ] && [ -x "$1/bin/java" ] || return 1
    local v
    v="$("$1/bin/java" -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')"
    [ -n "$v" ] && [ "$v" -ge 17 ] 2>/dev/null
  }
  if ! jdk_ok "${JAVA_HOME:-}"; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
      JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)"
    fi
    jdk_ok "${JAVA_HOME:-}" || die "no JDK 17+ found — set JAVA_HOME"
    export JAVA_HOME
    info "using JDK at $JAVA_HOME"
  fi
  # `clean` matters: Maven copies resources into target/classes but never removes
  # ones deleted from source, so every superseded dashboard bundle would otherwise
  # be packaged into all six artifacts.
  (cd "$ROOT/backend" && mvn -B -q clean package) || die "backend build failed"

  # Guard against the drift returning: what ships must equal what Vite just built.
  PACKAGED=$(find "$ROOT/backend/target/classes/static/assets" -type f 2>/dev/null | wc -l | tr -d ' ')
  BUILT=$(find "$ROOT/backend/src/main/resources/static/assets" -type f 2>/dev/null | wc -l | tr -d ' ')
  [ "$PACKAGED" = "$BUILT" ] \
    || die "packaged assets ($PACKAGED) do not match the dashboard build ($BUILT)"
  info "dashboard assets verified ($BUILT files)"
fi
[ -f "$JAR" ] || die "missing $JAR — run without --skip-build"

rm -rf "$WORK"; mkdir -p "$WORK" "$CACHE"

# ── 2. Fetch a JRE per platform ─────────────────────────────────────────────
fetch_jre() {
  local os="$1" arch="$2" kind="$3"
  local file="$CACHE/jre-21-${os}-${arch}.${kind}"
  if [ -s "$file" ]; then
    echo "$file"; return
  fi
  local url="https://api.adoptium.net/v3/binary/latest/21/ga/${os}/${arch}/jre/hotspot/normal/eclipse"
  info "downloading Temurin JRE 21 for ${os}/${arch}" >&2
  curl -sSL --fail --retry 3 -o "$file.tmp" "$url" || { rm -f "$file.tmp"; return 1; }
  mv "$file.tmp" "$file"
  echo "$file"
}

# Unpacks a JRE archive and returns the directory that directly contains bin/.
extract_jre() {
  local archive="$1" dest="$2"
  rm -rf "$dest"; mkdir -p "$dest"
  case "$archive" in
    *.zip)    (cd "$dest" && unzip -q "$archive") ;;
    *.tar.gz) tar -xzf "$archive" -C "$dest" ;;
  esac
  # Temurin nests everything under one top-level dir; macOS adds Contents/Home.
  local top
  top="$(find "$dest" -maxdepth 1 -mindepth 1 -type d | head -1)"
  if [ -d "$top/Contents/Home" ]; then
    echo "$top/Contents/Home"
  else
    echo "$top"
  fi
}

# ── 3. Assemble a bundle ────────────────────────────────────────────────────
assemble() {
  local label="$1" os="$2" arch="$3" kind="$4" with_runtime="$5"
  local name="acc-${VERSION}-${label}"
  local dir="$WORK/$name/acc-${VERSION}"
  mkdir -p "$dir/bin" "$dir/lib"

  cp "$JAR" "$dir/lib/acc-daemon.jar"
  cp "$OUT/payload/README.md" "$dir/README.md"

  # The universal package targets no single OS, so it carries both launchers.
  if [ "$os" != "windows" ] || [ "$label" = "universal" ]; then
    cp "$OUT/payload/acc.sh" "$dir/bin/acc"
    cp "$OUT/payload/install.sh" "$dir/install.sh"
    cp "$OUT/payload/uninstall.sh" "$dir/uninstall.sh"
    chmod +x "$dir/bin/acc" "$dir/install.sh" "$dir/uninstall.sh"
  fi
  if [ "$os" = "windows" ] || [ "$label" = "universal" ]; then
    cp "$OUT/payload/acc.bat" "$dir/bin/acc.bat"
    cp "$OUT/payload/install.bat" "$dir/install.bat"
    cp "$OUT/payload/uninstall.bat" "$dir/uninstall.bat"
  fi

  if [ "$with_runtime" = "yes" ]; then
    local archive home
    archive="$(fetch_jre "$os" "$arch" "$kind")" || {
      printf '\033[33mWARN:\033[0m no JRE for %s/%s — skipping %s\n' "$os" "$arch" "$label" >&2
      rm -rf "$WORK/$name"; return 1
    }
    home="$(extract_jre "$archive" "$WORK/.jre-$label")"
    cp -R "$home" "$dir/runtime"
    # Restore the exec bits cp can drop across filesystems.
    chmod -R u+rwX "$dir/runtime"
    [ -d "$dir/runtime/bin" ] && chmod +x "$dir/runtime/bin/"* 2>/dev/null || true
    rm -rf "$WORK/.jre-$label"
  fi

  ( cd "$WORK/$name"
    if [ "$kind" = "zip" ]; then
      zip -qr "$OUT/$name.zip" "acc-${VERSION}"
    else
      tar -czf "$OUT/$name.tar.gz" "acc-${VERSION}"
    fi )
  info "packaged $name"
}

rm -f "$OUT"/acc-${VERSION}-*.tar.gz "$OUT"/acc-${VERSION}-*.zip "$OUT/checksums.txt"

for target in "${TARGETS[@]}"; do
  IFS=: read -r label os arch kind <<<"$target"
  if [ "$NO_RUNTIMES" -eq 1 ]; then
    assemble "$label" "$os" "$arch" "$kind" "no" || true
  else
    assemble "$label" "$os" "$arch" "$kind" "yes" || true
  fi
done

# Universal: no runtime, runs on any OS with a JDK 17+.
assemble "universal" "posix" "any" "zip" "no" || true

# ── 4. Checksums ────────────────────────────────────────────────────────────
( cd "$OUT" && shasum -a 256 acc-${VERSION}-*.tar.gz acc-${VERSION}-*.zip 2>/dev/null > checksums.txt ) || true
rm -rf "$WORK"

info "artifacts in $OUT"
( cd "$OUT" && ls -lh acc-${VERSION}-* 2>/dev/null | awk '{print "   ", $9, $5}' )
