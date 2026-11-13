@echo off
setlocal EnableExtensions
title Music Player - create my signing key and add it to GitHub
set "REPO=mbnetworku/MusicPlayer"
set "DIR=%USERPROFILE%\MusicPlayer-signing-key"

echo.
echo  Create your own signing key and add it to GitHub
echo  ------------------------------------------------
echo  This makes a private key for your app, keeps a copy on this PC,
echo  and puts it into GitHub Actions as four secrets. Nothing is sent
echo  to anyone else. You never type a password.
echo.
echo  IMPORTANT: the first app signed with this new key cannot update over
echo  the app you have now. Uninstall the old one once, then install the new.
echo  Your favorites and settings are lost in that one reinstall.
echo.
choice /C YN /M "Continue"
if errorlevel 2 exit /b 0

rem --- tools --------------------------------------------------------------
where keytool >nul 2>&1
if errorlevel 1 (
  echo.
  echo  Java is not installed ^(it gives the "keytool" program^).
  echo  Install it from a command window with:
  echo    winget install --id EclipseAdoptium.Temurin.21.JDK -e
  echo  Then close this window and run this file again.
  pause
  exit /b 1
)
where gh >nul 2>&1
if errorlevel 1 (
  echo.
  echo  The GitHub program "gh" is missing. Run Publish-Update.bat first
  echo  ^(right-click, Run as administrator^). It installs it. Then run this again.
  pause
  exit /b 1
)
gh auth status >nul 2>&1
if errorlevel 1 (
  echo  Signing in to GitHub. Follow the steps in your browser.
  gh auth login --hostname github.com --git-protocol https --web
  if errorlevel 1 goto failed
)

rem --- the key: made once, then reused -------------------------------------
if not exist "%DIR%" mkdir "%DIR%"
if exist "%DIR%\release.jks" if exist "%DIR%\password.txt" (
  echo  A key from an earlier run was found. Using it.
  goto have_key
)
set "KSPW=Mp%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%x"
echo  Creating the key...
keytool -genkeypair -keystore "%DIR%\release.jks" -alias music-player -keyalg RSA -keysize 2048 -validity 10000 -storepass:env KSPW -keypass:env KSPW -dname "CN=Music Player, O=Music Player, C=US" >nul 2>&1
if errorlevel 1 goto failed
<nul set /p "=%KSPW%" > "%DIR%\password.txt"

:have_key
certutil -f -encode "%DIR%\release.jks" "%DIR%\key.b64" >nul 2>&1
if errorlevel 1 goto failed
findstr /v /c:"-----" "%DIR%\key.b64" > "%DIR%\key.txt"

rem --- add the four secrets to GitHub --------------------------------------
echo  Adding the secrets to GitHub...
gh secret set RELEASE_KEYSTORE_BASE64 --repo %REPO% < "%DIR%\key.txt"
if errorlevel 1 goto failed
gh secret set RELEASE_KEYSTORE_PASSWORD --repo %REPO% < "%DIR%\password.txt"
if errorlevel 1 goto failed
gh secret set RELEASE_KEY_PASSWORD --repo %REPO% < "%DIR%\password.txt"
if errorlevel 1 goto failed
gh secret set RELEASE_KEY_ALIAS --repo %REPO% --body music-player
if errorlevel 1 goto failed
del "%DIR%\key.b64" "%DIR%\key.txt" >nul 2>&1

echo.
echo  Done. The four secrets are in GitHub.
echo.
echo  KEEP THIS FOLDER SAFE, and copy it to a USB stick or cloud drive:
echo    %DIR%
echo  If you lose release.jks, the app can never be updated again.
echo  Never share that folder with anyone.
echo.
echo  Next: tell Claude "the key is added". It will make the first
echo  version signed with your key.
echo.
pause
exit /b 0

:failed
echo.
echo  Something did not work. Check the message above, then run this again.
echo  Nothing was shared outside GitHub.
pause
exit /b 1
