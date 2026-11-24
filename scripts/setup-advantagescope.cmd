@echo off
rem Installs the Block Bot 3D model where AdvantageScope looks for robots, so its 3D view draws the
rem Block Bot instead of its built-in KitBot. Double-click it in Explorer, or run it from any
rem terminal. Run it once after cloning, and again after a pull that changes assets\Robot_BlockBot;
rem then close AdvantageScope completely and open it again, because it reads models only at launch.
rem
rem A .cmd file is not a PowerShell script, so Windows' default execution policy does not block it.

setlocal
set "SOURCE=%~dp0..\assets\Robot_BlockBot"
set "TARGET=%APPDATA%\AdvantageScope\userAssets\Robot_BlockBot"

if not exist "%SOURCE%\config.json" (
  echo Cannot find the Block Bot model in "%SOURCE%".
  echo Run this script from the scripts folder of the basicsim26 project.
  goto :fail
)

rem /MIR makes the target an exact copy, removing files a newer model no longer has.
rem robocopy exit codes below 8 mean success.
robocopy "%SOURCE%" "%TARGET%" /MIR /NJH /NJS /NFL /NDL /NP >nul
if errorlevel 8 (
  echo Copying the model to "%TARGET%" failed.
  goto :fail
)

echo Installed the Block Bot model in:
echo   %TARGET%
echo.
echo Close AdvantageScope completely and open it again to see it.
call :pause_if_double_clicked
exit /b 0

:fail
call :pause_if_double_clicked
exit /b 1

rem A double-clicked script's window closes as soon as it ends; keep it open to show the result.
:pause_if_double_clicked
echo %cmdcmdline% | "%SystemRoot%\System32\find.exe" /i "%~nx0" >nul && pause
exit /b 0
