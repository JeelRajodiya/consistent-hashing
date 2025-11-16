#!/bin/bash

# Build the Docker container named 'cloud'
echo "Building Docker container 'cloud'..."
docker build -t cloud .

if [ $? -eq 0 ]; then
    echo "✓ Docker container 'cloud' built successfully!"
else
    echo "✗ Failed to build Docker container"
    exit 1
fi
