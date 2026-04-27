[CmdletBinding()]
param(
    [string]$ReportRoot = "build/reports/pluginVerifier"
)

$SummaryFile = "build/reports/pluginVerifier/githubSummary.md"

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Escape-TableCell {
    param(
        [AllowEmptyString()]
        [string]$Value
    )

    # GitHub tables break on raw newlines and pipe characters, so normalize them.
    return ($Value -replace "`r", " " -replace "`n", " " -replace "\|", "\\|")
}

function Get-MetricValue {
    param(
        [string]$FilePath,
        [string]$Label
    )

    # Telemetry files are simple "Label: Value" pairs. Match the first exact label.
    $prefix = "${Label}:"
    foreach ($line in Get-Content -Path $FilePath) {
        if ($line.StartsWith($prefix)) {
            return $line.Substring($prefix.Length).Trim()
        }
    }

    return ""
}

function Write-CodeBlockFromFile {
    param(
        [string]$FilePath
    )

    if ((Test-Path -Path $FilePath -PathType Leaf) -and (Get-Item -Path $FilePath).Length -gt 0) {
        Write-Output '```text'
        Get-Content -Path $FilePath | Write-Output
        Write-Output '```'
        return
    }

    Write-Output 'No data available.'
}

if (-not (Test-Path -Path $ReportRoot -PathType Container)) {
    throw "Plugin verifier report directory not found: $ReportRoot"
}

# Each Rider build gets its own RD-* directory. Under plugins/, the raw reports live at plugins/<plugin-id>/<version>/.
$reportDirectories = Get-ChildItem -Path $ReportRoot -Directory | Sort-Object Name
if ($reportDirectories.Count -eq 0) {
    throw "No plugin verifier reports found in $ReportRoot"
}

$reportRows = foreach ($reportDirectory in $reportDirectories) {
    $pluginsRoot = Join-Path $reportDirectory.FullName "plugins"
    $pluginDirectory = Get-ChildItem -Path $pluginsRoot -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name |
        ForEach-Object {
            Get-ChildItem -Path $_.FullName -Directory -ErrorAction SilentlyContinue
        } |
        Sort-Object FullName |
        Select-Object -First 1

    if (-not $pluginDirectory) {
        continue
    }

    $telemetryFile = Join-Path $pluginDirectory.FullName "telemetry.txt"
    $verdictFile = Join-Path $pluginDirectory.FullName "verification-verdict.txt"
    $compatibilityFile = Join-Path $pluginDirectory.FullName "compatibility-problems.txt"

    if (-not (Test-Path -Path $telemetryFile -PathType Leaf) -or -not (Test-Path -Path $verdictFile -PathType Leaf)) {
        continue
    }

    $verdictText = (Get-Content -Path $verdictFile -TotalCount 1).Trim()
    $resultMarker = if ($verdictText -eq "Compatible") { "✅" } else { "❌" }

    [pscustomobject]@{
        RiderVersion      = $reportDirectory.Name -replace '^RD-', ''
        ResultMarker      = $resultMarker
        VerdictSummary    = $verdictText
        VerificationTime  = Get-MetricValue -FilePath $telemetryFile -Label "Verification time"
        TelemetryFile     = $telemetryFile
        CompatibilityFile = $compatibilityFile
    }
}

if (-not $reportRows) {
    throw "No raw plugin verifier text reports found in $ReportRoot"
}

function New-SummaryContent {
    & {
        Write-Output "## Plugin Verifier Summary"
        Write-Output ""
        Write-Output "| Rider version | Result | Verdict | Verification time |"
        Write-Output "| --- | --- | --- | --- |"

        foreach ($row in $reportRows) {
            Write-Output ("| {0} | {1} | {2} | {3} |" -f
                (Escape-TableCell $row.RiderVersion),
                (Escape-TableCell $row.ResultMarker),
                (Escape-TableCell $row.VerdictSummary),
                (Escape-TableCell $row.VerificationTime))
        }

        Write-Output ""
        Write-Output "## Verification details"
        Write-Output ""

        foreach ($row in $reportRows) {
            Write-Output "<details>"
            Write-Output ("<summary><strong>Rider {0}</strong> ({1} {2})</summary>" -f
                (Escape-TableCell $row.RiderVersion),
                (Escape-TableCell $row.ResultMarker),
                (Escape-TableCell $row.VerdictSummary))
            Write-Output ""

            Write-Output ("**Result:** {0}" -f (Escape-TableCell ("{0} {1}" -f $row.ResultMarker, $row.VerdictSummary)))
            Write-Output ""

            Write-Output "<details>"
            Write-Output "<summary>Telemetry</summary>"
            Write-Output ""
            Write-CodeBlockFromFile -FilePath $row.TelemetryFile
            Write-Output ""
            Write-Output "</details>"
            Write-Output ""

            Write-Output "<details>"
            Write-Output "<summary>Compatibility problems</summary>"
            Write-Output ""
            if ((Test-Path -Path $row.CompatibilityFile -PathType Leaf) -and (Get-Item -Path $row.CompatibilityFile).Length -gt 0) {
                Write-CodeBlockFromFile -FilePath $row.CompatibilityFile
            }
            else {
                Write-Output "No compatibility problems reported."
            }
            Write-Output ""
            Write-Output "</details>"
            Write-Output ""

            Write-Output "</details>"
            Write-Output ""
        }
    }
}

$summaryDirectory = Split-Path -Path $SummaryFile -Parent
if (-not (Test-Path -Path $summaryDirectory -PathType Container)) {
    New-Item -Path $summaryDirectory -ItemType Directory -Force | Out-Null
}

$summaryContent = New-SummaryContent
$summaryContent | Set-Content -Path $SummaryFile -Encoding utf8