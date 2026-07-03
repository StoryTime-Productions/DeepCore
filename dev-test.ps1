param(
    [switch]$NoStart,
    [switch]$Continual,
    [int]$RestartDelaySeconds = 2,
    [switch]$SyncConfig,
    [string]$RepoRoot,
    [string]$TestServerDir,
    [switch]$ExposeResourcePackPublicly,
    [string]$ResourcePackHostOverride
)

$ErrorActionPreference = "Stop"

# Defaults keep repository and test server separate from each other.
if (-not $RepoRoot -or [string]::IsNullOrWhiteSpace($RepoRoot)) {
    $nestedRepo = Join-Path $PSScriptRoot "DeepCore"
    $currentRepo = $PSScriptRoot
    $legacyRepo = Join-Path $PSScriptRoot "yart"

    if (Test-Path (Join-Path $nestedRepo "gradlew.bat")) {
        $RepoRoot = $nestedRepo
    }
    elseif (Test-Path (Join-Path $currentRepo "gradlew.bat")) {
        $RepoRoot = $currentRepo
    }
    elseif (Test-Path (Join-Path $legacyRepo "gradlew.bat")) {
        $RepoRoot = $legacyRepo
    }
    else {
        throw "Could not auto-detect repo root with gradlew.bat. Pass -RepoRoot explicitly."
    }
}
if (-not $TestServerDir -or [string]::IsNullOrWhiteSpace($TestServerDir)) {
    $TestServerDir = Join-Path $PSScriptRoot "minecraft-test"
}

$repoRoot = $RepoRoot
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$libsDir = Join-Path $repoRoot "build\libs"
$sourceConfig = Join-Path $repoRoot "src\main\resources\config.yml"
$stweaksPackSource = Join-Path $PSScriptRoot "stweaks-resourcepack"
$testServerDir = $TestServerDir
$pluginsDir = Join-Path $testServerDir "plugins"
$pluginDataDir = Join-Path $pluginsDir "DeepCore"
$pluginConfig = Join-Path $pluginDataDir "config.yml"
$resourcePackHostDir = Join-Path $testServerDir "resource-pack-host"
$resourcePackStageDir = Join-Path $resourcePackHostDir "stweaks-active"
$resourcePackZip = Join-Path $resourcePackHostDir "storytime-stweaks.zip"
$resourcePackPort = 8123
$serverPropertiesPath = Join-Path $testServerDir "server.properties"
$startScript = Join-Path $testServerDir "start.bat"
$startScriptPs1 = Join-Path $testServerDir "start.ps1"

if (-not (Test-Path $gradleWrapper)) {
    throw "Could not find gradlew.bat at: $gradleWrapper. Pass -RepoRoot if your repo is elsewhere."
}

if (-not (Test-Path $testServerDir)) {
    throw "Could not find test server folder at: $testServerDir. Pass -TestServerDir if needed."
}

if (-not (Test-Path $stweaksPackSource)) {
    throw "Could not find local workspace resource-pack source at: $stweaksPackSource"
}

if (-not (Test-Path $serverPropertiesPath)) {
    throw "Could not find server.properties at: $serverPropertiesPath"
}

if ($NoStart -and $Continual) {
    throw "-NoStart and -Continual cannot be used together."
}

if ($RestartDelaySeconds -lt 0) {
    throw "RestartDelaySeconds must be >= 0."
}

if (-not (Test-Path $pluginsDir)) {
    New-Item -ItemType Directory -Path $pluginsDir | Out-Null
}

