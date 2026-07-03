param(
    [string]$MinecraftVersion,
    [string]$WorldType,
    [Alias("Name")]
    [string]$WorldName,
    [string]$Seed,
    [Nullable[bool]]$GenerateStructures,
    [switch]$Interactive,
    [switch]$Force,
    [int]$MaxRetries = 3,
    [int]$GenerationTimeoutSeconds = 180,
    [string]$OutputRoot,
    [string]$WorldOutputPath,
    [string]$CacheRoot,
    [switch]$KeepTemporaryFiles,
    [string]$JavaExecutable = "java"
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not [string]::IsNullOrWhiteSpace($WorldOutputPath)) {
    $OutputRoot = $WorldOutputPath
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $scriptRoot "outputs"
}
if ([string]::IsNullOrWhiteSpace($CacheRoot)) {
    $CacheRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("deepcore-worldgen-" + [guid]::NewGuid().ToString("N"))
}

if ($MaxRetries -lt 1) {
    throw "MaxRetries must be >= 1."
}
if ($GenerationTimeoutSeconds -lt 30) {
    throw "GenerationTimeoutSeconds must be >= 30."
}

$worldTypeOptions = @(
    [pscustomobject]@{ Key = "normal"; Display = "Normal"; LevelType = "minecraft:normal"; GeneratorSettings = "{}" },
    [pscustomobject]@{ Key = "large_biomes"; Display = "Large Biomes"; LevelType = "minecraft:large_biomes"; GeneratorSettings = "{}" },
    [pscustomobject]@{ Key = "amplified"; Display = "Amplified"; LevelType = "minecraft:amplified"; GeneratorSettings = "{}" },
    [pscustomobject]@{ Key = "single_biome"; Display = "Single Biome Surface"; LevelType = "minecraft:single_biome_surface"; GeneratorSettings = "{}" },
    [pscustomobject]@{ Key = "flat"; Display = "Superflat"; LevelType = "minecraft:flat"; GeneratorSettings = "{}" },
    [pscustomobject]@{ Key = "void"; Display = "Void (no barrier)"; LevelType = "minecraft:flat"; GeneratorSettings = '{"biome":"minecraft:the_void","layers":[{"height":1,"block":"minecraft:air"}],"structures":{"structures":{}}}' }
)

