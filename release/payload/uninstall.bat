@echo off
setlocal EnableDelayedExpansion
rem Removes Agent Control Center.
rem Session history in %USERPROFILE%\.acc is kept unless --purge is passed,
rem because it is the only record of what your agents did.

set "PREFIX=%LOCALAPPDATA%\ACC"
set "PURGE=0"
for %%a in (%*) do (
  if /i "%%a"=="--purge" set "PURGE=1"
)

if exist "%PREFIX%\bin\acc.bat" (
  echo ==^> stopping daemon
  call "%PREFIX%\bin\acc.bat" stop >nul 2>&1
  echo ==^> removing ACC's hooks from Claude Code
  curl -s --max-time 3 -X POST "http://127.0.0.1:4000/api/hooks/uninstall" ^
    -H "Content-Type: application/json" -d "{\"projectScope\":false}" >nul 2>&1
)

if exist "%PREFIX%" (
  rmdir /s /q "%PREFIX%"
  echo ==^> removed %PREFIX%
)

rem Strip our entry from the user PATH, leaving everything else intact.
for /f "skip=2 tokens=2,*" %%a in ('reg query HKCU\Environment /v PATH 2^>nul') do set "USERPATH=%%b"
if defined USERPATH (
  set "NEWPATH=!USERPATH:%PREFIX%\bin;=!"
  set "NEWPATH=!NEWPATH:;%PREFIX%\bin=!"
  set "NEWPATH=!NEWPATH:%PREFIX%\bin=!"
  if not "!NEWPATH!"=="!USERPATH!" (
    setx PATH "!NEWPATH!" >nul
    echo ==^> removed %PREFIX%\bin from PATH
  )
)

if "%PURGE%"=="1" (
  if exist "%USERPROFILE%\.acc" rmdir /s /q "%USERPROFILE%\.acc"
  echo ==^> purged %USERPROFILE%\.acc ^(history, logs and the hook bridge^)
) else (
  echo.
  echo Kept %USERPROFILE%\.acc ^(session history and logs^). Remove with --purge.
)
exit /b 0