Write-Host "[1/3] Building plugin with Gradle wrapper..." -ForegroundColor Cyan
Push-Location $repoRoot
try {
    & $gradleWrapper assemble
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

Write-Host "[2/3] Copying latest plugin jar to test server..." -ForegroundColor Cyan
$builtJars = Get-ChildItem -Path $libsDir -Filter "*.jar" |
    Where-Object { $_.Name -notmatch "-sources\.jar$|-javadoc\.jar$" } |
    Sort-Object LastWriteTime -Descending

if (-not $builtJars -or $builtJars.Count -eq 0) {
    throw "No built plugin jar found in: $libsDir"
}

$latestJar = $builtJars[0]

@("DeepCore*.jar", "deepcore*.jar", "YetAnotherRayTracer*.jar", "minecraft-raytracing*.jar") | ForEach-Object {
    Get-ChildItem -Path $pluginsDir -Filter $_ -ErrorAction SilentlyContinue |
        Remove-Item -Force -ErrorAction SilentlyContinue
}

$destinationJar = Join-Path $pluginsDir $latestJar.Name
Copy-Item -Path $latestJar.FullName -Destination $destinationJar -Force

Write-Host "Copied $($latestJar.Name) to $pluginsDir" -ForegroundColor Green

Write-Host "[2.5/3] Handling plugin config..." -ForegroundColor Cyan
if (-not (Test-Path $sourceConfig)) {
    throw "Could not find source config at: $sourceConfig"
}

if (-not (Test-Path $pluginDataDir)) {
    New-Item -ItemType Directory -Path $pluginDataDir | Out-Null
}

if (-not (Test-Path $pluginConfig)) {
    Copy-Item -Path $sourceConfig -Destination $pluginConfig
    Write-Host "Created plugin config from source at $pluginConfig" -ForegroundColor Green
}
elseif ($SyncConfig) {
    Copy-Item -Path $sourceConfig -Destination $pluginConfig -Force
    Write-Host "Synced config.yml to $pluginConfig (-SyncConfig)." -ForegroundColor Yellow
}
else {
    Write-Host "Preserving existing plugin config at $pluginConfig (use -SyncConfig to overwrite)." -ForegroundColor Green
}

Write-Host "[2.75/3] Syncing and hosting workspace resource pack..." -ForegroundColor Cyan

if (Test-Path $resourcePackStageDir) {
    Remove-Item -Path $resourcePackStageDir -Recurse -Force
}
New-Item -ItemType Directory -Path $resourcePackStageDir -Force | Out-Null
Copy-Item -Path (Join-Path $stweaksPackSource "*") -Destination $resourcePackStageDir -Recurse -Force

$jackpotOggPath = Join-Path $resourcePackStageDir "assets\storytime\sounds\disco\jackpot.ogg"
if (-not (Test-Path $jackpotOggPath)) {
    throw "Missing staged jackpot OGG at: $jackpotOggPath. Add it to stweaks-resourcepack once, then rerun dev-test.ps1."
}

$soundsJsonPath = Join-Path $resourcePackStageDir "assets\storytime\sounds.json"
if (-not (Test-Path $soundsJsonPath)) {
    throw "Missing storytime sounds.json at: $soundsJsonPath. Define storytime.disco.jackpot in stweaks-resourcepack once, then rerun dev-test.ps1."
}

$soundsJson = Get-Content -Path $soundsJsonPath -Raw | ConvertFrom-Json -AsHashtable
if (-not ($soundsJson.ContainsKey("disco.jackpot") -or $soundsJson.ContainsKey("storytime.disco.jackpot"))) {
    throw "Missing sound key 'disco.jackpot' (or legacy 'storytime.disco.jackpot') in $soundsJsonPath. Add it once in stweaks-resourcepack, then rerun dev-test.ps1."
}

if (Test-Path $resourcePackZip) {
    Remove-Item -Path $resourcePackZip -Force
}
Compress-Archive -Path (Join-Path $resourcePackStageDir "*") -DestinationPath $resourcePackZip -CompressionLevel Optimal

$packHash = (Get-FileHash -Path $resourcePackZip -Algorithm SHA1).Hash.ToLowerInvariant()

try {
    $listeners = Get-NetTCPConnection -LocalPort $resourcePackPort -State Listen -ErrorAction Stop
    foreach ($listener in $listeners) {
        try {
            Stop-Process -Id $listener.OwningProcess -Force -ErrorAction Stop
        }
        catch {
        }
    }
}
catch {
}

if ($ExposeResourcePackPublicly) {
    $firewallRuleName = "DeepCore Resource Pack Host ($resourcePackPort)"
    $existingRule = Get-NetFirewallRule -DisplayName $firewallRuleName -ErrorAction SilentlyContinue
    if (-not $existingRule) {
        try {
            New-NetFirewallRule -DisplayName $firewallRuleName -Direction Inbound -Action Allow -Protocol TCP -LocalPort $resourcePackPort -Profile Any | Out-Null
            Write-Host "Created firewall rule '$firewallRuleName'." -ForegroundColor Green
        }
        catch {
            Write-Host "Could not create firewall rule '$firewallRuleName'. Run PowerShell as Administrator if external clients cannot connect." -ForegroundColor Yellow
        }
    }
}

Start-Process -FilePath "python" -ArgumentList @("-m", "http.server", "$resourcePackPort", "--bind", "0.0.0.0", "--directory", $resourcePackHostDir) -WindowStyle Hidden

$hostIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.IPAddress -notlike "169.254.*" -and
        $_.IPAddress -ne "127.0.0.1" -and
        $_.PrefixOrigin -ne "WellKnown"
    } |
    Select-Object -First 1 -ExpandProperty IPAddress)

