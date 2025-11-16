# Open bash in the running Docker container named 'cloud'
Write-Host "Opening bash in container 'cloud'..." -ForegroundColor Cyan

# Check if container is running
$runningContainer = docker ps -q -f name=cloud
if (-not $runningContainer) {
    Write-Host "✗ Container 'cloud' is not running" -ForegroundColor Red
    Write-Host "Please start the container first using .\run-docker.ps1" -ForegroundColor Yellow
    exit 1
}

# Execute bash in the container
docker exec -it cloud /bin/bash
