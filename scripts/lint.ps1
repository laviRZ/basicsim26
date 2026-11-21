# Spotless formatting check (no file changes).
#
#   .\scripts\lint.ps1           # gradlew spotlessCheck
#   .\scripts\lint.ps1 --fix     # gradlew spotlessApply (rewrites files)
#
# Checks java (google-java-format), *.gradle (greclipse), *.json (gson),
# *.md/.gitignore (whitespace).
#
# Reminder: compileJava depends on spotlessApply in build.gradle, so a plain
# build silently reformats instead of failing. Run this before committing.

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

$fix = $false
$gradleArgs = @()
foreach ($arg in $args) {
    if ($arg -in @('--fix', '-Fix')) { $fix = $true } else { $gradleArgs += $arg }
}

$root = Split-Path -Parent $PSScriptRoot
$jdk = Resolve-WpilibJavaHome -Root $root
if ($jdk) { $env:JAVA_HOME = $jdk }

$task = if ($fix) { 'spotlessApply' } else { 'spotlessCheck' }
exit (Invoke-WpilibGradle -Root $root -GradleArgs (@($task, '--console=plain') + $gradleArgs))
