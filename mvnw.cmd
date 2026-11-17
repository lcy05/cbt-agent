@echo off
setlocal EnableDelayedExpansion
rem Portable Maven launcher.
rem   CI (GitHub Actions): trust JAVA_HOME from setup-java and mvn on PATH.
rem   Local: prefer CBT_JAVA_HOME, else a locally installed JDK 17, else existing JAVA_HOME.
rem Maven resolution order: CBT_MAVEN_HOME > mvn on PATH > local cached 3.9.14 > IDEA bundled.

rem --- JDK selection -------------------------------------------------------
if defined GITHUB_ACTIONS goto :jdk_done
if defined CI goto :jdk_done
if defined CBT_JAVA_HOME (
  set "JAVA_HOME=%CBT_JAVA_HOME%"
  goto :jdk_done
)
rem Auto-detect a common local JDK 17; keep existing JAVA_HOME if none found.
if exist "%USERPROFILE%\.jdks\corretto-17.0.20.1\bin\javac.exe" (
  set "JAVA_HOME=%USERPROFILE%\.jdks\corretto-17.0.20.1"
)
:jdk_done

rem --- Maven selection -----------------------------------------------------
set "MVN_CMD="
if defined CBT_MAVEN_HOME (
  if exist "%CBT_MAVEN_HOME%\bin\mvn.cmd" set "MVN_CMD=%CBT_MAVEN_HOME%\bin\mvn.cmd"
)
if not defined MVN_CMD (
  for %%M in (mvn.cmd) do set "MVN_CMD=%%~$PATH:M"
)
if not defined MVN_CMD (
  for /d %%D in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.14-bin\*") do (
    if exist "%%D\apache-maven-3.9.14\bin\mvn.cmd" set "MVN_CMD=%%D\apache-maven-3.9.14\bin\mvn.cmd"
  )
)
if not defined MVN_CMD (
  if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.1\plugins\maven\lib\maven3\bin\mvn.cmd" (
    set "MVN_CMD=C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.1\plugins\maven\lib\maven3\bin\mvn.cmd"
  )
)
if not defined MVN_CMD (
  echo [cbt-agent] Maven not found. Set CBT_MAVEN_HOME or put mvn on PATH. 1>&2
  exit /b 1
)
call "%MVN_CMD%" %*
exit /b %ERRORLEVEL%
