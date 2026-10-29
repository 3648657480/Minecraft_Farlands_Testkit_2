param(
    [ValidateSet("norm", "seam", "coll", "save", "dist", "all")][string]$Mode = "all",
    [int]$NormTimeoutMin = 8,
    [int]$SeamTimeoutMin = 14,
    [int]$CollTimeoutMin = 8,
    [int]$SaveTimeoutMin = 8,
    [int]$DistTimeoutMin = 12,
    [switch]$AllowConcurrent
)

# Regression gate ("canary") for the wide branch.
# Runs the headless rig and checks the known-good numbers. Exit code 1 on any
# failure, so it can be wired into a build step or run by the next session.
$ErrorActionPreference = "Stop"
$root = "C:\Project-G2"
$aux = "C:\Project-G2-aux\logs"
$results = @()

function Get-Log([string]$tag) {
    return "C:\Users\EASON\AppData\Local\Temp\opencode\exp-$tag.log"
}

function Invoke-Run([string]$tag, [string]$outDir, [string]$testGen, [int]$timeoutMin,
    [string]$extra, [switch]$keepWorld) {
    $runArgs = @{
        Tag = $tag
        OutDir = $outDir
        TestGen = $testGen
        TimeoutMin = $timeoutMin
        Settle = 40
        Extra = $extra
    }
    if ($keepWorld) { $runArgs.KeepWorld = $true }
    & "$root\tools\exp-run.ps1" @runArgs
}

function Add-Result([string]$name, [bool]$ok, [string]$detail) {
    $script:results += [pscustomobject]@{ Check = $name; Ok = $ok; Detail = $detail }
    $tag = if ($ok) { "PASS" } else { "FAIL" }
    Write-Host ("[{0}] {1}: {2}" -f $tag, $name, $detail)
}

function Assert-NoConcurrentClient() {
    if ($AllowConcurrent) { return }
    $clients = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object {
            $_.CommandLine -and
            $_.CommandLine -match 'devlaunchinjector|net\.fabricmc\.devlaunchinjector' -and
            $_.CommandLine -notmatch 'farlands\.test\.run='
        }
    if ($clients) {
        Write-Host "[ABORT] A dev client appears to be running. Close it or pass -AllowConcurrent." -ForegroundColor Red
        exit 2
    }
}

function Test-Norm() {
    $tag = "verify-norm"
    Invoke-Run -tag $tag -outDir "$aux\verify-norm" -testGen "0,0" -timeoutMin $NormTimeoutMin `
        -extra "-Dfarlands.test.allowDedicated=true"
    $log = Get-Log $tag
    $line = Select-String -Path $log -Pattern "gen OK" | Select-Object -Last 1
    if (-not $line) {
        Add-Result "norm fingerprint" $false "no 'gen OK' line in $log"
        return
    }
    $m = [regex]::Match($line.Line, 'surf=(?<surf>[0-9a-f]+)\s+floor=(?<floor>[0-9a-f]+)\s+biome=(?<biome>[0-9a-f]+)')
    if (-not $m.Success) {
        Add-Result "norm fingerprint" $false "could not parse: $($line.Line)"
        return
    }
    $ok = ($m.Groups['surf'].Value -eq "b9216bad2029") -and
          ($m.Groups['floor'].Value -eq "8936c275d063") -and
          ($m.Groups['biome'].Value -eq "4bf4e2ea5526")
    Add-Result "norm fingerprint" $ok ("surf={0} floor={1} biome={2}" -f `
        $m.Groups['surf'].Value, $m.Groups['floor'].Value, $m.Groups['biome'].Value)
}

