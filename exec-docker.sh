#!/bin/bash

# Open bash in the running Docker container named 'cloud'
echo "Opening bash in container 'cloud'..."

# Check if container is running
if [ ! "$(docker ps -q -f name=cloud)" ]; then
    echo "✗ Container 'cloud' is not running"
    echo "Please start the container first using ./run-docker.sh"
    exit 1
fi

# Execute bash in the container
docker exec -it cloud /bin/bash
