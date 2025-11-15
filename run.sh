#!/bin/bash

# Consistent Hashing Load Balancer - Run Script

echo "=========================================="
echo "  Consistent Hashing Load Balancer"
echo "=========================================="
echo ""

# Check if build is needed
if [ ! -d "app/build/classes/java/main" ]; then
    echo "Building the project..."
    ./gradlew clean build
    if [ $? -ne 0 ]; then
        echo "Build failed!"
        exit 1
    fi
    echo "Build successful!"
    echo ""
fi

# Run the application
echo "Starting Load Balancer..."
echo ""

# Build classpath with all dependencies
./gradlew :app:run --args="config.properties" --console=plain
