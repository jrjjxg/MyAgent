#Requires -Version 5.1

[CmdletBinding()]
param(
    [string]$RocketMqHome,
    [string]$JavaHome,
    [string]$NameServer = "127.0.0.1:9876",
    [string]$StoreRoot,
    [string]$RuntimeRoot,
    [switch]$CleanStore
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-JavaHome {
    param([string]$ConfiguredJavaHome)

    if ($ConfiguredJavaHome) {
        return (Resolve-Path -LiteralPath $ConfiguredJavaHome).Path
    }

    if ($env:JAVA_HOME) {
        return (Resolve-Path -LiteralPath $env:JAVA_HOME).Path
    }

    $javaCommand = (Get-Command java -ErrorAction Stop).Source
    $javaBin = Split-Path -Parent $javaCommand
    return Split-Path -Parent $javaBin
}

function Get-RocketMqProcess {
    Get-CimInstance Win32_Process |
            Where-Object {
                $_.CommandLine -and (
                    $_.CommandLine -like "*mqnamesrv*" -or
                    $_.CommandLine -like "*mqbroker*" -or
                    $_.CommandLine -like "*NamesrvStartup*" -or
                    $_.CommandLine -like "*BrokerStartup*"
                )
            }
}

function Wait-ForPort {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 20
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
                Select-Object -First 1
        if ($listener) {
            return
        }
        Start-Sleep -Milliseconds 500
    }

    throw "Timed out waiting for port $Port to start listening."
}

function Ensure-Topic {
    param(
        [string]$MqAdminCmd,
        [string]$NameServer,
        [string]$Topic
    )

    & $MqAdminCmd updateTopic -n $NameServer -c DefaultCluster -t $Topic | Out-Null
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $RocketMqHome) {
    $RocketMqHome = Join-Path $repoRoot ".local\rocketmq\5.3.1\rocketmq-all-5.3.1-bin-release"
}
$RocketMqHome = (Resolve-Path -LiteralPath $RocketMqHome).Path

$javaHomeResolved = Resolve-JavaHome -ConfiguredJavaHome $JavaHome

if (-not $StoreRoot) {
    $StoreRoot = Join-Path $repoRoot ".local\rocketmq\store"
}
if (-not $RuntimeRoot) {
    $RuntimeRoot = Join-Path $repoRoot ".local\rocketmq\runtime"
}

$StoreRoot = [System.IO.Path]::GetFullPath($StoreRoot)
$RuntimeRoot = [System.IO.Path]::GetFullPath($RuntimeRoot)
$binDir = Join-Path $RocketMqHome "bin"
$namesrvCmd = Join-Path $binDir "mqnamesrv.cmd"
$brokerCmd = Join-Path $binDir "mqbroker.cmd"
$mqAdminCmd = Join-Path $binDir "mqadmin.cmd"

if (-not (Test-Path -LiteralPath $namesrvCmd)) {
    throw "RocketMQ namesrv launcher not found: $namesrvCmd"
}
if (-not (Test-Path -LiteralPath $brokerCmd)) {
    throw "RocketMQ broker launcher not found: $brokerCmd"
}
if (-not (Test-Path -LiteralPath $mqAdminCmd)) {
    throw "RocketMQ admin launcher not found: $mqAdminCmd"
}

$existing = @(Get-RocketMqProcess)
if ($existing.Count -gt 0) {
    $details = $existing | Select-Object ProcessId, Name, CommandLine | Format-Table -AutoSize | Out-String
    throw "RocketMQ processes are already running. Stop them first with ops\\rocketmq\\stop-local.ps1.`n$details"
}

$storeRootResolved = [System.IO.Path]::GetFullPath($StoreRoot)
$allowedRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot ".local"))
if (-not $storeRootResolved.StartsWith($allowedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "StoreRoot must stay under $allowedRoot for safe local cleanup."
}

if ($CleanStore -and (Test-Path -LiteralPath $storeRootResolved)) {
    Remove-Item -LiteralPath $storeRootResolved -Recurse -Force
}

$null = New-Item -ItemType Directory -Path $storeRootResolved -Force
$null = New-Item -ItemType Directory -Path $RuntimeRoot -Force

$brokerConfigPath = Join-Path $RuntimeRoot "broker-local.conf"
$storeRootConfig = $storeRootResolved.Replace("\", "/")
$brokerConfig = @"
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=0
deleteWhen=04
fileReservedTime=48
brokerRole=ASYNC_MASTER
flushDiskType=ASYNC_FLUSH
namesrvAddr=$NameServer
listenPort=10911
brokerIP1=127.0.0.1
autoCreateTopicEnable=true
autoCreateSubscriptionGroup=true
diskMaxUsedSpaceRatio=99
storePathRootDir=$storeRootConfig
"@

Set-Content -LiteralPath $brokerConfigPath -Value $brokerConfig -Encoding ASCII

$env:JAVA_HOME = $javaHomeResolved
$env:ROCKETMQ_HOME = $RocketMqHome
$env:NAMESRV_ADDR = $NameServer
$env:JAVA_OPT_EXT = "-server -Xms256m -Xmx256m -Xmn128m -XX:MaxDirectMemorySize=256m -XX:-AlwaysPreTouch"

$namesrvProcess = Start-Process -FilePath $namesrvCmd `
        -WorkingDirectory $binDir `
        -WindowStyle Hidden `
        -PassThru

Wait-ForPort -Port 9876

$brokerProcess = Start-Process -FilePath $brokerCmd `
        -ArgumentList "-c", $brokerConfigPath `
        -WorkingDirectory $binDir `
        -WindowStyle Hidden `
        -PassThru

Wait-ForPort -Port 10911

foreach ($topic in @(
    "platform_tasks_dispatch",
    "platform_memory_events",
    "platform_memory_long_term_jobs"
)) {
    Ensure-Topic -MqAdminCmd $mqAdminCmd -NameServer $NameServer -Topic $topic
}

Write-Host "RocketMQ local broker is up."
Write-Host "NameServer : $NameServer"
Write-Host "Broker PID : $($brokerProcess.Id)"
Write-Host "NameSrv PID: $($namesrvProcess.Id)"
Write-Host "StoreRoot  : $storeRootResolved"
Write-Host "Config     : $brokerConfigPath"
Write-Host "Topics     : platform_tasks_dispatch, platform_memory_events, platform_memory_long_term_jobs"
