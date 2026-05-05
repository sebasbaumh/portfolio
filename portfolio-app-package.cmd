@echo off

REM force build with Java 21
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"

"%JAVA_HOME%\bin\java.exe" -version

REM clean up old builds
for /d /r %%d in (target) do @if exist "%%d" rd /s /q "%%d"

REM build
mvn -f portfolio-app\pom.xml -Denforcer.skip=true clean verify

REM skip tests (optional)
REM mvn -f portfolio-app\pom.xml -Denforcer.skip=true -DskipTests clean verify

pause
