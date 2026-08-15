@echo off
setlocal EnableDelayedExpansion
rem Agent Control Center launcher (Windows).
rem Prefers the JRE bundled next to this script; falls back to JAVA_HOME or PATH.

set "APP=%~dp0.."
for %%I in ("%APP%") do set "APP=%%~fI"

set "JAR=%APP%\lib\acc-daemon.jar"
if "%ACC_PORT%"=="" set "ACC_PORT=4000"
set "BASE=http://127.0.0.1:%ACC_PORT%"
if "%ACC_HOME%"=="" set "ACC_HOME=%USERPROFILE%\.acc"
set "LOG=%ACC_HOME%\daemon.log"

if exist "%APP%\runtime\bin\java.exe" (
  set "JAVA=%APP%\runtime\bin\java.exe"
) else if exist "%JAVA_HOME%\bin\java.exe" (
  set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA=java"
)

if not exist "%JAR%" (
  echo ERROR: missing %JAR%
  exit /b 1
)

set "CMD=%~1"
if "%CMD%"=="" set "CMD=status"

if /i "%CMD%"=="start" goto :start
if /i "%CMD%"=="stop" goto :stop
if /i "%CMD%"=="restart" goto :restart
if /i "%CMD%"=="status" goto :status
if /i "%CMD%"=="open" goto :open
if /i "%CMD%"=="attach" goto :attach
if /i "%CMD%"=="detach" goto :detach
if /i "%CMD%"=="run" goto :run
if /i "%CMD%"=="logs" goto :logs
if /i "%CMD%"=="version" goto :version
goto :usage

:start
  call :isup && (echo ACC already running on :%ACC_PORT% & exit /b 0)
  if not exist "%ACC_HOME%" mkdir "%ACC_HOME%"
  rem Detach so the console returns immediately.
  start "ACC daemon" /b "" "%JAVA%" -jar "%JAR%" > "%LOG%" 2>&1
  for /l %%i in (1,1,45) do (
    timeout /t 1 /nobreak >nul
    call :isup && (echo ACC up on %BASE% & exit /b 0)
  )
  echo ERROR: daemon did not start; see %LOG%
  exit /b 1

:stop
  for /f "tokens=2 delims=," %%p in ('tasklist /fi "imagename eq java.exe" /fo csv /nh 2^>nul') do (
    wmic process where "ProcessId=%%~p" get CommandLine 2>nul | find "acc-daemon.jar" >nul && taskkill /PID %%~p /F >nul 2>&1
  )
  echo stopped
  exit /b 0

:restart
  call "%~f0" stop
  timeout /t 1 /nobreak >nul
  call "%~f0" start
  exit /b %errorlevel%

:status
  call :isup || (echo daemon: down & exit /b 1)
  curl -s "%BASE%/api/system/status"
  echo.
  exit /b 0

:open
  call :isup || (echo daemon is not running - try: acc start & exit /b 1)
  start "" "%BASE%"
  exit /b 0

:attach
  if "%~2"=="" (
    curl -s -X POST "%BASE%/api/hooks/install" -H "Content-Type: application/json" -d "{\"projectScope\":false}"
  ) else (
    curl -s -X POST "%BASE%/api/hooks/install" -H "Content-Type: application/json" -d "{\"projectScope\":true,\"projectDir\":\"%~2\"}"
  )
  echo.
  exit /b 0

:detach
  if "%~2"=="" (
    curl -s -X POST "%BASE%/api/hooks/uninstall" -H "Content-Type: application/json" -d "{\"projectScope\":false}"
  ) else (
    curl -s -X POST "%BASE%/api/hooks/uninstall" -H "Content-Type: application/json" -d "{\"projectScope\":true,\"projectDir\":\"%~2\"}"
  )
  echo.
  exit /b 0

:run
  if "%~2"=="" (
    echo usage: acc run "^<task^>" [dir] [default^|acceptEdits^|plan^|bypassPermissions]
    exit /b 2
  )
  set "TASKDIR=%~3"
  if "%TASKDIR%"=="" set "TASKDIR=%CD%"
  set "MODE=%~4"
  if "%MODE%"=="" set "MODE=default"
  powershell -NoProfile -Command ^
    "$b=@{prompt='%~2';cwd='%TASKDIR:\=\\%';permissionMode='%MODE%'}|ConvertTo-Json;" ^
    "$r=Invoke-RestMethod -Uri '%BASE%/api/sessions' -Method Post -ContentType 'application/json' -Body $b;" ^
    "Write-Host ('session ' + $r.id + '  mode=' + $r.permissionMode + '  auto-approve=' + $r.autoApprove);" ^
    "Write-Host 'watch it at %BASE%'"
  exit /b 0

:logs
  powershell -NoProfile -Command "Get-Content -Path '%LOG%' -Wait -Tail 50"
  exit /b 0

:version
  echo Agent Control Center 0.2.0
  "%JAVA%" -version
  exit /b 0

:isup
  curl -s --max-time 2 "%BASE%/api/system/status" >nul 2>&1
  exit /b %errorlevel%

:usage
  echo Agent Control Center
  echo.
  echo   acc start ^| stop ^| restart ^| status ^| open
  echo   acc attach [dir]     register ACC's hooks in Claude Code
  echo   acc detach [dir]     remove them
  echo   acc run "^<task^>" [dir] [mode]
  echo   acc logs ^| version
  echo.
  echo Dashboard: %BASE%
  exit /b 0
