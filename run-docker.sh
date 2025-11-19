#!/bin/bash

# Runs the Docker container for this project. The image name can be overridden
# by setting the IMAGE_NAME environment variable before running this script.
IMAGE_NAME="${IMAGE_NAME:-cloud}"
CONTAINER_NAME="cloud"

echo "Running Docker container '$CONTAINER_NAME' using image '$IMAGE_NAME'..."

# Stop and remove existing running container with same name
if [ "$(docker ps -q -f name=^/${CONTAINER_NAME}$)" ]; then
    echo "Container '$CONTAINER_NAME' is already running"
    echo "Stopping existing container..."
    docker stop "$CONTAINER_NAME"
    docker rm "$CONTAINER_NAME"
fi

# Remove container if it exists but is stopped
if [ "$(docker ps -aq -f name=^/${CONTAINER_NAME}$)" ]; then
    echo "Removing stopped container '$CONTAINER_NAME'..."
    docker rm "$CONTAINER_NAME"
fi

# Ensure the image exists locally; if not, try to build it from the current directory
if [ -z "$(docker images -q "$IMAGE_NAME" 2>/dev/null)" ]; then
    echo "Image '$IMAGE_NAME' not found locally. Attempting to build from current directory..."
    if ! docker build -t "$IMAGE_NAME" .; then
        echo "✗ Failed to build image '$IMAGE_NAME'"
        exit 1
    fi
fi

# Run the container (single command to avoid continuation-line issues)
if docker run -d --name "$CONTAINER_NAME" -p 8080:8080 -p 3000:3000 -p 8081:8081 "$IMAGE_NAME"; then
    echo "✓ Docker container '$CONTAINER_NAME' is now running!"
    echo "  - Load Balancer: http://localhost:8080"
    echo "  - UI: http://localhost:3000"
    echo ""
    echo "To view logs: docker logs -f $CONTAINER_NAME"
else
    echo "✗ Failed to run Docker container"
    exit 1
fi
