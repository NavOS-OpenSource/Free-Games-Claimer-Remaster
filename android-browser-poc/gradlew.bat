@rem Gradle wrapper script for Windows
@if "%DEBUG%"=="" @echo off

set DIRNAME=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS="-Xmx2048m" "-Dfile.encoding=UTF-8"

set JAVA_HOME=%PROGRAMFILES%\Android\Android Studio\jbr

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Error: JAVA_HOME is not set or java.exe not found
    exit /b 1
)

set JAVA_EXE=%JAVA_HOME%\bin\java.exe

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