function Test-Seam() {
    $tag = "verify-seam"
    $world = "$root\mod\run\world"
    $src = "$root\mod\run\saves\test2"
    if (-not (Test-Path $src)) {
        Add-Result "seam fixture" $false "missing fixture world: $src"
        return
    }
    if (Test-Path $world) { Remove-Item $world -Recurse -Force }
    Copy-Item $src $world -Recurse -Force
    $overworld = "$world\dimensions\minecraft\overworld"
    if (Test-Path $overworld) { Remove-Item $overworld -Recurse -Force }

    Invoke-Run -tag $tag -outDir "$aux\verify-seam" -testGen "134217727,0;134217728,0" `
        -timeoutMin $SeamTimeoutMin -keepWorld `
        -extra "-Dfarlands.test.allowDedicated=true -Dfarlands.testgen.seam=16 -Dfarlands.diag.noFeatures=true"
    $log = Get-Log $tag
    $line = Select-String -Path $log -Pattern "\[SEAM\]" | Select-Object -Last 1
    if (-not $line) {
        Add-Result "seam" $false "no [SEAM] line in $log"
        return
    }
    $dMatch = [regex]::Match($line.Line, 'maxDelta=(?<v>-?\d+)')
    $fMatch = [regex]::Match($line.Line, 'maxFloorDelta=(?<v>-?\d+)')
    $bMatch = [regex]::Match($line.Line, 'biomeMismatch=(?<b>\d+)/(?<n>\d+)')
    if (-not $dMatch.Success -or -not $fMatch.Success -or -not $bMatch.Success) {
        Add-Result "seam" $false "could not parse: $($line.Line)"
        return
    }
    $d = [int]$dMatch.Groups['v'].Value
    $f = [int]$fMatch.Groups['v'].Value
    $b = [int]$bMatch.Groups['b'].Value
    $ok = ($d -le 1) -and ($f -le 1) -and ($b -eq 0)
    Add-Result "seam" $ok ("maxDelta={0} maxFloorDelta={1} biomeMismatch={2}/{3}" -f `
        $d, $f, $b, $bMatch.Groups['n'].Value)
}

function Test-Coll() {
    $tag = "verify-coll"
    Invoke-Run -tag $tag -outDir "$aux\verify-coll" -testGen "134217728,0" -timeoutMin $CollTimeoutMin `
        -extra "-Dfarlands.test.allowDedicated=true -Dfarlands.diag.collision=true"
    $log = Get-Log $tag
    $clip = Select-String -Path $log -Pattern "\[CLIP\]" |
        ForEach-Object { $_.Line } |
        Where-Object { $_ -match '2147483648' -and $_ -match 'miss=false' } |
        Select-Object -Last 1
    $blk = Select-String -Path $log -Pattern "\[COLLBLK\]" |
        ForEach-Object { $_.Line } |
        Where-Object { $_ -match '2147483648' -and $_ -match 'n=1' } |
        Select-Object -Last 1
    $ok = ($null -ne $clip) -and ($null -ne $blk)
    $detail = "CLIP=" + ($(if ($clip) { "hit" } else { "missing" })) +
              " COLLBLK=" + ($(if ($blk) { "n=1" } else { "missing" }))
    Add-Result "collision/pick at 2^31" $ok $detail
}

function Test-Save() {
    $tagA = "verify-save-a"
    $tagB = "verify-save-b"
    $world = "$root\mod\run\world"
    if (Test-Path $world) { Remove-Item $world -Recurse -Force }
    Invoke-Run -tag $tagA -outDir "$aux\verify-save-a" -testGen "0,0" `
        -timeoutMin $SaveTimeoutMin -keepWorld `
        -extra "-Dfarlands.test.allowDedicated=true -Dfarlands.y.window=true -Dfarlands.gen.cwg=true -Dfarlands.testgen.cwg=134217714 -Dfarlands.testgen.savemark=134217714 -Dfarlands.testgen.blockent=true"
    $logA = Get-Log $tagA
    $mark = Select-String -Path $logA -Pattern '\[SAVEMARK\].*read=Block\{minecraft:gold_block\}' |
        Select-Object -Last 1
    if (-not $mark) {
        Add-Result "save round-trip (far Y)" $false "phase A did not write the gold marker"
        return
    }
    Invoke-Run -tag $tagB -outDir "$aux\verify-save-b" -testGen "0,0" `
        -timeoutMin $SaveTimeoutMin -keepWorld `
        -extra "-Dfarlands.test.allowDedicated=true -Dfarlands.y.window=true -Dfarlands.farsex.save=false -Dfarlands.testgen.readsy=134217714 -Dfarlands.testgen.readbe=true"
    $logB = Get-Log $tagB
    $read = Select-String -Path $logB -Pattern '\[READSY\].*ok=true' | Select-Object -Last 1
    $be = Select-String -Path $logB -Pattern '\[READBE\].*ok=true' | Select-Object -Last 1
    $ok = ($null -ne $read) -and ($null -ne $be)
    $detail = if ($ok) {
        "gold marker + normal-band chest reloaded"
    } else {
        "gold=" + ($(if ($read) { "ok" } else { "missing" })) +
        " chest=" + ($(if ($be) { "ok" } else { "missing" }))
    }
    Add-Result "save round-trip (far Y + block entity)" $ok $detail
}

function Test-Dist() {
    $tag = "verify-dist53"
    Invoke-Run -tag $tag -outDir "$aux\verify-dist53" -testGen "562949953421312,0" `
        -timeoutMin $DistTimeoutMin `
        -extra "-Dfarlands.test.allowDedicated=true"
    $log = Get-Log $tag
    $gen = Select-String -Path $log -Pattern 'gen OK region \(562949953421312,0\)' |
        Select-Object -Last 1
    $sec = Select-String -Path $log -Pattern 'section2560 x=562949953421312.*ok=true' |
        Select-Object -Last 1
    $ok = ($null -ne $gen) -and ($null -ne $sec)
    $detail = if ($ok) {
        $hash = [regex]::Match($gen.Line, 'wgHash=surf=([0-9a-f]+) floor=([0-9a-f]+)')
        "chunk 2^49 / block 2^53 gen OK; surf=$($hash.Groups[1].Value) floor=$($hash.Groups[2].Value)"
    } else {
        "2^53 generation or section2560 check missing"
    }
    Add-Result "distance 2^53 (chunk 2^49)" $ok $detail
}

Assert-NoConcurrentClient

switch ($Mode) {
    "norm" { Test-Norm }
    "seam" { Test-Seam }
    "coll" { Test-Coll }
    "save" { Test-Save }
    "dist" { Test-Dist }
    "all" {
        Test-Norm
        Test-Seam
        Test-Coll
        Test-Save
        Test-Dist
    }
}

Write-Host ""
$failed = @($results | Where-Object { -not $_.Ok })
Write-Host ("RESULT: {0}/{1} passed" -f ($results.Count - $failed.Count), $results.Count)
if ($failed.Count -gt 0) {
    Write-Host "FAILED:" -ForegroundColor Red
    $failed | ForEach-Object { Write-Host ("  - {0}: {1}" -f $_.Check, $_.Detail) }
    exit 1
}
exit 0
