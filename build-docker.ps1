# Build the Docker container named 'cloud'
Write-Host "Building Docker container 'cloud'..." -ForegroundColor Cyan

docker build -t cloud .

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Docker container 'cloud' built successfully!" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to build Docker container" -ForegroundColor Red
    exit 1
}