function Invoke-WithRetry {
    param(
        [scriptblock]$Operation,
        [string]$FailurePrefix,
        [int]$Retries
    )

    $lastError = $null
    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        try {
            return (& $Operation)
        }
        catch {
            $lastError = $_
            Write-Host "$FailurePrefix (attempt $attempt/$Retries): $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }

    throw "${FailurePrefix}: $($lastError.Exception.Message)"
}

function Get-MojangVersionManifest {
    param([int]$Retries)

    $manifestUrl = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
    return Invoke-WithRetry -Retries $Retries -FailurePrefix "Failed to fetch Mojang version manifest" -Operation {
        Invoke-RestMethod -Method Get -Uri $manifestUrl -TimeoutSec 20
    }
}

function Get-MojangReleaseVersionList {
    param(
        [object]$Manifest,
        [int]$MaxResults = 25
    )

    if ($null -eq $Manifest -or $null -eq $Manifest.versions) {
        throw "Mojang manifest is missing versions list."
    }

    $results = New-Object System.Collections.Generic.List[object]
    $releaseCandidates = $Manifest.versions | Where-Object { $_.type -eq "release" } | Select-Object -First $MaxResults
    foreach ($candidate in $releaseCandidates) {
        $results.Add([pscustomobject]@{
            Version = $candidate.id
            MetadataUrl = $candidate.url
        }) | Out-Null
    }

    if ($results.Count -eq 0) {
        throw "No release versions were found in Mojang manifest."
    }

    return $results.ToArray()
}

function Resolve-ServerDownloadForVersion {
    param(
        [object]$VersionSelection,
        [int]$Retries
    )

    $metadata = Invoke-WithRetry -Retries $Retries -FailurePrefix "Failed to fetch metadata for version $($VersionSelection.Version)" -Operation {
        Invoke-RestMethod -Method Get -Uri $VersionSelection.MetadataUrl -TimeoutSec 20
    }

    if ($null -eq $metadata.downloads -or $null -eq $metadata.downloads.server) {
        throw "Selected version '$($VersionSelection.Version)' does not expose a Mojang server download."
    }

    return [pscustomobject]@{
        Version = $VersionSelection.Version
        ServerUrl = $metadata.downloads.server.url
        ServerSha1 = $metadata.downloads.server.sha1
    }
}

function Ensure-MojangVanillaJar {
    param(
        [object]$VersionSelection,
        [string]$CacheBase,
        [int]$Retries
    )

    $versionCacheDir = Join-Path $CacheBase $VersionSelection.Version
    if (-not (Test-Path -LiteralPath $versionCacheDir)) {
        New-Item -ItemType Directory -Path $versionCacheDir -Force | Out-Null
    }

    $jarPath = Join-Path $versionCacheDir ("vanilla-server-" + $VersionSelection.Version + ".jar")
    $sha1Path = Join-Path $versionCacheDir ("vanilla-server-" + $VersionSelection.Version + ".sha1")

    if (Test-Path -LiteralPath $jarPath) {
        if (-not [string]::IsNullOrWhiteSpace($VersionSelection.ServerSha1)) {
            $localSha1 = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA1).Hash.ToLowerInvariant()
            if ($localSha1 -eq $VersionSelection.ServerSha1.ToLowerInvariant()) {
                return $jarPath
            }
            Remove-Item -LiteralPath $jarPath -Force
            if (Test-Path -LiteralPath $sha1Path) {
                Remove-Item -LiteralPath $sha1Path -Force
            }
        }
        else {
            return $jarPath
        }
    }

    Invoke-WithRetry -Retries $Retries -FailurePrefix "Failed downloading vanilla server jar" -Operation {
        Invoke-WebRequest -Uri $VersionSelection.ServerUrl -OutFile $jarPath -TimeoutSec 120 | Out-Null
        return $null
    } | Out-Null

    if (-not [string]::IsNullOrWhiteSpace($VersionSelection.ServerSha1)) {
        $downloadSha1 = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA1).Hash.ToLowerInvariant()
        if ($downloadSha1 -ne $VersionSelection.ServerSha1.ToLowerInvariant()) {
            Remove-Item -LiteralPath $jarPath -Force
            throw "Downloaded jar hash mismatch for version $($VersionSelection.Version)."
        }
        Set-Content -LiteralPath $sha1Path -Value $VersionSelection.ServerSha1 -Encoding ASCII
    }

    return $jarPath
}

function Read-ValidatedInput {
    param(
        [string]$Prompt,
        [scriptblock]$Validator,
        [string]$FailureMessage,
        [int]$Retries
    )

    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        $value = Read-Host $Prompt
        if (& $Validator $value) {
            return $value
        }
        Write-Host "$FailureMessage (attempt $attempt/$Retries)" -ForegroundColor Yellow
    }

    throw "Input validation failed after $Retries attempts for prompt: $Prompt"
}

