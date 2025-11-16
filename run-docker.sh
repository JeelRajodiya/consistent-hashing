#!/bin/bash

# Run the Docker container named 'cloud'
echo "Running Docker container 'cloud'..."

# Check if container is already running
if [ "$(docker ps -q -f name=cloud)" ]; then
    echo "Container 'cloud' is already running"
    echo "Stopping existing container..."
    docker stop cloud
    docker rm cloud
fi

# Remove container if it exists but is stopped
if [ "$(docker ps -aq -f name=cloud)" ]; then
    echo "Removing stopped container 'cloud'..."
    docker rm cloud
fi

# Run the container
docker run -d \
    --name cloud \
    -p 8080:8080 \
    -p 3000:3000 \
    -p 8081:8081 \
    
    cloud

if [ $? -eq 0 ]; then
    echo "✓ Docker container 'cloud' is now running!"
    echo "  - Load Balancer: http://localhost:8080"
    echo "  - UI: http://localhost:3000"
    echo ""
    echo "To view logs: docker logs -f cloud"
else
    echo "✗ Failed to run Docker container"
    exit 1
fi
