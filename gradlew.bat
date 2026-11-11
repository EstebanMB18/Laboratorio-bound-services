@echo off
where gradle >nul 2>nul
if errorlevel 1 (
 echo Gradle no esta en PATH. Abre el proyecto en Android Studio y configura Gradle 8.11.1.
 exit /b 1
)
gradle %*
