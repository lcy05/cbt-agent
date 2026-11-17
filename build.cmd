@echo off
rem One-shot build: clean compile + 21 tests (V1-V12 + A1-A4 + 5 prompt assets), JDK 17.
call "%~dp0mvnw.cmd" clean verify
exit /b %ERRORLEVEL%
