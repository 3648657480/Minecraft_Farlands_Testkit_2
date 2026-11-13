# Compare the [RDIAG] time series of two A/B runs (dev client vs installed client).
#
# Metrics are about convergence, not averages:
#   store        - far chunks the client has decoded (data arrival)
#   uncompiled   - visible sections still waiting for a mesh
#   queue        - compile queue depth
#   first/last   - so a run that never reached the far domain is obvious
#
# Usage: tools\ab-compare.ps1 -PathA <log> -PathB <log>
param(
    [Parameter(Mandatory = $true)][string]$PathA,
    [Parameter(Mandatory = $true)][string]$PathB
)

$pattern = '\[(?<t>\d{2}:\d{2}:\d{2})\].*\[RDIAG\] visible=(?<vis>\d+)/(?<tot>\d+) uncompiled=(?<unc>\d+).*?store=(?<store>\d+).*?farMeshSec=(?<fms>\d+).*?queue=(?<q>\d+).*?player=(?<px>[-\d.E]+),(?<py>[-\d.E]+),(?<pz>[-\d.E]+)'

function Get-Samples([string]$logPath) {
    $acc = New-Object System.Collections.Generic.List[object]
    foreach ($line in Get-Content -LiteralPath $logPath) {
        $m = [regex]::Match($line, $pattern)
        if (-not $m.Success) { continue }
        $acc.Add([pscustomobject]@{
            T     = [timespan]::Parse($m.Groups['t'].Value)
            Vis   = [int]$m.Groups['vis'].Value
            Unc   = [int]$m.Groups['unc'].Value
            Store = [int]$m.Groups['store'].Value
            Fms   = [int]$m.Groups['fms'].Value
            Q     = [int]$m.Groups['q'].Value
            Px    = [double]::Parse($m.Groups['px'].Value, [Globalization.CultureInfo]::InvariantCulture)
        })
    }
    return ,$acc
}

function Show-Run([string]$name, $samples) {
    if ($samples.Count -eq 0) { return "$name : NO [RDIAG] LINES" }
    $far = @($samples | Where-Object { [math]::Abs($_.Px) -gt 1.0e9 })
    if ($far.Count -eq 0) { return "$name : never entered far domain ($($samples.Count) samples)" }
    $t0 = $far[0].T
    $last = $far[-1]
    $span = ($last.T - $t0).TotalSeconds
    $store81 = $far | Where-Object { $_.Store -ge 81 } | Select-Object -First 1
    $unc0 = $far | Where-Object { $_.Unc -eq 0 } | Select-Object -First 1
    $meshOk = $far | Where-Object { $_.Unc -eq 0 -and $_.Fms -ge 1 } | Select-Object -First 1
    $storeMax = ($far | Measure-Object -Property Store -Maximum).Maximum
    $qMax = ($far | Measure-Object -Property Q -Maximum).Maximum
    $secs = { param($x) if ($null -eq $x) { "-" } else { (($x.T - $t0).TotalSeconds).ToString("0.0") + "s" } }
    $out = @()
    $out += ("{0}: far samples={1} span={2:N1}s" -f $name, $far.Count, $span)
    $out += ("{0}  store>=81 at {1} (max={2})" -f $name, (& $secs $store81), $storeMax)
    $out += ("{0}  uncompiled=0 at {1}; mesh+uncompiled0 at {2}; queue max={3}" -f $name, (& $secs $unc0), (& $secs $meshOk), $qMax)
    $out += ("{0}  end: visible={1} uncompiled={2} store={3} farMeshSec={4}" -f $name, $last.Vis, $last.Unc, $last.Store, $last.Fms)
    return ($out -join "`n")
}

$nameA = [IO.Path]::GetFileNameWithoutExtension($PathA)
$nameB = [IO.Path]::GetFileNameWithoutExtension($PathB)
Show-Run $nameA (Get-Samples $PathA)
""
Show-Run $nameB (Get-Samples $PathB)
