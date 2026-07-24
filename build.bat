@echo off
title FileScanner - Build
echo.
echo  ================================
echo   FileScanner - Building...
echo  ================================
echo.

mvn clean package -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo.
    echo  Build successful! JAR is at: target\file-scanner-1.0.0.jar
    echo  Run start.bat to launch the application.
) else (
    echo.
    echo  Build FAILED. Check the output above for errors.
)
echo.
pause
