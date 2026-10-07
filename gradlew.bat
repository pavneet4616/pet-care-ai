@ECHO OFF
SETLOCAL

SET DIRNAME=%~dp0
IF "%DIRNAME%"=="" SET DIRNAME=.
SET APP_HOME=%DIRNAME%
SET WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Missing Gradle wrapper jar at "%WRAPPER_JAR%".
  ECHO Download the wrapper jar or run a Gradle installation to regenerate it.
  EXIT /B 1
)

SET JAVA_EXE=java.exe
%JAVA_EXE% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*

