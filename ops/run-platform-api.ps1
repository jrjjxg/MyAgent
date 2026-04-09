param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$SpringArgs
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$postgresEnv = Join-Path $repoRoot ".local\postgres-env.ps1"
if (Test-Path $postgresEnv) {
    . $postgresEnv
}

Write-Host "[myagent] Installing backend reactor modules to keep local SNAPSHOTs aligned..."
& .\mvnw.cmd -q -pl backend/platform-api -am -DskipTests install

Write-Host "[myagent] Starting platform-api..."
$mavenArgs = @("-pl", "backend/platform-api", "spring-boot:run")
if ($SpringArgs.Count -gt 0) {
    $mavenArgs += "-Dspring-boot.run.arguments=$($SpringArgs -join ' ')"
}
& .\mvnw.cmd @mavenArgs
