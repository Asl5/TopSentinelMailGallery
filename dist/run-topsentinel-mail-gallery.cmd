@echo off
setlocal
cd /d "%~dp0"
set "JAVA_EXE=java"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not exist "%~dp0logs" mkdir "%~dp0logs"
"%JAVA_EXE%" -jar "%~dp0TopSentinelMailGallery.jar" %* >> "%~dp0logs\TopSentinelMailGallery.log" 2>&1
exit /b %ERRORLEVEL%
