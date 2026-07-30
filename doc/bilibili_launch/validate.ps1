[CmdletBinding()]
param(
    [ValidateSet("Draft", "Release")]
    [string]$Mode = "Draft"
)

$ErrorActionPreference = "Stop"
$launchRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $launchRoot "../..")).Path
$errors = [System.Collections.Generic.List[string]]::new()
$notices = [System.Collections.Generic.List[string]]::new()

function Add-ValidationError([string]$Message) {
    $errors.Add($Message)
}

function Read-Utf8([string]$Path) {
    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8
}

function Convert-SrtTimestampToMilliseconds([string]$Value) {
    $match = [regex]::Match($Value, '^(?<hours>\d{2}):(?<minutes>\d{2}):(?<seconds>\d{2}),(?<milliseconds>\d{3})$')
    if (-not $match.Success) {
        throw "Malformed SRT timestamp: $Value"
    }

    return (
        ([int]$match.Groups['hours'].Value * 3600000) +
        ([int]$match.Groups['minutes'].Value * 60000) +
        ([int]$match.Groups['seconds'].Value * 1000) +
        [int]$match.Groups['milliseconds'].Value
    )
}

function Convert-MediaTimestampToSeconds([string]$Value) {
    $match = [regex]::Match($Value, '^(?<minutes>\d{2}):(?<seconds>\d{2})$')
    if (-not $match.Success) {
        throw "Malformed media timestamp: $Value"
    }

    return (([int]$match.Groups['minutes'].Value * 60) + [int]$match.Groups['seconds'].Value)
}

$publishableFiles = @(
    "narration.txt",
    "subtitles.srt",
    "publish-copy.md",
    "cover/cover-template.html"
)

$forbiddenClaims = @(
    "神器",
    "首创",
    "吊打",
    "永久免费",
    "精准\s*AI",
    "无需权限",
    "完全离线",
    "开机自启"
)

$expectedBrand = "炫羲单词"
$brandPlaceholderPattern = '\[App 新名称\]'
$placeholderPattern = '\[(版本号|版本代码|固定下载地址|64 位 SHA-256 校验值|签名候选 APK[^\]]*|Git 提交号|YYYY-MM-DD)\]'
$legacyBrands = @(
    "Memorize Words"
)

foreach ($relativePath in $publishableFiles) {
    $fullPath = Join-Path $launchRoot $relativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        Add-ValidationError "Missing publishable file: $relativePath"
        continue
    }

    $content = Read-Utf8 $fullPath
    if (-not $content.Contains($expectedBrand)) {
        Add-ValidationError "Expected brand '$expectedBrand' missing from $relativePath"
    }
    if ($content -match $brandPlaceholderPattern) {
        Add-ValidationError "Brand placeholder found in $relativePath"
    }
    foreach ($legacyBrand in $legacyBrands) {
        if ($content.Contains($legacyBrand)) {
            Add-ValidationError "Legacy brand found in $relativePath"
        }
    }

    foreach ($claim in $forbiddenClaims) {
        if ($content -match $claim) {
            Add-ValidationError "Forbidden claim '$claim' found in $relativePath"
        }
    }

    $matches = [regex]::Matches($content, $placeholderPattern)
    if ($matches.Count -gt 0) {
        if ($Mode -eq "Release") {
            Add-ValidationError "$relativePath still contains $($matches.Count) release placeholder(s)"
        } else {
            $notices.Add("$relativePath contains $($matches.Count) expected draft placeholder(s)")
        }
    }
}

