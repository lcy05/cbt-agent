@echo off
rem ===========================================================================
rem  CBT Agent - settings TEMPLATE (safe to commit).
rem
rem  Copy this file to config.cmd, fill in your own key and password, and run
rem  start.cmd. config.cmd is git-ignored; this template is the version that
rem  goes into the repository.
rem
rem      copy config.example.cmd config.cmd
rem
rem  start.cmd loads config.cmd automatically if it exists and skips it if not.
rem ===========================================================================

rem ---- 1. model provider ---------------------------------------------------
rem Default is DeepSeek. Any OpenAI-compatible endpoint works:
rem   DeepSeek   https://api.deepseek.com                 deepseek-chat
rem   Kimi       https://api.moonshot.cn                  moonshot-v1-8k
rem   GLM        https://open.bigmodel.cn/api/paas/v4     glm-4-plus
rem   Ollama     http://127.0.0.1:11434/v1                qwen2.5:14b
rem Base URL is the bare host: the client appends /v1/chat/completions.
set "AI_BASE_URL=https://api.deepseek.com"
set "AI_MODEL=deepseek-chat"

rem ---- 2. API key ----------------------------------------------------------
rem Paste your key between the quotes. Get one at:
rem   https://platform.deepseek.com/api_keys
rem Leave it empty to boot without a key: the page still opens, but every
rem question returns 401.
set "DEEPSEEK_API_KEY="

rem ---- 3. MySQL ------------------------------------------------------------
rem These become environment variables that application.yml reads:
rem   spring.datasource.url        = CBT_DB_URL
rem   spring.datasource.username   = CBT_DB_USERNAME
rem   spring.datasource.password   = CBT_DB_PASSWORD
rem
rem Set the password once here: start.cmd uses it for BOTH the `mysql.exe`
rem schema import and the Spring Boot datasource, so it is never asked twice.
rem
rem Leave it empty and start.cmd prompts for it at run time. Do that instead
rem if the password contains percent or exclamation signs, because cmd.exe
rem mangles those inside a `set` statement.
set "CBT_DB_USERNAME=root"
set "CBT_DB_PASSWORD="

rem Only needed when MySQL is not on 127.0.0.1:3306, or the schema uses a
rem different database name. If you rename the database here you must rename
rem it in src/main/resources/db/schema.sql too.
rem set "CBT_DB_URL=jdbc:mysql://127.0.0.1:3306/cbt_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"

rem ---- 4. tool paths -------------------------------------------------------
rem Auto-detected. Uncomment only when detection picks the wrong one.
rem set "CBT_MYSQL_HOME=C:\Program Files\MySQL\MySQL Server 8.0"
rem set "CBT_JAVA_HOME=C:\path\to\jdk-17"

rem ---- 5. server -----------------------------------------------------------
rem Default 8080. Change if that port is already taken.
rem set "SERVER_PORT=8081"