function Resolve-VersionSelection {
    param(
        [string]$Provided,
        [array]$Available,
        [switch]$UseInteractive,
        [int]$Retries
    )

    if ([string]::IsNullOrWhiteSpace($Provided)) {
        if (-not $UseInteractive) {
            throw "MinecraftVersion is required in flag mode."
        }

        Write-Host "Available Minecraft versions:" -ForegroundColor Cyan
        for ($i = 0; $i -lt $Available.Count; $i++) {
            Write-Host "[$($i + 1)] $($Available[$i].Version)"
        }

        $selectedText = Read-ValidatedInput -Prompt "Choose version by number or exact value" `
            -Validator {
                param($candidate)
                if ([string]::IsNullOrWhiteSpace($candidate)) {
                    return $false
                }
                if ($candidate -match '^\d+$') {
                    $idx = [int]$candidate
                    return $idx -ge 1 -and $idx -le $Available.Count
                }
                return $Available.Version -contains $candidate
            } `
            -FailureMessage "Invalid version selection" -Retries $Retries

        if ($selectedText -match '^\d+$') {
            return $Available[[int]$selectedText - 1]
        }
        return ($Available | Where-Object { $_.Version -eq $selectedText } | Select-Object -First 1)
    }

    $selected = $Available | Where-Object { $_.Version -eq $Provided } | Select-Object -First 1
    if ($null -eq $selected) {
        throw "Minecraft version '$Provided' is not available from Mojang release manifest."
    }
    return $selected
}

function Resolve-WorldTypeSelection {
    param(
        [string]$Provided,
        [array]$TypeOptions,
        [switch]$UseInteractive,
        [int]$Retries
    )

    if ([string]::IsNullOrWhiteSpace($Provided)) {
        if (-not $UseInteractive) {
            throw "WorldType is required in flag mode."
        }

        Write-Host "World type options:" -ForegroundColor Cyan
        for ($i = 0; $i -lt $TypeOptions.Count; $i++) {
            Write-Host "[$($i + 1)] $($TypeOptions[$i].Display) [$($TypeOptions[$i].Key)]"
        }

        $selectedText = Read-ValidatedInput -Prompt "Choose world type by number or key" `
            -Validator {
                param($candidate)
                if ([string]::IsNullOrWhiteSpace($candidate)) {
                    return $false
                }
                if ($candidate -match '^\d+$') {
                    $idx = [int]$candidate
                    return $idx -ge 1 -and $idx -le $TypeOptions.Count
                }
                return $TypeOptions.Key -contains $candidate.ToLowerInvariant()
            } `
            -FailureMessage "Invalid world type selection" -Retries $Retries

        if ($selectedText -match '^\d+$') {
            return $TypeOptions[[int]$selectedText - 1]
        }
        return ($TypeOptions | Where-Object { $_.Key -eq $selectedText.ToLowerInvariant() } | Select-Object -First 1)
    }

    $normalized = $Provided.ToLowerInvariant()
    $selected = $TypeOptions | Where-Object { $_.Key -eq $normalized } | Select-Object -First 1
    if ($null -eq $selected) {
        throw "World type '$Provided' is invalid. Supported: $($TypeOptions.Key -join ', ')."
    }
    return $selected
}

