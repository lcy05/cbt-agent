@echo off
setlocal EnableDelayedExpansion
rem Local Maven launcher: reuses the already cached Maven 3.9.14 distribution, no download.
rem Project requires JDK 17 (the machine default JDK is 24, so we override it here).
if "%CBT_JAVA_HOME%"=="" (
  set "JAVA_HOME=C:\Users\luoch\.jdks\corretto-17.0.20.1"
) else (
  set "JAVA_HOME=%CBT_JAVA_HOME%"
)
set "MVN_CMD="
for /d %%D in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.14-bin\*") do (
  if exist "%%D\apache-maven-3.9.14\bin\mvn.cmd" set "MVN_CMD=%%D\apache-maven-3.9.14\bin\mvn.cmd"
)
if not defined MVN_CMD (
  if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.1\plugins\maven\lib\maven3\bin\mvn.cmd" set "MVN_CMD=C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.1\plugins\maven\lib\maven3\bin\mvn.cmd"
)
if not defined MVN_CMD (
  echo [cbt-agent] Maven 3.9.14 not found. Set CBT_MAVEN_HOME or run Maven from IDEA. 1>&2
  exit /b 1
)
call "%MVN_CMD%" %*
exit /b %ERRORLEVEL%
