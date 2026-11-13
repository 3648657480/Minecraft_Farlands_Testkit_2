# Launch one side of the "runClient vs installed client" A/B and stop it after N seconds.
#
# Why a dedicated runner: the two sides start differently (Gradle runClient vs
# prod-client.ps1/KnotClient), so the only way to compare them is to give both the
# same procedure, the same time window and the same kill rule. The game JVM is
# tagged with -Dfarlands.ab.tag=<Tag>, so only that process tree is terminated -
# a launcher's or the user's own client is never touched.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\ab-client.ps1 `
#     -Mode dev|prod -Tag name -Seconds 240 `
#     -JvmArgs "-Dfarlands.autotest=true;-D..." -GameArgs "--quickPlaySingleplayer;world"
#
# Output: C:\Project-G2-aux\logs\ab-<Tag>.log plus ab-<Tag>.rdiag.txt (the
# [RDIAG] time series, which is what the comparison reads).
param(
    [ValidateSet("dev", "prod")][string]$Mode = "dev",
    [Parameter(Mandatory = $true)][string]$Tag,
    [int]$Seconds = 240,
    [string]$JvmArgs = "",
    [string]$GameArgs = ""
)

$ErrorActionPreference = "Stop"
$root = "C:\Project-G2"
$logDir = "C:\Project-G2-aux\logs"
$log = Join-Path $logDir "ab-$Tag.log"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
if (Test-Path $log) { Remove-Item $log -Force }

# Marker so the cleanup can find exactly this game JVM.
$marker = "-Dfarlands.ab.tag=$Tag"
if ($Mode -eq "dev") {
    # build.gradle forwards every -Dfarlands.* property to the game JVM.
    $props = @()
    foreach ($a in ($JvmArgs -split ';')) {
        $t = $a.Trim()
        if ($t) { $props += $t }
    }
    $props += $marker
    $propLine = ($props -join ' ')
    $cmd = "cd /d $root && .\gradlew.bat $propLine :mod:runClient --no-daemon -q > `"$log`" 2>&1"
} else {
    $jvm = $JvmArgs
    if ($jvm) { $jvm = $jvm + ";" + $marker } else { $jvm = $marker }
    $cmd = "cd /d $root && powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\prod-client.ps1 -JvmArgs `"$jvm`" -ExtraGameArgs `"$GameArgs`" > `"$log`" 2>&1"
}

$proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c $cmd" -WindowStyle Hidden -PassThru
Write-Host "[ab-client] mode=$Mode tag=$Tag pid=$($proc.Id) seconds=$Seconds"
Write-Host "[ab-client] log=$log"

# Wait for the log, then for boot + the observation window.
$deadline = (Get-Date).AddSeconds($Seconds)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
}

# Kill only this run: the cmd tree plus java processes carrying our marker.
taskkill /PID $proc.Id /T /F 2>$null | Out-Null
$victims = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine.Contains($marker) }
foreach ($v in $victims) { taskkill /PID $v.ProcessId /T /F 2>$null | Out-Null }

$rdiag = Join-Path $logDir "ab-$Tag.rdiag.txt"
if (Test-Path $log) {
    Select-String -Path $log -Pattern "\[RDIAG\] |\[FARPUSH\]|\[WIDEBASE\]|\[FARSTREAM\]|\[STALL\]|Exception|ERROR" |
        ForEach-Object { $_.Line } | Set-Content -Path $rdiag -Encoding UTF8
    $lines = (Get-Content $log).Count
    Write-Host "[ab-client] log lines=$lines rdiag=$rdiag"
    $first = Select-String -Path $log -Pattern "\[RDIAG\] visible=" | Select-Object -First 1
    $last = Select-String -Path $log -Pattern "\[RDIAG\] visible=" | Select-Object -Last 1
    if ($first) { Write-Host ("[ab-client] first " + $first.Line) }
    if ($last) { Write-Host ("[ab-client] last  " + $last.Line) }
} else {
    Write-Host "[ab-client] no log produced"
}
