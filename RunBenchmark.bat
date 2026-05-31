@echo off
setlocal enabledelayedexpansion
title BiomeTool Benchmark
mode con: cols=100 lines=40

set "SCRIPT_DIR=%~dp0"
set "JAR_PATTERN=BiomeToolEnhanced-*-all.jar"
set "MIN_JAVA_VERSION=25"
set "JAVA_EXE="

:: ============================================================================
:: BiomeTool Benchmark Script
:: Usage: RunBenchmark.bat [tilesX] [tilesY] [seed] [skipPause] [subsample] [lod] [threads] [overflowCheck] [packNameOrPath]
:: Defaults: 200x200 tiles, seed default, subsample 32, lod 0, threads 12, overflowCheck 1, pack CHIMERA
::
:: subsample and lod mirror the UI: world area per tile = 128 * subsample (blocks),
:: lod halves pixel count and doubles stride, matching TerraBiomeImageGenerator exactly.
::
:: threads:        number of concurrent worker threads (default 12).
:: overflowCheck:  1 = enabled (default), 0 = disabled (use 0 for fair speed comparison with the UI).
:: packNameOrPath: pack ID (e.g. CHIMERA) OR an absolute path to a pack folder.
::                 When a path is given, only that pack is loaded — faster startup, no cross-pack noise.
::                 The folder name is used as the pack ID automatically.
::
:: Examples:
::   RunBenchmark.bat                                              (200x200, default seed, 12t, CHIMERA)
::   RunBenchmark.bat 50                                           (50x50, CHIMERA)
::   RunBenchmark.bat 20 20 1 0 4 0 1 0                           (20x20, overflow DISABLED)
::   RunBenchmark.bat 200 200 1 0 4 0 4 1 REIMAGEND               (200x200, 4 threads, REIMAGEND by name)
::   RunBenchmark.bat 200 200 1 0 32 0 12 1 C:\Projects\CHIMERA   (load only CHIMERA from explicit path)
:: ============================================================================

set "TILES_X=%~1"
set "TILES_Y=%~2"
set "SEED=%~3"
set "SKIP_PAUSE=%~4"
set "SUBSAMPLE=%~5"
set "LOD=%~6"
set "THREADS=%~7"
set "OVERFLOW_CHECK=%~8"
set "PACK_NAME=%~9"
set "PACK_PATH="

if "%TILES_X%"==""        set "TILES_X=200"
if "%TILES_Y%"==""        set "TILES_Y=%TILES_X%"
if "%SEED%"==""           set "SEED=7099699057166038826"
if "%SKIP_PAUSE%"==""     set "SKIP_PAUSE=0"
if "%SUBSAMPLE%"==""      set "SUBSAMPLE=32"
if "%LOD%"==""            set "LOD=0"
if "%THREADS%"==""        set "THREADS=16"
if "%OVERFLOW_CHECK%"=="" set "OVERFLOW_CHECK=1"
if "%PACK_NAME%"==""      set "PACK_NAME=CHIMERA"

:: If arg 9 is a path to an existing directory, treat it as a single-pack override.
:: Derive the pack name from the folder name; pass the full path to the benchmark.
if defined PACK_NAME (
    if exist "!PACK_NAME!\" (
        for %%D in ("!PACK_NAME!") do set "PACK_NAME=%%~nxD"
        set "PACK_PATH=%~9"
    )
)

:: ── 1. Optional dev-mode pack sync ───────────────────────────────────────────
:: Only run CopyPacks.bat if it exists (developer environment with ORIGEN2 source).
:: Released distributions ship packs in build/libs/packs/ already (unzipped from packs.zip).
if exist "%SCRIPT_DIR%CopyPacks.bat" (
    if exist "%SCRIPT_DIR%..\ORIGEN2\" (
        echo  Detected ORIGEN2 source — syncing packs via CopyPacks.bat...
    )
)
        ::call "%SCRIPT_DIR%CopyPacks.bat"

:: ── 2. Find the JAR ──────────────────────────────────────────────────────────
set "JAR_FILE="
for %%F in ("%SCRIPT_DIR%%JAR_PATTERN%") do set "JAR_FILE=%%F"
if not defined JAR_FILE (
    for %%F in ("%SCRIPT_DIR%build\libs\%JAR_PATTERN%") do set "JAR_FILE=%%F"
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

:: ── 3. Locate Java ───────────────────────────────────────────────────────────
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

:: ── 4. Validate Java version ─────────────────────────────────────────────────
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
echo  Using JAR:              !JAR_FILE!
echo.

:: ── 5. Launch benchmark ──────────────────────────────────────────────────────
:: Pack name is appended by the tool at runtime: benchmark_{PACK}.csv (stable for diffing).
set "CSV_PREFIX=%SCRIPT_DIR%benchmark_"

echo  Launching benchmark: %TILES_X%x%TILES_Y% tiles, seed %SEED%, subsample %SUBSAMPLE%x, lod %LOD%, %THREADS% thread(s), overflow=%OVERFLOW_CHECK%, pack %PACK_NAME%
if defined PACK_PATH echo  Pack path: %PACK_PATH%
echo.

for %%F in ("%JAR_FILE%") do set "JAR_DIR=%%~dpF"
cd /d "!JAR_DIR!"

"!JAVA_EXE!" --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED ^
    --add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED ^
    -cp "%JAR_FILE%" com.dfsek.terra.biometool.BiomeBenchmark ^
    %TILES_X% %TILES_Y% %SEED% "%CSV_PREFIX%" %SUBSAMPLE% %LOD% %THREADS% %OVERFLOW_CHECK% %PACK_NAME% "%PACK_PATH%"

set "EXIT_CODE=!errorlevel!"
echo.

if "%SKIP_PAUSE%"=="1" goto end
if !EXIT_CODE! neq 0 (
    echo  Benchmark exited with error code !EXIT_CODE!.
)
pause

:end
endlocal
exit /b %EXIT_CODE%


:: ═══════════════════════════════════════════════════════════════════════════════
:: Subroutines (shared with StartBiomeTool.bat)
:: ═══════════════════════════════════════════════════════════════════════════════

:find_java
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
        exit /b 0
    )
)

where java >nul 2>&1
if %errorlevel% == 0 (
    for /f "delims=" %%J in ('where java') do (
        if not defined JAVA_EXE set "JAVA_EXE=%%J"
    )
    exit /b 0
)

call :scan_root "C:\Program Files\Java"
call :scan_root "C:\Program Files\Eclipse Adoptium"
call :scan_root "C:\Program Files\Microsoft"
call :scan_root "C:\Program Files\Amazon Corretto"
call :scan_root "C:\Program Files\BellSoft"
call :scan_root "C:\Program Files\Azul Systems\Zulu"
call :scan_root "C:\JAVA"
exit /b 0

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
