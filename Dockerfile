# Stage 1: Build the UI (Nuxt.js)
FROM node:20-slim AS ui-builder

# Install pnpm
RUN npm install -g pnpm

WORKDIR /app/ui

# Copy UI project files and install dependencies
COPY ui/package.json ui/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile --shamefully-hoist

# Copy the rest of the UI code and build
COPY ui/ ./
RUN pnpm run build

# Stage 2: Build the Java Application
FROM gradle:8.5.0-jdk21 AS java-builder

WORKDIR /app

# Copy only Gradle files first for better caching
COPY settings.gradle.kts ./
COPY gradle.properties ./
COPY app/build.gradle.kts ./app/
COPY gradle/ ./gradle/

# Download dependencies (this layer will be cached)
RUN gradle dependencies --no-daemon || true

# Now copy the rest of the source code
COPY . .

# Build the Java application and create the distribution
RUN ./gradlew :app:installDist --no-daemon

# Stage 3: Final Image
FROM eclipse-temurin:21-jre-jammy

# Install Node.js for the Nuxt UI server
RUN apt-get update && \
    apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Java application from the builder stage
COPY --from=java-builder /app/app/build/install/app ./java-app

# Copy UI build from the ui-builder stage (full .output with server)
COPY --from=ui-builder /app/ui/.output ./ui

# Copy configuration and scripts
COPY config.properties ./config.properties.local
COPY config.docker.properties ./config.properties
COPY run.sh ./
COPY load-test.sh ./
RUN chmod +x run.sh load-test.sh

# Create a startup script to run both services
RUN echo '#!/bin/bash\n\
echo "Starting Nuxt UI server on port 3000..."\n\
cd /app/ui && PORT=3000 node server/index.mjs &\n\
echo "Starting Java Load Balancer on port 8080..."\n\
cd /app && java-app/bin/app config.properties\n\
' > /app/start.sh && chmod +x /app/start.sh

# Expose the load balancer port and UI port
EXPOSE 8080 3000

# Run both the Java app and Nuxt server
CMD ["/app/start.sh"]
