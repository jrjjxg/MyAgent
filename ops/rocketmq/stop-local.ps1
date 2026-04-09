#Requires -Version 5.1

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$processes = Get-CimInstance Win32_Process |
        Where-Object {
            $_.CommandLine -and (
                $_.CommandLine -like "*mqnamesrv*" -or
                $_.CommandLine -like "*mqbroker*" -or
                $_.CommandLine -like "*NamesrvStartup*" -or
                $_.CommandLine -like "*BrokerStartup*"
            )
        }

if (-not $processes) {
    Write-Host "No RocketMQ namesrv or broker process is running."
    return
}

$processes | ForEach-Object {
    Write-Host "Stopping PID $($_.ProcessId): $($_.Name)"
    Stop-Process -Id $_.ProcessId -Force
}

Start-Sleep -Seconds 2

$remaining = Get-CimInstance Win32_Process |
        Where-Object {
            $_.CommandLine -and (
                $_.CommandLine -like "*mqnamesrv*" -or
                $_.CommandLine -like "*mqbroker*" -or
                $_.CommandLine -like "*NamesrvStartup*" -or
                $_.CommandLine -like "*BrokerStartup*"
            )
        }

if ($remaining) {
    $details = $remaining | Select-Object ProcessId, Name, CommandLine | Format-Table -AutoSize | Out-String
    throw "Some RocketMQ processes are still alive.`n$details"
}

Write-Host "RocketMQ local processes stopped."