$srtPath = Join-Path $launchRoot "subtitles.srt"
if (Test-Path -LiteralPath $srtPath -PathType Leaf) {
    $srt = Read-Utf8 $srtPath
    $blocks = $srt -split "(?:\r?\n){2,}"
    $cueCount = 0
    $expectedCue = 1
    $previousEndMs = $null
    foreach ($block in $blocks) {
        $lines = @($block -split "\r?\n" | Where-Object { $_ -ne "" })
        if ($lines.Count -eq 0) { continue }
        if ($lines[0] -notmatch '^\d+$') {
            Add-ValidationError "Malformed SRT block starting with '$($lines[0])'"
            continue
        }
        $cueCount++
        $cueNumber = [int]$lines[0]
        if ($cueNumber -ne $expectedCue) {
            Add-ValidationError "Expected SRT cue $expectedCue, found $cueNumber"
        }
        $expectedCue++

        $timestampMatch = if ($lines.Count -ge 3) {
            [regex]::Match($lines[1], '^(?<start>\d{2}:\d{2}:\d{2},\d{3}) --> (?<end>\d{2}:\d{2}:\d{2},\d{3})$')
        } else {
            $null
        }
        if ($null -eq $timestampMatch -or -not $timestampMatch.Success) {
            Add-ValidationError "Malformed timestamp in SRT cue $($lines[0])"
        } else {
            $startMs = Convert-SrtTimestampToMilliseconds $timestampMatch.Groups['start'].Value
            $endMs = Convert-SrtTimestampToMilliseconds $timestampMatch.Groups['end'].Value
            if ($endMs -le $startMs) {
                Add-ValidationError "SRT cue $cueNumber does not have a positive duration"
            }
            if ($cueNumber -eq 1 -and $startMs -ne 0) {
                Add-ValidationError "First SRT cue must start at 00:00:00,000"
            }
            if ($null -ne $previousEndMs -and $startMs -ne $previousEndMs) {
                Add-ValidationError "SRT cue $cueNumber is not contiguous with the previous cue"
            }
            $previousEndMs = $endMs
        }
        if (($lines.Count - 2) -gt 2) {
            Add-ValidationError "SRT cue $($lines[0]) contains more than two subtitle lines"
        }
    }
    if ($cueCount -eq 0) {
        Add-ValidationError "SRT contains no cues"
    } elseif ($previousEndMs -ne 85000) {
        Add-ValidationError "Final SRT cue does not end at 00:01:25,000"
    }
}

$manifestPath = Join-Path $launchRoot "media-manifest.csv"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    Add-ValidationError "Missing media-manifest.csv"
} else {
    $manifest = @(Import-Csv -LiteralPath $manifestPath -Encoding UTF8)
    $duration = ($manifest | Measure-Object -Property duration_sec -Sum).Sum
    if ([int]$duration -ne 85) {
        Add-ValidationError "Media manifest totals $duration seconds instead of 85"
    }
    $duplicates = $manifest | Group-Object filename | Where-Object Count -gt 1
    if ($duplicates) {
        Add-ValidationError "Media manifest contains duplicate filenames"
    }

    $expectedStartSeconds = 0
    foreach ($item in $manifest) {
        $startSeconds = Convert-MediaTimestampToSeconds $item.start
        $endSeconds = Convert-MediaTimestampToSeconds $item.end
        if ($startSeconds -ne $expectedStartSeconds) {
            Add-ValidationError "Media item $($item.id) does not start where the previous item ended"
        }
        if ($endSeconds -le $startSeconds) {
            Add-ValidationError "Media item $($item.id) does not have a positive duration"
        }
        if (($endSeconds - $startSeconds) -ne [int]$item.duration_sec) {
            Add-ValidationError "Media item $($item.id) duration does not match its timestamps"
        }
        $expectedStartSeconds = $endSeconds
    }
    if ($expectedStartSeconds -ne 85) {
        Add-ValidationError "Final media item does not end at 01:25"
    }

    if ($Mode -eq "Release") {
        foreach ($item in $manifest | Where-Object required -eq "yes") {
            $mediaPath = Join-Path $launchRoot (Join-Path "media/raw" $item.filename)
            if (-not (Test-Path -LiteralPath $mediaPath -PathType Leaf)) {
                Add-ValidationError "Missing required recording: media/raw/$($item.filename)"
            }
        }
    } else {
        $notices.Add("Raw phone recordings are checked only in Release mode")
    }
}

