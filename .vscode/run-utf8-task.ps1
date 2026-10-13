param(
    [Parameter(Mandatory = $true)]
    [string]$GradleTask
)

$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
$null = chcp 65001

$gradleWrapper = Join-Path $PSScriptRoot "..\gradlew.bat"
& $gradleWrapper --no-daemon --max-workers=1 $GradleTask
exit $LASTEXITCODE
