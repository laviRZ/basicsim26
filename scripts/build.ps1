# Compile, run the tests and the spotless checks (Gradle `build`).
#
#   .\scripts\build.ps1              # full build
#   .\scripts\build.ps1 --offline    # extra args are forwarded to Gradle
#
# Note: this project wires spotlessApply into compileJava, so building also
# reformats sources. Use scripts/lint.ps1 for a non-mutating format check.

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

$root = Split-Path -Parent $PSScriptRoot
$jdk = Resolve-WpilibJavaHome -Root $root
if ($jdk) { $env:JAVA_HOME = $jdk }

exit (Invoke-WpilibGradle -Root $root -GradleArgs (@('build', '--console=plain') + $args))
