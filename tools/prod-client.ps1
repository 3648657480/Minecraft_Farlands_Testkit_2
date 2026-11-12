# Launch the INSTALLED client (patched fork jar + mod jar, Knot) from the CLI.
#
# Why: `gradle runClient` and a launcher install differ in more than mappings -
# different launcher class, different argument set, different class-load order.
# This script reproduces the install path while letting us control JVM flags, so
# "dev launcher vs production launcher" can be tested as a single variable.
#
# Usage:
#   .\tools\prod-client.ps1 [-Version "26.2-Fabric_0.19.3_fork_v2"] [-JvmArgs "-Dfoo=1"] [-ExtraGameArgs "--quickPlaySingleplayer","world"]
param(
    [string]$Version = "26.2-Fabric_0.19.3_fork_v2",
    [string]$GameDir = "D:\Minecraft\.minecraft",
    [string[]]$JvmArgs = @(),
    [string[]]$ExtraGameArgs = @()
)

$ErrorActionPreference = "Stop"
$verDir = Join-Path $GameDir "versions\$Version"
$json = Get-Content (Join-Path $verDir "$Version.json") -Raw | ConvertFrom-Json
$java = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe"
if (-not (Test-Path $java)) { throw "java not found: $java" }

# --- classpath from the library list -----------------------------------------
function Lib-Path([string]$name) {
    # group:artifact:version[:classifier]
    $parts = $name.Split(':')
    $group = $parts[0] -replace '\.', '\'
    $artifact = $parts[1]
    $version = $parts[2]
    $classifier = if ($parts.Count -gt 3) { "-" + $parts[3] } else { "" }
    return Join-Path $GameDir "libraries\$group\$artifact\$version\$artifact-$version$classifier.jar"
}

$cp = New-Object System.Collections.Generic.List[string]
$cp.Add((Join-Path $verDir "$Version.jar"))
foreach ($lib in $json.libraries) {
    if ($null -eq $lib.name) { continue }
    $p = Lib-Path $lib.name
    if (Test-Path $p) { $cp.Add($p) } else { Write-Host "[prod-client] missing lib: $p" }
}

# --- argument resolution ------------------------------------------------------
$assetIndex = if ($json.assetIndex) { $json.assetIndex.id } else { "legacy" }
$vars = @{
    '${auth_player_name}'   = 'ProdTest'
    '${version_name}'       = $Version
    '${game_directory}'     = $verDir
    '${assets_root}'        = (Join-Path $GameDir 'assets')
    '${assets_index_name}'  = $assetIndex
    '${auth_uuid}'          = '00000000000000000000000000000000'
    '${auth_access_token}'  = '0'
    '${clientid}'           = '0'
    '${auth_xuid}'          = '0'
    '${user_type}'          = 'legacy'
    '${version_type}'       = 'release'
    '${natives_directory}'  = (Join-Path $verDir "$Version-natives")
    '${launcher_name}'      = 'prod-client'
    '${launcher_version}'   = '1'
    '${classpath}'          = ($cp -join ';')
    '${resolution_width}'   = '854'
    '${resolution_height}'  = '480'
}

function Expand-Arg($v) {
    $s = [string]$v
    foreach ($k in $vars.Keys) { $s = $s.Replace($k, $vars[$k]) }
    return $s
}

$jvm = New-Object System.Collections.Generic.List[string]
foreach ($a in $json.arguments.jvm) {
    if ($a -is [string]) { $jvm.Add((Expand-Arg $a)); continue }
    # rules object: take it unless an os rule says otherwise (we are windows)
    $skip = $false
    foreach ($r in $a.rules) {
        if ($r.os -and $r.os.name -and $r.os.name -ne 'windows') { $skip = $true }
        if ($r.action -eq 'allow' -and $r.os -and $r.os.name -eq 'windows') { $skip = $false }
    }
    if (-not $skip) { foreach ($v in $a.value) { $jvm.Add((Expand-Arg $v)) } }
}
foreach ($a in $JvmArgs) { $jvm.Add($a) }

$game = New-Object System.Collections.Generic.List[string]
foreach ($a in $json.arguments.game) {
    if ($a -is [string]) { $game.Add((Expand-Arg $a)) }
}
foreach ($a in $ExtraGameArgs) { $game.Add($a) }

$argList = @()
$argList += $jvm
$argList += '-cp'
$argList += ($cp -join ';')
$argList += $json.mainClass
$argList += $game

Write-Host "[prod-client] version=$Version libs=$($cp.Count) main=$($json.mainClass)"
Write-Host "[prod-client] jvmArgs=$($jvm -join ' ')"
& $java @argList
