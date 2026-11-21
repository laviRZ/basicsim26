# Shared helpers for the scripts/ launchers. Dot-source, do not run.

# JDK resolution order:
#   1. $env:JAVA_HOME, if it contains bin\java.exe
#   2. %PUBLIC%\wpilib\<projectYear>\jdk, where projectYear comes from
#      .wpilib/wpilib_preferences.json (the JDK the project was created with)
#   3. newest %PUBLIC%\wpilib\*\jdk that has bin\java.exe
#   4. none -> leave JAVA_HOME unset and let Gradle use `java` from PATH
function Resolve-WpilibJavaHome {
    param([Parameter(Mandatory)][string]$Root)

    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return $env:JAVA_HOME
    }

    $public = if ($env:PUBLIC) { $env:PUBLIC } else { 'C:\Users\Public' }
    $wpilibRoot = Join-Path $public 'wpilib'
    $candidates = @()

    $prefs = Join-Path $Root '.wpilib\wpilib_preferences.json'
    if (Test-Path -LiteralPath $prefs) {
        $year = (Get-Content -LiteralPath $prefs -Raw | ConvertFrom-Json).projectYear
        if ($year) { $candidates += Join-Path $wpilibRoot "$year\jdk" }
    }

    $candidates += Get-ChildItem -LiteralPath $wpilibRoot -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName 'jdk' }

    foreach ($jdk in $candidates) {
        if (Test-Path -LiteralPath (Join-Path $jdk 'bin\java.exe')) { return $jdk }
    }

    if (Get-Command java -ErrorAction SilentlyContinue) { return $null }

    throw "No JDK found. Set JAVA_HOME or install WPILib (looked in $wpilibRoot)."
}

# Runs gradlew.bat from the repo root; returns its exit code.
function Invoke-WpilibGradle {
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][string[]]$GradleArgs
    )

    Push-Location -LiteralPath $Root
    try {
        # Out-Host keeps stdout streaming while the function returns only the exit code.
        & (Join-Path $Root 'gradlew.bat') @GradleArgs | Out-Host
        return $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}
