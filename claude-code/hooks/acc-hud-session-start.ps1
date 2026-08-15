# SessionStart hook for Agent Control Center (Windows).
#
# Tells Claude whether the local ACC daemon is running, so it can offer to start
# it once at the beginning of a session — and knows to offer stopping it again
# when you say goodbye.
#
# Rules: silent unless ACC is installed, never starts anything itself, always
# exits 0 so a broken check cannot stop a session from starting.
#
# Opt out permanently:   New-Item $env:USERPROFILE\.acc\no-prompt
# Override the launcher: $env:ACC_BIN = 'C:\path\to\acc.bat'

$ErrorActionPreference = 'SilentlyContinue'

$port    = if ($env:ACC_PORT) { $env:ACC_PORT } else { '4000' }
$base    = "http://127.0.0.1:$port"
$accHome = if ($env:ACC_HOME) { $env:ACC_HOME } else { Join-Path $env:USERPROFILE '.acc' }

if (Test-Path (Join-Path $accHome 'no-prompt')) { exit 0 }

# Locate the launcher: explicit override, then PATH, then standard locations.
$accBin = $null
$candidates = @(
    $env:ACC_BIN,
    (Get-Command acc -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1),
    (Join-Path $env:LOCALAPPDATA 'ACC\bin\acc.bat'),
    (Join-Path $accHome 'app\bin\acc.bat')
)
foreach ($c in $candidates) {
    if ($c -and (Test-Path $c)) { $accBin = $c; break }
}

# ACC is not installed on this machine — stay completely quiet.
if (-not $accBin) { exit 0 }

function Emit([string]$text) {
    @{ hookSpecificOutput = @{
        hookEventName    = 'SessionStart'
        additionalContext = $text
    } } | ConvertTo-Json -Depth 5 -Compress
}

$running = $false
try {
    Invoke-WebRequest -Uri "$base/api/system/status" -TimeoutSec 2 -UseBasicParsing | Out-Null
    $running = $true
} catch { $running = $false }

if ($running) {
    Emit @"
ACC HUD is RUNNING at $base (launcher: $accBin).
Do not offer to start it. If the user says goodbye or otherwise ends the session,
ask once whether they want to stop the HUD, and run '$accBin stop' only if they say yes.
"@
} else {
    Emit @"
ACC HUD is NOT running (launcher: $accBin, would serve $base).
At the start of your next reply, ask the user — briefly, in one line — whether they
want to start the HUD. Only run '$accBin start' if they say yes; never start it
unprompted. If they decline, do not ask again this session.
If the user later says goodbye, ask once whether to stop it (only if it is running).
"@
}

exit 0
