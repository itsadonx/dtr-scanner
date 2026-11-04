@echo off
cd /d "%~dp0"
title SecureAttend Companion

where node >nul 2>&1
if errorlevel 1 (
  echo Node.js was not found.
  echo Install Node.js 18 or newer from https://nodejs.org
  echo then double-click this file again.
  echo.
  pause
  exit /b 1
)

echo Starting SecureAttend companion...
echo Leave this window open while you use the website.
echo.
echo Website:    http://localhost:8787
echo Admin:      admin / admin123
echo Device key: dtr-sync-key
echo.

start "" cmd /c "timeout /t 2 /nobreak >nul & start http://localhost:8787"
node server.js

echo.
echo The server stopped.
pause
