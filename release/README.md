# ACC 0.2.0 — Release artifacts

Self-contained packages for Agent Control Center. Each platform bundle ships a
**Temurin JRE 21** in `runtime/`, so the target machine needs no Java installed —
only [Claude Code](https://claude.com/claude-code) on its PATH.

## Which one do I want?

| Package | For | Java needed |
|---|---|---|
| `acc-0.2.0-macos-aarch64.tar.gz` | macOS, Apple Silicon (M1–M4) | no |
| `acc-0.2.0-macos-x64.tar.gz` | macOS, Intel | no |
| `acc-0.2.0-linux-x64.tar.gz` | Linux, x86-64 | no |
| `acc-0.2.0-linux-aarch64.tar.gz` | Linux, ARM64 | no |
| `acc-0.2.0-windows-x64.zip` | Windows 10/11, x86-64 | no |
| `acc-0.2.0-universal.zip` | any other OS/arch | **JDK 17+** |

Verify a download against `checksums.txt`:

```bash
shasum -a 256 -c checksums.txt --ignore-missing
```

## Install

**macOS / Linux**

```bash
tar -xzf acc-0.2.0-macos-aarch64.tar.gz
cd acc-0.2.0
./install.sh                     # → ~/.acc/app, links ~/.local/bin/acc
```

Options: `--prefix DIR`, `--bindir DIR`, `--system` (installs to `/usr/local`,
needs sudo).

**Windows** — no admin rights required:

```
Expand-Archive acc-0.2.0-windows-x64.zip -DestinationPath .
cd acc-0.2.0
.\install.bat                    # → %LOCALAPPDATA%\ACC, adds it to your PATH
```

Then, in a **new** terminal:

```
acc start      # daemon on http://127.0.0.1:4000
acc attach     # register ACC's hooks in Claude Code
acc open       # open the dashboard
```

## Uninstall

```bash
~/.acc/app/uninstall.sh              # keeps session history in ~/.acc
~/.acc/app/uninstall.sh --purge      # removes it too
```

Windows: `%LOCALAPPDATA%\ACC\uninstall.bat` (add `--purge`). Both stop the
daemon, remove ACC's hooks from Claude Code, and take the PATH entry back out.

## Rebuilding these artifacts

```bash
./release/build-release.sh                 # full build + all packages
./release/build-release.sh --skip-build    # repackage an existing jar
./release/build-release.sh --no-runtimes   # skip the bundled JREs
```

JREs are cached in `release/.jre-cache/`, so only the first run downloads.

The build runs `mvn clean package` deliberately. Maven copies resources into
`target/classes` but never removes ones deleted from source, so without `clean`
every superseded dashboard bundle stays in the jar — 0.2.0 was briefly packaged
with 7.9 MB of dead JS/CSS for exactly that reason. The build now fails if the
packaged asset count does not match what Vite just produced.

## What's new

See [CHANGELOG.md](CHANGELOG.md). 0.2.0 adds the overview dashboard, two themes,
working Windows support, and a fix for the approval gate asking twice.

## What was verified, and what was not

Built and tested on macOS (Apple Silicon):

- The **macos-aarch64** bundle: unpacked, installed, and started **with system
  Java removed from the environment entirely** — proving the bundled runtime
  works — then served the overview dashboard and the stats API, and uninstalled
  cleanly with no residue.
- The shipped jar contains exactly the two assets the current dashboard build
  produced, and nothing else.
- The **universal** package: installed, correctly *skipped* a stale `JAVA_HOME`
  pointing at Java 11 and found JDK 21, started and served.
- All five platform bundles: confirmed each `runtime/bin/java` is a real binary
  for that architecture (Mach-O arm64 / x86_64, ELF x86-64 / aarch64, PE32+).
- **69 backend tests** pass, including the approval gate under real concurrent
  waits.

Not executed here, because this is a macOS machine:

- **Windows** — `acc.bat`, `install.bat`, `uninstall.bat` and the PowerShell
  hook bridge are written and structurally checked, but have not been run on
  Windows.
- **Linux** — the launcher and installer are the same shell scripts verified on
  macOS, and the runtime is the correct ELF binary, but no Linux run was done.

Treat Windows and Linux as untested-in-the-field until someone runs them.