function Resolve-WorldName {
    param(
        [string]$Provided,
        [switch]$UseInteractive,
        [int]$Retries
    )

    $validator = {
        param($candidate)
        return -not [string]::IsNullOrWhiteSpace($candidate) -and ($candidate -match '^[A-Za-z0-9._-]+$')
    }

    if (-not [string]::IsNullOrWhiteSpace($Provided)) {
        if (-not (& $validator $Provided)) {
            throw "WorldName '$Provided' is invalid. Use [A-Za-z0-9._-]."
        }
        return $Provided
    }

    if (-not $UseInteractive) {
        throw "WorldName is required in flag mode."
    }

    return Read-ValidatedInput -Prompt "World folder name (letters/numbers/._-)" -Validator $validator `
        -FailureMessage "Invalid world name" -Retries $Retries
}

function Resolve-GenerateStructures {
    param(
        [Nullable[bool]]$Provided,
        [switch]$UseInteractive,
        [int]$Retries
    )

    if ($null -ne $Provided) {
        return [bool]$Provided
    }

    if (-not $UseInteractive) {
        return $true
    }

    $answer = Read-ValidatedInput -Prompt "Generate structures? (y/n)" `
        -Validator {
            param($candidate)
            if ([string]::IsNullOrWhiteSpace($candidate)) {
                return $false
            }
            $normalized = $candidate.Trim().ToLowerInvariant()
            return @("y", "yes", "n", "no") -contains $normalized
        } `
        -FailureMessage "Please answer y/yes or n/no" -Retries $Retries

    return @("y", "yes") -contains $answer.Trim().ToLowerInvariant()
}

function Resolve-Seed {
    param(
        [string]$Provided,
        [switch]$UseInteractive,
        [int]$Retries
    )

    if (-not [string]::IsNullOrWhiteSpace($Provided)) {
        return $Provided.Trim()
    }

    if (-not $UseInteractive) {
        return ""
    }

    $raw = Read-ValidatedInput -Prompt "Seed (optional; press Enter for random)" `
        -Validator {
            param($candidate)
            if ($null -eq $candidate) {
                return $true
            }
            return $candidate.Length -le 128
        } `
        -FailureMessage "Seed must be at most 128 characters" -Retries $Retries

    return ($raw ?? "").Trim()
}

function Request-OverwriteConsent {
    param(
        [string[]]$Paths,
        [switch]$ForceOverwrite,
        [switch]$UseInteractive,
        [int]$Retries
    )

    $existing = @($Paths | Where-Object { Test-Path -LiteralPath $_ })
    if ($existing.Count -eq 0) {
        return
    }

    if ($ForceOverwrite) {
        foreach ($path in $existing) {
            Remove-Item -LiteralPath $path -Recurse -Force
        }
        return
    }

    if (-not $UseInteractive) {
        throw "One or more target world folders already exist. Use -Force to overwrite."
    }

    Write-Host "Existing world folders detected:" -ForegroundColor Yellow
    foreach ($path in $existing) {
        Write-Host "- $path"
    }

    $consent = Read-ValidatedInput -Prompt "Overwrite these folders? (y/n)" `
        -Validator {
            param($candidate)
            if ([string]::IsNullOrWhiteSpace($candidate)) {
                return $false
            }
            $normalized = $candidate.Trim().ToLowerInvariant()
            return @("y", "yes", "n", "no") -contains $normalized
        } `
        -FailureMessage "Please answer y/yes or n/no" -Retries $Retries

    if (@("y", "yes") -contains $consent.Trim().ToLowerInvariant()) {
        foreach ($path in $existing) {
            Remove-Item -LiteralPath $path -Recurse -Force
        }
        return
    }

    throw "Generation cancelled by user."
}

function Get-RuntimeWorkspace {
    param(
        [string]$CacheBase,
        [string]$Version
    )

    $runtimeRoot = Join-Path $CacheBase "runtime"
    if (-not (Test-Path -LiteralPath $runtimeRoot)) {
        New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
    }

    $workspace = Join-Path $runtimeRoot $Version
    if (-not (Test-Path -LiteralPath $workspace)) {
        New-Item -ItemType Directory -Path $workspace -Force | Out-Null
    }

    return $workspace
}

function Clear-ExistingWorldFolders {
    param(
        [string]$Workspace,
        [string]$WorldNameValue
    )

    $folders = @($WorldNameValue, "$WorldNameValue`_nether", "$WorldNameValue`_the_end")
    foreach ($folder in $folders) {
        $path = Join-Path $Workspace $folder
        if (Test-Path -LiteralPath $path) {
            Remove-Item -LiteralPath $path -Recurse -Force
        }
    }
}

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Write-ServerFiles {
    param(
        [string]$Workspace,
        [string]$WorldNameValue,
        [string]$LevelType,
        [string]$GeneratorSettings,
        [string]$SeedValue,
        [bool]$WithStructures,
        [int]$ServerPort
    )

    Set-Content -LiteralPath (Join-Path $Workspace "eula.txt") -Value "eula=true" -Encoding UTF8

    $properties = @(
        "motd=Standalone World Generator",
        "online-mode=false",
        "server-ip=127.0.0.1",
        "server-port=$ServerPort",
        "query.port=$ServerPort",
        "enable-rcon=false",
        "enable-query=false",
        "view-distance=6",
        "simulation-distance=4",
        "spawn-protection=0",
        "max-tick-time=60000",
        "level-name=$WorldNameValue",
        "level-type=$LevelType",
        "generator-settings=$GeneratorSettings",
        "level-seed=$SeedValue",
        "generate-structures=$($WithStructures.ToString().ToLowerInvariant())"
    )

    Set-Content -LiteralPath (Join-Path $Workspace "server.properties") -Value $properties -Encoding UTF8
}

function Invoke-WorldGeneration {
    param(
        [string]$JavaPath,
        [string]$JarPath,
        [string]$Workspace,
        [string]$WorldNameValue,
        [int]$TimeoutSeconds
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $JavaPath
    $psi.Arguments = "-Xms512M -Xmx1G -jar `"$JarPath`" nogui"
    $psi.WorkingDirectory = $Workspace
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $false
    $psi.RedirectStandardError = $false
    $psi.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi

    try {
        if (-not $process.Start()) {
            throw "Failed to start Java process for world generation."
        }

        $worldPath = Join-Path $Workspace $WorldNameValue
        $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
        $generated = $false

        while ((Get-Date) -lt $deadline) {
            if (Test-Path -LiteralPath (Join-Path $worldPath "level.dat")) {
                $generated = $true
                break
            }

            Start-Sleep -Milliseconds 500

            if ($process.HasExited) {
                break
            }
        }

        if ($generated) {
            if (-not $process.HasExited) {
                $process.StandardInput.WriteLine("stop")
                $process.StandardInput.Flush()
            }

            if (-not $process.WaitForExit(30000)) {
                $process.Kill($true)
            }
        }
        elseif (-not $process.HasExited) {
            $process.Kill($true)
        }

        if (-not $generated) {
            throw "World generation did not complete before timeout. Check '$Workspace\\logs\\latest.log' for details."
        }
    }
    finally {
        try { $process.Dispose() } catch {}
    }
}

