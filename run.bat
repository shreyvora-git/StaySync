
@echo off
setlocal EnableExtensions
cd /d "%~dp0"

title StaySync - Portable Desktop Launcher

echo ==============================================================================
echo                      StaySync - College Microproject
echo                Hostel Mess Complaint Tracker (Java 21 + MySQL)
echo ==============================================================================
echo.

echo [1/3] Checking Java environment...
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java was not found or could not run.
    echo Install Java 21 or higher from:
    echo https://adoptium.net/temurin/releases/?version=21
    echo.
    pause
    exit /b 1
)

echo       Java detected:
java -version 2>&1
echo.

echo [2/3] Checking application configuration...
if not exist "config\app.properties" (
    if exist "config\app.properties.example" (
        if not exist "config" mkdir config
        copy /Y "config\app.properties.example" "config\app.properties" >nul
        echo.
        echo [SETUP] Created config\app.properties.
        echo Edit this file with your local MySQL credentials.
        notepad "config\app.properties"
        echo.
        echo Save the configuration, then run run.bat again.
        pause
        exit /b 0
    ) else (
        echo [ERROR] Configuration template not found.
        pause
        exit /b 1
    )
)

echo       Configuration file found.

if not exist "uploads" mkdir uploads

echo.
echo [3/3] Launching StaySync...
echo.

if exist "target\staysync.jar" (
    java -jar "target\staysync.jar"
) else (
    echo [ERROR] target\staysync.jar was not found.
    echo Please download the complete project or rebuild the JAR.
    pause
    exit /b 1
)

if errorlevel 1 (
    echo.
    echo [ERROR] StaySync exited with an error.
    pause
)

endlocal
