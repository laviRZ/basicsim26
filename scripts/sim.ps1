# Run the robot in desktop simulation.
#
#   .\scripts\sim.ps1                # Sim GUI (Robot State / DS controls in-GUI)
#   .\scripts\sim.ps1 --offline      # extra args are forwarded to Gradle untouched
#
# The Sim GUI extension is off in build.gradle unless the `simGui` project property is set:
# GradleRIO turns a default-enabled extension into HALSIM_EXTENSIONS for the forked JVM, and
# without it the HAL loads no extensions at all (what AdvantageKit log replay needs).
# simulateJava depends on extractReleaseNative, so the natives exist before the fork.
# halsim_ds_socket stays disabled: it hands DS state to a real DriverStation and takes the GUI's
# own Robot State controls out of the loop.

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

$root = Split-Path -Parent $PSScriptRoot
$jdk = Resolve-WpilibJavaHome -Root $root
if ($jdk) { $env:JAVA_HOME = $jdk }

# AdvantageScope only reads models from its own user assets folder. Mirror the committed model so
# a fresh clone draws the robot without running tools/build_block_bot.py.
$assets = Join-Path $root 'assets\Robot_BlockBot'
if ($env:APPDATA -and (Test-Path -LiteralPath $assets)) {
    $userAssets = Join-Path $env:APPDATA 'AdvantageScope\userAssets\Robot_BlockBot'
    New-Item -ItemType Directory -Force -Path $userAssets | Out-Null
    Copy-Item -Path (Join-Path $assets '*') -Destination $userAssets -Recurse -Force
}

exit (Invoke-WpilibGradle -Root $root -GradleArgs (@('simulateJava', '-PsimGui', '--console=plain') + $args))
