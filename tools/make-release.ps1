# Build the G1-style release zip for a given version.
#
# Layout (mirrors FarLands-G1-<ver>.zip):
#   FarLands-G2-<ver>/  farlands-g2-mod-<ver>.jar, patcher-cli-<ver>.jar,
#                       INSTALL.md, LICENSE.txt, patch.bat, patch.ps1,
#                       README.md, README.zh-CN.md, docs/<selected>.md
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\make-release.ps1 -Version 0.1.0-alpha1
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$OutDir = "dist"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$stage = Join-Path $root "$OutDir\FarLands-G2-$Version"
$docs = @(
    "ALPHA1.md",
    "BETA.md",
    "CURRENT.md",
    "PHENOMENA.md",
    "TOMBSTONE.md",
    "USAGE.md",
    "USAGE.en.md",
    "CONFIG.md",
    "CONFIG.en.md",
    "ARCHITECTURE.md",
    "REVIEW.md",
    "RELEASE-NOTES-ALPHA1.md"
)

if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $stage "docs") | Out-Null

Copy-Item (Join-Path $root "mod\build\libs\farlands-g2-mod-0.1.0.jar") `
    (Join-Path $stage "farlands-g2-mod-0.1.0.jar")
Copy-Item (Join-Path $root "patcher-cli\build\libs\patcher-cli-0.1.0.jar") `
    (Join-Path $stage "patcher-cli-0.1.0.jar")
foreach ($f in @("INSTALL.md", "LICENSE", "patch.bat", "patch.ps1", "README.md", "README.zh-CN.md")) {
    if (-not (Test-Path (Join-Path $root $f))) { throw "missing $f" }
    $dest = if ($f -eq "LICENSE") { "LICENSE.txt" } else { $f }
    Copy-Item (Join-Path $root $f) (Join-Path $stage $dest)
}
foreach ($d in $docs) {
    if (-not (Test-Path (Join-Path $root "docs\$d"))) { throw "missing docs\$d" }
    Copy-Item (Join-Path $root "docs\$d") (Join-Path $stage "docs\$d")
}

# Normalise line endings for the Windows helper scripts (LF works, CRLF is safer for .bat).
foreach ($f in @("patch.bat", "patch.ps1")) {
    $p = Join-Path $stage $f
    $t = [System.IO.File]::ReadAllText($p)
    $t = $t -replace "`r`n", "`n" -replace "`n", "`r`n"
    [System.IO.File]::WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($false)))
}

$zip = Join-Path $root "$OutDir\FarLands-G2-$Version.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path $stage -DestinationPath $zip -CompressionLevel Optimal

$hash = (Get-FileHash $zip).Hash
$size = (Get-Item $zip).Length
Write-Host ("zip   : {0}" -f $zip)
Write-Host ("size  : {0:N0} bytes" -f $size)
Write-Host ("sha256: {0}" -f $hash)
Write-Host "contents:"
Get-ChildItem $stage -Recurse -File | ForEach-Object {
    "  " + $_.FullName.Substring($stage.Length + 1) + "  (" + $_.Length + " bytes)"
}

