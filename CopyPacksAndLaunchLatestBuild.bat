echo Copying packs...
call CopyPacks.bat

:: Check if BiomeTool is already running using PowerShell (more reliable)
call StartBiomeTool.bat

BORDER_LAND_CAVES
