<#
.SYNOPSIS
  Installs the ACC integration for Claude Code on Windows.

.DESCRIPTION
  Adds three things under %USERPROFILE%\.claude:

    skill    acc-hud       start / stop / open the HUD, and when to ask
    command  /acc          control the HUD from a slash command
    hook     SessionStart  offers to start the HUD if it is not running

  Your existing hooks are preserved — only ACC's own entry is added or replaced.

.EXAMPLE
  .\install-skills.ps1
  .\install-skills.ps1 -Uninstall
#>
param([switch]$Uninstall)

$ErrorActionPreference = 'Stop'

$src        = Split-Path -Parent $MyInvocation.MyCommand.Path
$claudeDir  = if ($env:CLAUDE_CONFIG_DIR) { $env:CLAUDE_CONFIG_DIR } else { Join-Path $env:USERPROFILE '.claude' }
$hookDest   = Join-Path $claudeDir 'scripts\acc-hud-session-start.ps1'
$settings   = Join-Path $claudeDir 'settings.json'

function Info($m) { Write-Host "==> $m" -ForegroundColor Green }
function Warn($m) { Write-Host "warning: $m" -ForegroundColor Yellow }

function Update-SessionStartHook([string]$Action) {
    $data = [ordered]@{}
    if (Test-Path $settings) {
        $raw = Get-Content $settings -Raw
        if ($raw.Trim()) {
            try { $data = $raw | ConvertFrom-Json -AsHashtable }
            catch { throw "settings.json is not valid JSON; refusing to touch it" }
            Copy-Item $settings "$settings.acc-backup" -Force
        }
    }
    if (-not $data.ContainsKey('hooks')) { $data['hooks'] = @{} }

    $existing = @()
    if ($data['hooks'].ContainsKey('SessionStart')) { $existing = @($data['hooks']['SessionStart']) }

    # Drop any previous ACC entry, keep everything else untouched.
    $kept = @($existing | Where-Object {
        $isOurs = $false
        foreach ($h in @($_.hooks)) {
            if ($h.command -and $h.command -match 'acc-hud-session-start') { $isOurs = $true }
        }
        -not $isOurs
    })

    if ($Action -eq 'install') {
        $cmd = 'powershell -NoProfile -ExecutionPolicy Bypass -File "' + $hookDest + '"'
        $kept += @{ hooks = @(@{ type = 'command'; command = $cmd; timeout = 10 }) }
    }

    if ($kept.Count -gt 0) { $data['hooks']['SessionStart'] = $kept }
    else { $data['hooks'].Remove('SessionStart') | Out-Null }
    if ($data['hooks'].Count -eq 0) { $data.Remove('hooks') | Out-Null }

    New-Item -ItemType Directory -Force -Path (Split-Path $settings) | Out-Null
    $data | ConvertTo-Json -Depth 20 | Set-Content $settings -Encoding UTF8
    Info "SessionStart hooks now: $($kept.Count)"
}

if ($Uninstall) {
    Info 'removing the ACC integration'
    Remove-Item (Join-Path $claudeDir 'skills\acc-hud\SKILL.md') -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $claudeDir 'skills\acc-hud') -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $claudeDir 'commands\acc.md') -ErrorAction SilentlyContinue
    Remove-Item $hookDest -ErrorAction SilentlyContinue
    if (Test-Path $settings) { Update-SessionStartHook 'uninstall' }
    Info 'removed. Restart Claude Code to pick up the change.'
    exit 0
}

Info "installing into $claudeDir"
New-Item -ItemType Directory -Force -Path (Join-Path $claudeDir 'skills\acc-hud') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $claudeDir 'commands')       | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $claudeDir 'scripts')        | Out-Null

Copy-Item (Join-Path $src 'skills\acc-hud\SKILL.md') (Join-Path $claudeDir 'skills\acc-hud\SKILL.md') -Force
Info 'skill    acc-hud'
Copy-Item (Join-Path $src 'commands\acc.md') (Join-Path $claudeDir 'commands\acc.md') -Force
Info 'command  /acc'
Copy-Item (Join-Path $src 'hooks\acc-hud-session-start.ps1') $hookDest -Force
Info 'hook     SessionStart'

Update-SessionStartHook 'install'

if (-not (Get-Command acc -ErrorAction SilentlyContinue)) {
    Warn "'acc' is not on your PATH yet. The hook stays silent until ACC is installed."
}

Write-Host @"

Installed. Restart Claude Code (hooks are read when a session starts).

Then:
  /acc              open the dashboard
  /acc status       is the daemon up?
  /acc start|stop   control it

Silence the startup offer:  New-Item `$env:USERPROFILE\.acc\no-prompt
Remove everything:          .\install-skills.ps1 -Uninstall
"@
