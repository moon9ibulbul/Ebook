@ECHO OFF
SET DIR=%~dp0
SET APP_BASE_NAME=%~n0
SET APP_HOME=%DIR%
SET DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
IF NOT "%JAVA_HOME%"=="" GOTO findJavaFromJavaHome
SET JAVA_EXE=java.exe
GOTO run
:findJavaFromJavaHome
SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
:run
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -Dorg.gradle.appname=%APP_BASE_NAME% -classpath %CLASSPATH% org.gradle.wrapper.GradleWrapperMain %*
