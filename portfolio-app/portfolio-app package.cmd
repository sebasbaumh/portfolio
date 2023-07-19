@echo off
cd /d "%~dp0"
mvn -U -T 8 -D tycho.disableP2Mirrors=true clean package
pause
