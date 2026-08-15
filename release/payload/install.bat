@echo off
setlocal EnableDelayedExpansion
rem Installs Agent Control Center for the current user.
rem Installs to %LOCALAPPDATA%\ACC and adds it to the user PATH.
rem No admin rights required; nothing is written outside the user profile.

set "SRC=%~dp0"
if "%SRC:~-1%"=="\" set "SRC=%SRC:~0,-1%"
set "PREFIX=%LOCALAPPDATA%\ACC"
if not "%~1"=="" set "PREFIX=%~1"

if not exist "%SRC%\lib\acc-daemon.jar" (
  echo ERROR: run this from inside the unpacked ACC directory.
  exit /b 1
)

rem Refuse to overwrite a directory that is not a previous ACC install.
if exist "%PREFIX%" (
  if not exist "%PREFIX%\lib\acc-daemon.jar" (
    echo ERROR: %PREFIX% exists and does not look like an ACC install.
    echo Remove it, or pass a different directory as the first argument.
    exit /b 1
  )
  echo ==^> upgrading existing install at %PREFIX%
  if exist "%PREFIX%\bin\acc.bat" call "%PREFIX%\bin\acc.bat" stop >nul 2>&1
)

echo ==^> installing to %PREFIX%
if exist "%PREFIX%\lib" rmdir /s /q "%PREFIX%\lib"
if exist "%PREFIX%\bin" rmdir /s /q "%PREFIX%\bin"
if exist "%PREFIX%\runtime" rmdir /s /q "%PREFIX%\runtime"
mkdir "%PREFIX%\lib" 2>nul
mkdir "%PREFIX%\bin" 2>nul

copy /y "%SRC%\lib\acc-daemon.jar" "%PREFIX%\lib\" >nul
copy /y "%SRC%\bin\acc.bat" "%PREFIX%\bin\" >nul
if exist "%SRC%\README.md" copy /y "%SRC%\README.md" "%PREFIX%\" >nul
rem Ship the uninstaller with the install so it survives deleting the download.
copy /y "%SRC%\uninstall.bat" "%PREFIX%\" >nul

if exist "%SRC%\runtime" (
  echo ==^> installing bundled Java runtime
  xcopy /e /i /q /y "%SRC%\runtime" "%PREFIX%\runtime" >nul
) else (
  echo ==^> no bundled runtime in this package - ACC will use your system Java 17+
)

rem Append to the *user* PATH, and only if it is not already there.
for /f "skip=2 tokens=2,*" %%a in ('reg query HKCU\Environment /v PATH 2^>nul') do set "USERPATH=%%b"
if not defined USERPATH set "USERPATH="
echo !USERPATH! | find /i "%PREFIX%\bin" >nul
if errorlevel 1 (
  if defined USERPATH (
    setx PATH "!USERPATH!;%PREFIX%\bin" >nul
  ) else (
    setx PATH "%PREFIX%\bin" >nul
  )
  echo ==^> added %PREFIX%\bin to your PATH ^(open a new terminal to pick it up^)
) else (
  echo ==^> %PREFIX%\bin already on PATH
)

where claude >nul 2>&1
if errorlevel 1 echo warning: Claude Code was not found on PATH. ACC needs it to launch agents.

echo.
echo Installed. Open a NEW terminal, then:
echo.
echo     acc start          # daemon on http://127.0.0.1:4000
echo     acc attach         # register ACC's hooks in Claude Code
echo     acc open           # open the dashboard
echo.
echo Uninstall with: %PREFIX%\uninstall.bat
exit /b 0
