# Installing Agent Control Center

Every platform bundle ships a **Temurin JRE 21** inside it, so you do not need
Java installed. The only real requirement is
[Claude Code](https://claude.com/claude-code) on your `PATH` — ACC watches it, it
does not replace it.

---

## 1. Pick your download

From the [Releases page](https://github.com/NexusLayerEU/AgentControlCenter/releases):

| You are on | Download | Java needed |
|---|---|---|
| macOS, Apple Silicon (M1–M4) | `acc-0.2.0-macos-aarch64.tar.gz` | no |
| macOS, Intel | `acc-0.2.0-macos-x64.tar.gz` | no |
| Linux, x86-64 | `acc-0.2.0-linux-x64.tar.gz` | no |
| Linux, ARM64 | `acc-0.2.0-linux-aarch64.tar.gz` | no |
| Windows 10/11, x86-64 | `acc-0.2.0-windows-x64.zip` | no |
| Anything else | `acc-0.2.0-universal.zip` | **JDK 17+** |

Not sure which Mac you have? `uname -m` → `arm64` is Apple Silicon, `x86_64` is
Intel.

### Verify the download (optional)

```bash
shasum -a 256 -c checksums.txt --ignore-missing
```

---

## 2. Install

### macOS / Linux

```bash
tar -xzf acc-0.2.0-macos-aarch64.tar.gz
cd acc-0.2.0
./install.sh
```

That installs to `~/.acc/app` and links `acc` into `~/.local/bin`. No sudo, and
nothing is written outside your home directory.

Options:

| Flag | Effect |
|---|---|
| `--prefix DIR` | Install somewhere else |
| `--bindir DIR` | Put the `acc` link somewhere else |
| `--system` | Install to `/usr/local` (needs sudo) |

If the installer warns that `~/.local/bin` is not on your `PATH`, add this to your
shell profile:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

> **macOS Gatekeeper:** the installer strips the quarantine flag from the bundled
> runtime automatically. If you moved files around by hand first and macOS
> complains, run `xattr -dr com.apple.quarantine ~/.acc/app`.

### Windows

No admin rights required.

```powershell
Expand-Archive acc-0.2.0-windows-x64.zip -DestinationPath .
cd acc-0.2.0
.\install.bat
```

Installs to `%LOCALAPPDATA%\ACC` and adds it to your user `PATH`. **Open a new
terminal** afterwards so the `PATH` change takes effect.

---

## 3. First run

```bash
acc start      # daemon on http://127.0.0.1:4000
acc open       # opens the dashboard
```

You should see the overview dashboard. It will be empty — that is expected.

```bash
acc run "list the files here and summarise the project" ~/some/project
```

Watch it appear live.

---

## 4. Recording your own Claude Code sessions

By default ACC only sees agents it launched. To have it record the Claude Code
sessions you run yourself:

```bash
acc attach            # registers hooks in ~/.claude/settings.json
acc attach ~/project  # or just for one project
```

**Restart any Claude Code session that is already open** — hooks are read when a
session starts.

This edits Claude Code's `settings.json`. ACC tags its own entries and only ever
replaces those, so hooks you already had are preserved. Undo at any time:

```bash
acc detach
```

Read [GUIDE.md](GUIDE.md#recording-your-own-sessions) for what attaching actually
does and what it costs (~21 ms per tool call).

---

## 5. Optional — let Claude drive the HUD

A skill, a `/acc` slash command, and a hook that offers to start the HUD when a
session begins:

```bash
cd claude-code && ./install-skills.sh      # Windows: .\install-skills.ps1
```

Restart Claude Code afterwards. See [SKILLS.md](SKILLS.md).

---

## Upgrading

Download the new bundle and run the installer again. It stops the daemon, replaces
the app, and leaves your history in `~/.acc/acc.db` untouched.

---

## Uninstalling

```bash
~/.acc/app/uninstall.sh           # keeps your session history
~/.acc/app/uninstall.sh --purge   # removes ~/.acc entirely
```

Windows: `%LOCALAPPDATA%\ACC\uninstall.bat` (add `--purge`).

Either way it stops the daemon, removes ACC's hooks from Claude Code, and takes
the `PATH` entry back out.

---

## Troubleshooting

**`acc: command not found`** — `~/.local/bin` is not on your `PATH` (see above),
or on Windows you did not open a new terminal.

**`No Java 17+ found`** — you downloaded the `universal` package, which has no
bundled runtime. Either install a JDK 17+ or download your platform's bundle. If
you *have* a modern JDK, `JAVA_HOME` may be pointing at an older one; ACC checks
every candidate it can find, so this usually means none of them is 17+.

**`claude: not found` in the dashboard header** — Claude Code is not on the
`PATH` the daemon inherited. Start the daemon from a shell where `claude --version`
works, or set `ACC_CLAUDE_BIN=/full/path/to/claude`.

**Port 4000 already taken** — `ACC_PORT=4100 acc start`. Re-run `acc attach`
afterwards so the hook bridge points at the new port.

**The daemon will not start** — `acc logs`, or read `~/.acc/daemon.log`.

**Nothing appears when I use Claude Code** — you have not run `acc attach`, or the
session was already open when you attached. Restart it.

---

## Configuration

| Variable | Default | What |
|---|---|---|
| `ACC_PORT` | `4000` | Port for the daemon and dashboard |
| `ACC_HOME` | `~/.acc` | Database, logs and the hook bridge |
| `ACC_CLAUDE_BIN` | `claude` | Path to the Claude Code launcher |

| Path | What |
|---|---|
| `~/.acc/acc.db` | Session history, activity trees, approvals |
| `~/.acc/daemon.log` | Daemon log |
| `~/.acc/logs/` | Per-session stderr |
| `~/.acc/acc-hook.sh` (or `.ps1`) | The generated hook bridge |
