@echo off
setlocal EnableDelayedExpansion
title CBT Agent
cd /d "%~dp0"

rem Some host shells (including the WorkBuddy desktop app) export SERVER__PORT for
rem their own CLI. Spring relaxed-binding reads SERVER__PORT as server.port, so the
rem app silently binds a random host port instead of 8080. Drop it before we start.
set "SERVER__PORT="

rem ===========================================================================
rem  CBT Agent - one-shot launcher.
rem  Frontend and backend are ONE process: Spring Boot serves
rem    src/main/resources/static/*  ->  http://localhost:8080/
rem    POST /api/cbt/turn          ->  the only REST endpoint
rem  Flow: JDK -> LLM key -> MySQL -> schema -> run.  LLM defaults to DeepSeek.
rem
rem  Usage:  start.cmd                 full flow, opens the browser
rem          start.cmd --no-browser    full flow, no browser
rem          start.cmd --skip-db       skip schema check (database already set up)
rem ===========================================================================

set "OPEN_BROWSER=1"
set "SKIP_DB=0"
for %%A in (%*) do (
  if /i "%%A"=="--no-browser" set "OPEN_BROWSER=0"
  if /i "%%A"=="--skip-db"    set "SKIP_DB=1"
  if /i "%%A"=="--help"       goto :usage
  if /i "%%A"=="-h"           goto :usage
)

echo.
echo  ================================================================
echo    CBT Agent  ^|  10 阶段最小介入闭环  ^|  http://localhost:8080/
echo  ================================================================
echo.

rem ------------------------------------------------------- 0. local config
rem config.cmd holds your key / site / model / MySQL password. Git-ignored.
rem Optional: plain environment variables work too.
if exist "%~dp0config.cmd" (
  call "%~dp0config.cmd"
  echo  [0/5] config.cmd ....... loaded  ^(key + database taken from local file^)
) else (
  echo  [0/5] config.cmd ....... not found  ^(env vars only^)
  echo        copy config.example.cmd to config.cmd to keep settings locally
)

rem ---------------------------------------------------------------- 1. JDK 17
if "%CBT_JAVA_HOME%"=="" (
  set "JAVA_HOME=C:\Users\luoch\.jdks\corretto-17.0.20.1"
) else (
  set "JAVA_HOME=%CBT_JAVA_HOME%"
)
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo  [1/5] JDK 17 ............ FAILED
  echo.
  echo    Not found: %JAVA_HOME%\bin\java.exe
  echo    Set CBT_JAVA_HOME to a JDK 17 installation, e.g.
  echo      set CBT_JAVA_HOME=C:\path\to\jdk-17
  echo.
  goto :fail
)
echo  [1/5] JDK 17 ............ ok   %JAVA_HOME%

rem ------------------------------------------------------------- 2. LLM key
rem Three env vars, all optional. Defaults point at DeepSeek.
if "%AI_BASE_URL%"=="" set "AI_BASE_URL=https://api.deepseek.com"
if "%AI_MODEL%"==""    set "AI_MODEL=deepseek-chat"
if "%AI_API_KEY%"=="" if not "%DEEPSEEK_API_KEY%"=="" set "AI_API_KEY=%DEEPSEEK_API_KEY%"

if "%AI_API_KEY%"=="" (
  echo  [2/5] LLM key .......... MISSING  ^(warn only, continuing^)
  echo.
  echo    The page, the graph and the checkpoint tables all still work;
  echo    every question returns 401 until a key is set.
  echo.
  echo      pwsh / ps  :  $env:DEEPSEEK_API_KEY="sk-xxxx"
  echo      cmd.exe    :  set DEEPSEEK_API_KEY=sk-xxxx
  echo.
  echo    Key page: https://platform.deepseek.com/api_keys
  echo.
) else (
  echo  [2/5] LLM key .......... ok   !AI_API_KEY:~0,6!...
)
echo        base-url           %AI_BASE_URL%
echo        model              %AI_MODEL%

rem ------------------------------------------------------------- 3. MySQL CLI
set "MYSQL="
if not "%CBT_MYSQL_HOME%"=="" (
  if exist "%CBT_MYSQL_HOME%\bin\mysql.exe" set "MYSQL=%CBT_MYSQL_HOME%\bin\mysql.exe"
) else (
  for %%P in (
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
    "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
    "C:\Program Files\MySQL\MySQL Server 8.1\bin\mysql.exe"
    "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe"
  ) do (
    if not defined MYSQL if exist %%P set "MYSQL=%%~P"
  )
)
if not defined MYSQL (
  echo  [3/5] MySQL client ...... NOT FOUND
  echo.
  echo    mysql.exe was not found in the usual install paths.
  echo    Set CBT_MYSQL_HOME to your MySQL root, e.g.
  echo      set CBT_MYSQL_HOME=C:\Program Files\MySQL\MySQL Server 8.0
  echo.
  echo    Or skip this step and apply the schema by hand:
  echo      src\main\resources\db\schema.sql
  echo    then run:  start.cmd --skip-db
  echo.
  goto :fail
)
echo  [3/5] MySQL client ...... ok   %MYSQL%

if "%CBT_DB_USERNAME%"=="" set "CBT_DB_USERNAME=root"

rem ------------------------------------------------------------- 4. Schema
if "%SKIP_DB%"=="1" (
  echo  [4/5] schema ............ skipped ^(--skip-db^)
  goto :port
)

if "%CBT_DB_PASSWORD%"=="" (
  echo.
  echo    MySQL password for user '%CBT_DB_USERNAME%'
  echo    ^(press Enter for no password^)
  echo    ^(or put CBT_DB_PASSWORD in config.cmd to skip this prompt^)
  set /p "CBT_DB_PASSWORD=   password: "
  echo.
) else (
  echo        db password           from config.cmd
)

set "MYSQL_PWD=%CBT_DB_PASSWORD%"
"%MYSQL%" -u %CBT_DB_USERNAME% -e "SELECT 1" >nul 2>&1
if not "%ERRORLEVEL%"=="0" (
  echo  [4/5] schema ............ FAILED to connect
  echo.
  echo    Connection to MySQL as '%CBT_DB_USERNAME%' failed. Server says:
  echo    ------------------------------------------------------------
  "%MYSQL%" -u %CBT_DB_USERNAME% -e "SELECT 1"
  echo    ------------------------------------------------------------
  echo    Check: MySQL80 service running? password correct? port 3306 open?
  echo.
  set "MYSQL_PWD="
  goto :fail
)

if not exist "src\main\resources\db\schema.sql" (
  echo  [4/5] schema ............ FAILED
  echo    src\main\resources\db\schema.sql not found.
  set "MYSQL_PWD="
  goto :fail
)

"%MYSQL%" -u %CBT_DB_USERNAME% --default-character-set=utf8mb4 < "src\main\resources\db\schema.sql" >nul 2>&1
if not "%ERRORLEVEL%"=="0" (
  echo  [4/5] schema ............ FAILED
  echo.
  echo    Could not apply schema.sql. Server says:
  echo    ------------------------------------------------------------
  "%MYSQL%" -u %CBT_DB_USERNAME% --default-character-set=utf8mb4 < "src\main\resources\db\schema.sql"
  echo    ------------------------------------------------------------
  set "MYSQL_PWD="
  goto :fail
)
set "MYSQL_PWD="
echo  [4/5] schema ............ ok   cbt_agent as %CBT_DB_USERNAME% ^(idempotent^)

rem ------------------------------------------------------------- port check
:port
set "PORT=8080"
if not "%SERVER_PORT%"=="" set "PORT=%SERVER_PORT%"
netstat -ano | findstr /i "LISTENING" | findstr /c:":%PORT% " >nul 2>&1
if "%ERRORLEVEL%"=="0" (
  echo  [5/5] port %PORT% ........... IN USE
  echo.
  echo    Something is already listening on %PORT%. Either stop it, or use:
  echo      set SERVER_PORT=8081  ^&^&  start.cmd
  echo.
  goto :fail
)

rem ------------------------------------------------------------- 5. Run
echo  [5/5] starting ......... http://localhost:%PORT%/
echo.
echo  -----------------------------------------------------------------
echo   first boot compiles and starts Tomcat: ~20-40s
echo   Ctrl+C stops it.  Logs below.
echo  -----------------------------------------------------------------
echo.

if "%OPEN_BROWSER%"=="1" (
  start "cbt-open-browser" /min powershell -NoProfile -WindowStyle Hidden -Command "Start-Sleep -Seconds 35; Start-Process 'http://localhost:%PORT%/'" >nul 2>&1
  echo  browser will open automatically in ~35s ^(--no-browser disables this^)
  echo.
)

call "%~dp0mvnw.cmd" -B spring-boot:run
set "CODE=%ERRORLEVEL%"

if not "%CODE%"=="0" (
  echo.
  echo  =================================================================
  echo   exited with code %CODE%. Match the first stack-trace line above:
  echo.
  echo    "401 Unauthorized" / "Authentication Fails"
  echo        -^> AI_API_KEY is wrong for %AI_BASE_URL%
  echo    "Access denied for user 'root'@'localhost'"
  echo        -^> wrong DB password; set CBT_DB_PASSWORD and retry
  echo    "Unknown database 'cbt_agent'" / "Unable to create tables"
  echo        -^> run: start.cmd            ^(without --skip-db^)
  echo    "Port %PORT% was already in use"
  echo        -^> set SERVER_PORT=8081 and retry
  echo    "Table 'cbt_agent.session_record' doesn't exist"
  echo        -^> schema was skipped; run without --skip-db
  echo  =================================================================
)
exit /b %CODE%

:fail
echo  aborted.
exit /b 1

:usage
echo.
echo  CBT Agent launcher
echo.
echo    start.cmd                 full flow: JDK, key, MySQL, schema, run, open browser
echo    start.cmd --no-browser    same, without opening the browser
echo    start.cmd --skip-db       skip the schema step ^(database already set up^)
echo.
echo  Settings live in config.cmd, which is git-ignored.
echo  Template for a fresh clone: config.example.cmd
echo.
echo  Environment:
echo    DEEPSEEK_API_KEY       API key; also accepted as AI_API_KEY
echo                           or set it once in config.cmd
echo    AI_BASE_URL            default https://api.deepseek.com
echo    AI_MODEL               default deepseek-chat
echo    CBT_DB_USERNAME        default root
echo    CBT_DB_PASSWORD        prompted for if unset
echo    CBT_DB_URL             default jdbc:mysql://127.0.0.1:3306/cbt_agent
echo    SERVER_PORT            default 8080
echo    CBT_JAVA_HOME          default C:\Users\luoch\.jdks\corretto-17.0.20.1
echo    CBT_MYSQL_HOME         default Program Files\MySQL\MySQL Server 8.0
echo.
exit /b 0