if ($Mode -eq "Release") {
    $appStringsPath = Join-Path $repoRoot "app/src/main/res/values/strings.xml"
    if (-not (Test-Path -LiteralPath $appStringsPath -PathType Leaf)) {
        Add-ValidationError "Cannot find app strings.xml"
    } else {
        $appStrings = Read-Utf8 $appStringsPath
        if (-not $appStrings.Contains($expectedBrand)) {
            Add-ValidationError "App display name does not use the expected brand '$expectedBrand'"
        }
        foreach ($legacyBrand in $legacyBrands) {
            if ($appStrings.Contains($legacyBrand)) {
                Add-ValidationError "App display name still uses the legacy brand"
            }
        }
        if ($appStrings -match $brandPlaceholderPattern) {
            Add-ValidationError "App display name still uses the brand placeholder"
        }
    }

    $coverPath = Join-Path $launchRoot "cover/cover-final.png"
    if (-not (Test-Path -LiteralPath $coverPath -PathType Leaf)) {
        Add-ValidationError "Missing cover/cover-final.png"
    } else {
        try {
            Add-Type -AssemblyName System.Drawing
            $cover = [System.Drawing.Image]::FromFile($coverPath)
            if ($cover.Width -ne 1920 -or $cover.Height -ne 1080) {
                Add-ValidationError "Final cover must be 1920 x 1080; found $($cover.Width) x $($cover.Height)"
            }
            $cover.Dispose()
        } catch {
            Add-ValidationError "Could not inspect final cover: $($_.Exception.Message)"
        }
    }

    $screenshotPath = Join-Path $launchRoot "cover/cross-app-screenshot.png"
    if (-not (Test-Path -LiteralPath $screenshotPath -PathType Leaf)) {
        Add-ValidationError "Missing real cross-App screenshot for the cover"
    }

    $releaseInfoPath = Join-Path $launchRoot "release-info.txt"
    if (-not (Test-Path -LiteralPath $releaseInfoPath -PathType Leaf)) {
        Add-ValidationError "Missing release-info.txt; copy release-info.template.txt and fill it"
    } else {
        $releaseInfoText = Read-Utf8 $releaseInfoPath
        if ($releaseInfoText -match '\[[^\]]+\]') {
            Add-ValidationError "release-info.txt still contains bracketed placeholders"
        }

        $info = @{}
        foreach ($line in $releaseInfoText -split "\r?\n") {
            if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#")) { continue }
            $parts = $line -split "=", 2
            if ($parts.Count -eq 2) {
                $info[$parts[0].Trim()] = $parts[1].Trim()
            }
        }

        $requiredKeys = @("APP_NAME", "VERSION_NAME", "VERSION_CODE", "APK_PATH", "SHA256", "DOWNLOAD_URL", "BUILD_COMMIT", "BUILD_DATE")
        foreach ($key in $requiredKeys) {
            if (-not $info.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($info[$key])) {
                Add-ValidationError "release-info.txt is missing $key"
            }
        }

        if ($info.ContainsKey("SHA256") -and $info["SHA256"] -notmatch '^[0-9a-fA-F]{64}$') {
            Add-ValidationError "SHA256 must contain exactly 64 hexadecimal characters"
        }

        if ($info.ContainsKey("APK_PATH") -and -not [string]::IsNullOrWhiteSpace($info["APK_PATH"])) {
            $apkPath = $info["APK_PATH"]
            if (-not [System.IO.Path]::IsPathRooted($apkPath)) {
                $apkPath = Join-Path $repoRoot $apkPath
            }
            if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
                Add-ValidationError "APK_PATH does not point to a file"
            } elseif ($info["SHA256"] -match '^[0-9a-fA-F]{64}$') {
                $actualHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash
                if ($actualHash -ne $info["SHA256"].ToUpperInvariant()) {
                    Add-ValidationError "APK SHA-256 does not match release-info.txt"
                }
            }
        }
    }
}

foreach ($notice in $notices) {
    Write-Host "NOTICE: $notice" -ForegroundColor Yellow
}

if ($errors.Count -gt 0) {
    foreach ($validationError in $errors) {
        Write-Host "ERROR: $validationError" -ForegroundColor Red
    }
    Write-Host "FAIL ($Mode): $($errors.Count) issue(s)" -ForegroundColor Red
    exit 1
}

Write-Host "PASS ($Mode): launch pack validation succeeded" -ForegroundColor Green

