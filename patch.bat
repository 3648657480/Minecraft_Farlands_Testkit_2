@echo off
rem FarLands G2 patcher helper (Windows). Drag a Minecraft 26.2 client jar onto this file.
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0patch.ps1" %*
echo.
pause
