@echo off
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel% equ 0 (
    py -3 scripts\bootstrap_gradle.py %*
) else (
    python scripts\bootstrap_gradle.py %*
)