if (-not $hostIp) {
    $hostIp = "127.0.0.1"
}

$resourcePackHost = $hostIp

if ($ResourcePackHostOverride -and -not [string]::IsNullOrWhiteSpace($ResourcePackHostOverride)) {
    $resourcePackHost = $ResourcePackHostOverride.Trim()
}
elseif ($ExposeResourcePackPublicly) {
    try {
        $publicIp = (Invoke-RestMethod -Uri "https://api.ipify.org?format=text" -TimeoutSec 3 -ErrorAction Stop).ToString().Trim()
        if ($publicIp) {
            $resourcePackHost = $publicIp
        }
    }
    catch {
        Write-Host "Could not auto-detect public IP; using local IP $hostIp. Use -ResourcePackHostOverride <host> if needed." -ForegroundColor Yellow
    }
}

$resourcePackUrl = "http://$resourcePackHost`:$resourcePackPort/storytime-stweaks.zip"

$serverPropsLines = Get-Content -Path $serverPropertiesPath
function Set-ServerPropertyLine {
    param(
        [string[]]$Lines,
        [string]$Key,
        [string]$Value
    )

    $prefix = "$Key="
    $updated = $false
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i].StartsWith($prefix)) {
            $Lines[$i] = "$prefix$Value"
            $updated = $true
            break
        }
    }

    if (-not $updated) {
        $Lines += "$prefix$Value"
    }
    return ,$Lines
}

$serverPropsLines = Set-ServerPropertyLine -Lines $serverPropsLines -Key "resource-pack" -Value $resourcePackUrl
$serverPropsLines = Set-ServerPropertyLine -Lines $serverPropsLines -Key "resource-pack-sha1" -Value $packHash
$serverPropsLines = Set-ServerPropertyLine -Lines $serverPropsLines -Key "require-resource-pack" -Value "true"
$resourcePackPromptJson = '{"text":"StoryTime Productions resource pack is required for disco audio."}'
$serverPropsLines = Set-ServerPropertyLine -Lines $serverPropsLines -Key "resource-pack-prompt" -Value $resourcePackPromptJson

Set-Content -Path $serverPropertiesPath -Value $serverPropsLines -Encoding UTF8
Write-Host "Resource pack hosted at $resourcePackUrl" -ForegroundColor Green
if ($ExposeResourcePackPublicly) {
    Write-Host "Public hosting mode is enabled. Ensure your router/NAT forwards TCP $resourcePackPort to this machine's LAN IP ($hostIp)." -ForegroundColor Yellow
}

if ($NoStart) {
    Write-Host "[3/3] Skipped starting test server (-NoStart)." -ForegroundColor Yellow
    exit 0
}

Write-Host "[3/3] Starting test server..." -ForegroundColor Cyan
if ($Continual) {
    if (-not (Test-Path $startScriptPs1)) {
        throw "Could not find start script for continual mode: $startScriptPs1"
    }

    Write-Host "Continual mode enabled. Press Ctrl+C in this tracking terminal to stop automatic restarts." -ForegroundColor Yellow
    $runNumber = 1
    while ($true) {
        Write-Host "[run #$runNumber] Launching server terminal..." -ForegroundColor Cyan
        $serverProcess = Start-Process -FilePath "pwsh" -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-File", $startScriptPs1) -WorkingDirectory $testServerDir -PassThru
        $serverProcess.WaitForExit()

        $exitCode = $serverProcess.ExitCode
        Write-Host "[run #$runNumber] Server terminal exited with code $exitCode." -ForegroundColor Yellow
        $runNumber++

        if ($RestartDelaySeconds -gt 0) {
            Write-Host "Restarting in $RestartDelaySeconds second(s)..." -ForegroundColor DarkGray
            Start-Sleep -Seconds $RestartDelaySeconds
        }
    }
}
else {
    if (-not (Test-Path $startScript)) {
        throw "Could not find start script: $startScript"
    }

    Start-Process -FilePath $startScript -WorkingDirectory $testServerDir
    Write-Host "Test server started." -ForegroundColor Green
}
