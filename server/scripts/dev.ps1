param([string]$Task='run',[Parameter(ValueFromRemainingArguments=$true)][string[]]$GradleArgs)
$ErrorActionPreference='Stop'
$serverRoot=Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $serverRoot
$previousJavaTools=$env:JAVA_TOOL_OPTIONS
try {
    if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath 'C:/Program Files/Android/Android Studio/jbr/bin/java.exe')) {
        $env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
    }
    $tempRoot=Join-Path $serverRoot '.local/t'
    New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
    $env:JAVA_TOOL_OPTIONS="$previousJavaTools -Djava.io.tmpdir=`"$tempRoot`" -Djdk.net.unixdomain.tmpdir=`"$tempRoot`""
    & ./gradlew.bat $Task @GradleArgs --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    $env:JAVA_TOOL_OPTIONS=$previousJavaTools
    Pop-Location
}
