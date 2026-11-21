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

exit (Invoke-WpilibGradle -Root $root -GradleArgs (@('simulateJava', '-PsimGui', '--console=plain') + $args))
