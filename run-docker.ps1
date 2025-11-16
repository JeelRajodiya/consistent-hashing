# Run the Docker container named 'cloud'
Write-Host "Running Docker container 'cloud'..." -ForegroundColor Cyan

# Check if container is already running
$runningContainer = docker ps -q -f name=cloud
if ($runningContainer) {
    Write-Host "Container 'cloud' is already running" -ForegroundColor Yellow
    Write-Host "Stopping existing container..." -ForegroundColor Yellow
    docker stop cloud
    docker rm cloud
}

# Remove container if it exists but is stopped
$stoppedContainer = docker ps -aq -f name=cloud
if ($stoppedContainer) {
    Write-Host "Removing stopped container 'cloud'..." -ForegroundColor Yellow
    docker rm cloud
}

# Run the container
docker run -d `
    --name cloud `
    -p 8080:8080 `
    -p 3000:3000 `
    cloud

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Docker container 'cloud' is now running!" -ForegroundColor Green
    Write-Host "  - Load Balancer: http://localhost:8080" -ForegroundColor Cyan
    Write-Host "  - UI: http://localhost:3000" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "To view logs: docker logs -f cloud" -ForegroundColor Gray
} else {
    Write-Host "✗ Failed to run Docker container" -ForegroundColor Red
    exit 1
}
