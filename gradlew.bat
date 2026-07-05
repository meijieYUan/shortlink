@echo off
setlocal
set DIRNAME=%~dp0
set GRADLE_HOME=%DIRNAME%gradle-dist
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
    echo ERROR: Gradle distribution not found at %GRADLE_HOME%
    exit /b 1
)
set JAVA_HOME=%DIRNAME%jdk-21\jdk-21.0.2
call "%GRADLE_HOME%\bin\gradle.bat" %*
endlocal
exit /b %ERRORLEVEL%
