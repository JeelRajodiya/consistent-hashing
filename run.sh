#!/bin/bash

# Consistent Hashing Load Balancer - Run Script

echo "=========================================="
echo "  Consistent Hashing Load Balancer"
echo "=========================================="
echo ""

# Check if build is needed
if [ ! -f "app/build/libs/app.jar" ]; then
    echo "📦 Building the project..."
    ./gradlew clean build
    if [ $? -ne 0 ]; then
        echo "❌ Build failed!"
        exit 1
    fi
    echo "✅ Build successful!"
    echo ""
fi

# Run the application
echo "🚀 Starting Load Balancer..."
echo ""

java -cp app/build/classes/java/main org.example.App config.properties
