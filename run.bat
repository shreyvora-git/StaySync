@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

title StaySync - Portable Desktop Launcher

echo ==============================================================================
echo                      StaySync - College Microproject
echo                Hostel Mess Complaint Tracker (Java 21 + MySQL)
echo ==============================================================================
echo.

:: 1. Verify Java 21+ is available
echo [1/3] Checking Java environment...
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java runtime was not found on your PATH!
    echo.
    echo StaySync requires Java 21 (JDK 21) or higher.
    echo Please download and install Eclipse Temurin JDK 21 from:
    echo   https://adoptium.net/temurin/releases/?version=21
    echo.
    echo Ensure "Add to PATH" is checked during installation.
    echo.
    pause
    exit /b 1
)

:: Extract and verify major version
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JVER=%%~g
)
echo       Detected Java version: !JVER!

:: 2. Verify / Prepare configuration file
echo [2/3] Checking application configuration...
if not exist "config\app.properties" (
    if exist "config\app.properties.example" (
        echo [SETUP] config\app.properties not found. Creating from template...
        copy "config\app.properties.example" "config\app.properties" >nul
        echo.
        echo [ACTION REQUIRED] A new 'config\app.properties' file was created!
        echo Please edit 'config\app.properties' with Notepad to configure your
        echo local MySQL password:
        echo.
        echo       db.user=root
        echo       db.password=YOUR_MYSQL_PASSWORD
        echo.
        echo After setting your password, run this script again.
        echo.
        notepad config\app.properties
        pause
        exit /b 0
    ) else (
        echo [ERROR] Neither config\app.properties nor config\app.properties.example exists!
        pause
        exit /b 1
    )
) else (
    echo       Configuration file verified: config\app.properties
)

:: Ensure uploads directory exists
if not exist "uploads" (
    mkdir uploads >nul 2>&1
)

:: 3. Launch Application
echo [3/3] Launching StaySync application...
echo.

if exist "target\staysync.jar" (
    echo Starting packaged executable JAR (target\staysync.jar)...
    echo.
    java -jar target\staysync.jar
) else (
    :: Check if Maven is installed
    mvn -v >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        echo Target JAR not found. Running via Maven exec plugin...
        echo.
        call mvn compile exec:java
    ) else (
        echo [ERROR] Neither 'target\staysync.jar' nor Apache Maven was found!
        echo.
        echo To run StaySync, you can either:
        echo   1. Install Apache Maven (https://maven.apache.org/) and run:
        echo        mvn clean package -DskipTests
        echo      This will generate 'target\staysync.jar'.
        echo   2. Copy the pre-built 'target\staysync.jar' from the build machine.
        echo.
        pause
        exit /b 1
    )
)

if %ERRORLEVEL% neq 0 (
    echo.
    echo [EXIT] StaySync closed with return code %ERRORLEVEL%.
    pause
)

endlocal