function Move-GeneratedWorldFolders {
    param(
        [string]$Workspace,
        [string]$DestinationRoot,
        [string]$WorldNameValue
    )

    $folders = @($WorldNameValue, "$WorldNameValue`_nether", "$WorldNameValue`_the_end")
    $moved = @()
    foreach ($folder in $folders) {
        $source = Join-Path $Workspace $folder
        if (-not (Test-Path -LiteralPath $source)) {
            continue
        }

        $dest = Join-Path $DestinationRoot $folder
        if (Test-Path -LiteralPath $dest) {
            Remove-Item -LiteralPath $dest -Recurse -Force
        }
        Move-Item -LiteralPath $source -Destination $dest -Force
        $moved += $dest
    }

    if ($moved.Count -eq 0) {
        throw "Generation finished but no world folders were found at '$Workspace'."
    }

    return ,$moved
}

function Cleanup-RuntimeArtifacts {
    param(
        [string]$Workspace,
        [string]$WorldNameValue
    )

    $pathsToRemove = @(
        "eula.txt",
        "server.properties",
        "banned-ips.json",
        "banned-players.json",
        "ops.json",
        "usercache.json",
        "whitelist.json",
        "logs",
        "crash-reports",
        $WorldNameValue,
        "$WorldNameValue`_nether",
        "$WorldNameValue`_the_end"
    )

    foreach ($relativePath in $pathsToRemove) {
        $fullPath = Join-Path $Workspace $relativePath
        if (Test-Path -LiteralPath $fullPath) {
            Remove-Item -LiteralPath $fullPath -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

$manifest = Get-MojangVersionManifest -Retries $MaxRetries
$availableVersions = Get-MojangReleaseVersionList -Manifest $manifest -MaxResults 25

$interactiveMode = $Interactive -or [string]::IsNullOrWhiteSpace($MinecraftVersion) -or [string]::IsNullOrWhiteSpace($WorldType) -or [string]::IsNullOrWhiteSpace($WorldName)

$versionSelection = Resolve-VersionSelection -Provided $MinecraftVersion -Available $availableVersions -UseInteractive:$interactiveMode -Retries $MaxRetries
$typeSelection = Resolve-WorldTypeSelection -Provided $WorldType -TypeOptions $worldTypeOptions -UseInteractive:$interactiveMode -Retries $MaxRetries
$resolvedWorldName = Resolve-WorldName -Provided $WorldName -UseInteractive:$interactiveMode -Retries $MaxRetries
$resolvedSeed = Resolve-Seed -Provided $Seed -UseInteractive:$interactiveMode -Retries $MaxRetries
$resolvedStructures = Resolve-GenerateStructures -Provided $GenerateStructures -UseInteractive:$interactiveMode -Retries $MaxRetries

$outputWorldPath = Join-Path $OutputRoot $resolvedWorldName
$outputNetherPath = Join-Path $OutputRoot ("$resolvedWorldName`_nether")
$outputEndPath = Join-Path $OutputRoot ("$resolvedWorldName`_the_end")

Request-OverwriteConsent -Paths @($outputWorldPath, $outputNetherPath, $outputEndPath) -ForceOverwrite:$Force -UseInteractive:$interactiveMode -Retries $MaxRetries

if (-not (Test-Path -LiteralPath $CacheRoot)) {
    New-Item -ItemType Directory -Path $CacheRoot -Force | Out-Null
}
if (-not (Test-Path -LiteralPath $OutputRoot)) {
    New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
}

$versionDownload = Resolve-ServerDownloadForVersion -VersionSelection $versionSelection -Retries $MaxRetries
$jarPath = Ensure-MojangVanillaJar -VersionSelection $versionDownload -CacheBase $CacheRoot -Retries $MaxRetries

Write-Host "Selected version: $($versionSelection.Version)" -ForegroundColor Cyan
Write-Host "World type: $($typeSelection.Display) [$($typeSelection.Key)]" -ForegroundColor Cyan
Write-Host "World name: $resolvedWorldName" -ForegroundColor Cyan
Write-Host "Generate structures: $resolvedStructures" -ForegroundColor Cyan
if ([string]::IsNullOrWhiteSpace($resolvedSeed)) {
    Write-Host "Seed: random" -ForegroundColor Cyan
}
else {
    Write-Host "Seed: $resolvedSeed" -ForegroundColor Cyan
}
Write-Host "Vanilla jar: $jarPath" -ForegroundColor Cyan

$runtimePort = Get-FreeTcpPort
$workspace = Get-RuntimeWorkspace -CacheBase $CacheRoot -Version $versionSelection.Version
Clear-ExistingWorldFolders -Workspace $workspace -WorldNameValue $resolvedWorldName

Write-ServerFiles -Workspace $workspace -WorldNameValue $resolvedWorldName -LevelType $typeSelection.LevelType `
    -GeneratorSettings $typeSelection.GeneratorSettings -SeedValue $resolvedSeed -WithStructures $resolvedStructures -ServerPort $runtimePort

Write-Host "Generating world folders..." -ForegroundColor Green
Write-Host "Note: first run for a version may take longer due to Mojang runtime unpack." -ForegroundColor DarkYellow
Write-Host "Using temporary local server port: $runtimePort" -ForegroundColor DarkGray
Invoke-WorldGeneration -JavaPath $JavaExecutable -JarPath $jarPath -Workspace $workspace `
    -WorldNameValue $resolvedWorldName -TimeoutSeconds $GenerationTimeoutSeconds

$movedWorldFolders = Move-GeneratedWorldFolders -Workspace $workspace -DestinationRoot $OutputRoot -WorldNameValue $resolvedWorldName
Cleanup-RuntimeArtifacts -Workspace $workspace -WorldNameValue $resolvedWorldName

if (-not $KeepTemporaryFiles) {
    Remove-Item -LiteralPath $CacheRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "World generation completed:" -ForegroundColor Green
foreach ($path in $movedWorldFolders) {
    Write-Host "- $path"
}
