@echo off
setlocal EnableExtensions
title Music Player - build, sign and publish an update on GitHub
set "REPO=mbnetworku/MusicPlayer"

echo.
echo  Music Player: build and sign an update on GitHub with your key
echo  -------------------------------------------------------
echo  You never type your GitHub password here. GitHub opens in your
echo  browser once, you sign in there, and Windows remembers it.
echo.

rem --- 1. GitHub CLI (the "gh" program) ---------------------------------
where gh >nul 2>&1
if not errorlevel 1 goto have_gh
echo  The GitHub program "gh" is not installed yet.
net session >nul 2>&1
if errorlevel 1 (
  echo  Installing it needs administrator rights.
  echo  Close this window, right-click this file and choose "Run as administrator".
  pause
  exit /b 1
)
winget install --id GitHub.cli -e --accept-source-agreements --accept-package-agreements
echo.
echo  Installed. Close this window and run this file again.
pause
exit /b 0

:have_gh
rem --- 2. Sign in once (browser, no password typed here) -----------------
gh auth status >nul 2>&1
if errorlevel 1 (
  echo  Signing in to GitHub. Follow the steps in your browser.
  gh auth login --hostname github.com --git-protocol https --web
  if errorlevel 1 goto failed
)

rem --- 3. The one-time setting: let the release workflow write -----------
echo  Setting "Read and write permissions" for GitHub Actions...
gh api -X PUT "repos/%REPO%/actions/permissions/workflow" -f default_workflow_permissions=write -F can_approve_pull_request_reviews=false >nul 2>&1
if errorlevel 1 (
  echo  Could not change that setting. Please do it by hand:
  echo  GitHub - Settings - Actions - General - Workflow permissions - Read and write.
) else (
  echo  Done.
)

rem --- 4. Publish the version --------------------------------------------
echo.
:ask
set "VERSION="
echo  Type the version NUMBER, for example 1.7.1 (not the word yes).
set /p "VERSION=Version to publish: "
echo %VERSION%| findstr /R /X "[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*" >nul
if errorlevel 1 (
  echo  "%VERSION%" is not a version number. Use three numbers like 1.7.0.
  echo.
  goto ask
)
echo  Publishing %VERSION% ...
gh workflow run release.yml --repo %REPO% -f version=%VERSION%
if errorlevel 1 goto failed

echo.
echo  Started. Waiting a few seconds, then showing the result.
timeout /t 20 /nobreak >nul
gh run list --repo %REPO% --workflow release.yml --limit 1
echo.
echo  GitHub now builds and signs it. This takes about 5 minutes.
echo  Look at https://github.com/%REPO%/actions
echo  When the run says "completed success", the update is ready:
echo  https://github.com/%REPO%/releases/latest
echo  Open Music Player - Settings - Updates to install it.
echo.
pause
exit /b 0

:failed
echo.
echo  Something did not work. Nothing was changed on GitHub.
echo  Check the message above, then run this file again.
pause
exit /b 1
