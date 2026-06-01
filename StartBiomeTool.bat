@echo off
setlocal enabledelayedexpansion
title BiomeTool Launcher
mode con: cols=80 lines=30

set "SCRIPT_DIR=%~dp0"
set "JAR_PATTERN=BiomeToolEnhanced-*-all.jar"
set "MIN_JAVA_VERSION=25"
set "JAVA_EXE="

:: ── 1. Find the JAR ──────────────────────────────────────────────────────────
set "JAR_FILE="
set "JAR_DIR="
for %%F in ("%SCRIPT_DIR%%JAR_PATTERN%") do (
    set "JAR_FILE=%%F"
    set "JAR_DIR=%%~dpF"
)
if not defined JAR_FILE (
    for %%F in ("%SCRIPT_DIR%build\libs\%JAR_PATTERN%") do (
        set "JAR_FILE=%%F"
        set "JAR_DIR=%%~dpF"
    )
)

if not defined JAR_FILE (
    cls
    echo.
    echo  ERROR: No BiomeTool JAR found.
    echo.
    echo  Looked in:
    echo    %SCRIPT_DIR%
    echo    %SCRIPT_DIR%build\libs\
    echo.
    echo  Build the project first:  gradlew.bat build
    echo.
    pause
    exit /b 1
)

:: ── 2. Locate Java ───────────────────────────────────────────────────────────
call :find_java

if not defined JAVA_EXE (
    cls
    echo.
    echo  ERROR: Java %MIN_JAVA_VERSION%+ could not be found on this system.
    echo.
    echo  Please install a Java %MIN_JAVA_VERSION%+ JDK from one of:
    echo    https://adoptium.net               ^(Eclipse Temurin - recommended^)
    echo    https://www.microsoft.com/openjdk
    echo    https://aws.amazon.com/corretto/
    echo    https://azul.com/downloads/
    echo.
    pause
    exit /b 1
)

:: ── 3. Validate Java version ─────────────────────────────────────────────────
call :check_version

if defined VERSION_ERROR (
    cls
    echo.
    echo  ERROR: Java %MIN_JAVA_VERSION%+ is required, but Java !MAJOR! was found at:
    echo    !JAVA_EXE!
    echo.
    echo  Please install Java %MIN_JAVA_VERSION%+ from https://adoptium.net
    echo.
    pause
    exit /b 1
)

echo  Using Java !MAJOR! from: !JAVA_EXE!

:: ── 4. Copy local DendryTerra build if available ─────────────────────────────
if exist "%SCRIPT_DIR%..\DendryTerra\build\libs\DendryTerra-1.0.0-BETA-*.jar" (
    if not exist "%JAR_DIR%addons\" mkdir "%JAR_DIR%addons" 2>nul
    del /Q "%JAR_DIR%addons\DendryTerra*.jar" 2>nul
    copy /Y "%SCRIPT_DIR%..\DendryTerra\build\libs\DendryTerra-1.0.0-BETA-*.jar" "%JAR_DIR%addons\" >nul
    echo  Loaded local DendryTerra build.
)

:: ── 5. Launch ────────────────────────────────────────────────────────────────
echo  Launching: %JAR_FILE%
echo.
cd /d "%JAR_DIR%"
"%JAVA_EXE%" --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED ^
    --add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED ^
    -jar "%JAR_FILE%" %*

if %errorlevel% neq 0 (
    echo.
    echo  BiomeTool exited with error code %errorlevel%.
    pause
)
endlocal
exit /b 0


:: ═══════════════════════════════════════════════════════════════════════════════
:: Subroutines
:: ═══════════════════════════════════════════════════════════════════════════════

:find_java
:: Priority 1: JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
        exit /b 0
    )
)

:: Priority 2: PATH
where java >nul 2>&1
if %errorlevel% == 0 (
    for /f "delims=" %%J in ('where java') do (
        if not defined JAVA_EXE set "JAVA_EXE=%%J"
    )
    exit /b 0
)

:: Priority 3: Scan common install roots (each as a separate call to avoid
::             nested for-loop / for-f backtick expansion issues in CMD)
call :scan_root "C:\Program Files\Java"
call :scan_root "C:\Program Files\Eclipse Adoptium"
call :scan_root "C:\Program Files\Microsoft"
call :scan_root "C:\Program Files\Amazon Corretto"
call :scan_root "C:\Program Files\BellSoft"
call :scan_root "C:\Program Files\Azul Systems\Zulu"
call :scan_root "C:\JAVA"
exit /b 0

:: Scan one root directory for jdk*/jre* subdirs that contain bin\java.exe
:scan_root
if not exist "%~1\" exit /b 0
for /f "delims=" %%D in ('dir /b /ad "%~1\jdk*" 2^>nul') do (
    if not defined JAVA_EXE (
        if exist "%~1\%%D\bin\java.exe" (
            set "JAVA_EXE=%~1\%%D\bin\java.exe"
        )
    )
)
for /f "delims=" %%D in ('dir /b /ad "%~1\jre*" 2^>nul') do (
    if not defined JAVA_EXE (
        if exist "%~1\%%D\bin\java.exe" (
            set "JAVA_EXE=%~1\%%D\bin\java.exe"
        )
    )
)
exit /b 0

:check_version
set "MAJOR="
set "VERSION_ERROR="
set "RAW_VERSION="
for /f "tokens=3" %%V in ('""%JAVA_EXE%" -version 2>&1 | findstr /i "version""') do (
    if not defined RAW_VERSION set "RAW_VERSION=%%~V"
)
for /f "tokens=1 delims=." %%M in ("!RAW_VERSION!") do set "MAJOR=%%M"
if "!MAJOR!" == "1" (
    for /f "tokens=2 delims=." %%M in ("!RAW_VERSION!") do set "MAJOR=%%M"
)
if not defined MAJOR (
    echo  WARNING: Could not read Java version, attempting launch anyway...
    exit /b 0
)
if !MAJOR! LSS %MIN_JAVA_VERSION% set "VERSION_ERROR=1"
exit /b 0
