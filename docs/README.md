# Consistent Hashing Load Balancer

A production-ready load balancer implementation using the **Consistent Hashing (Ring Hash)** algorithm in Java.

## Features

- **Consistent Hashing**: With virtual nodes for uniform distribution.
- **Dynamic Server Management**: Add/Remove servers at runtime.
- **Health Checks**: Automatic monitoring of backend servers.
- **REST API**: Simple HTTP interface for load balancing and management.
- **Configurable**: Externalized settings in `config.properties`.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Load Balancer                          │
│  (Port 8080 - Consistent Hash Ring + HTTP Server)          │
└────────────┬────────────────────────────────────────────────┘
             │
             ├──────────────┬──────────────┬──────────────┐
             │              │              │              │
             ▼              ▼              ▼              ▼
      ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
      │ Server 1 │   │ Server 2 │   │ Server 3 │   │ Server N │
      │Port 8081 │   │Port 8082 │   │Port 8083 │   │Port 808X │
      └──────────┘   └──────────┘   └──────────┘   └──────────┘
```

## Getting Started

### Prerequisites

- Java 21+
- Gradle 9.1.0+ (included)

### 1. Build

```bash
./gradlew clean build
```

### 2. Configure

Edit `config.properties` to customize ports, server counts, and other settings.

```properties
# Command to start backend servers
server.command=java -cp app/build/classes/java/main org.example.server.SimpleServer {PORT}
# Initial number of servers
server.initial.count=3
# Starting port for backend servers
server.starting.port=8081
# Load balancer port
loadbalancer.port=8080
# Number of virtual nodes per physical server
virtual.nodes=150
# Health check interval in seconds
health.check.interval=10
```

### 3. Run

```bash
./gradlew run
```

Or run the JAR directly:

```bash
java -jar app/build/libs/app.jar
```

## API Endpoints

- **Load Balanced Requests**: `GET /any/path`
  ```bash
  curl http://localhost:8080/api/users
  ```
- **View Statistics**: `GET /stats`
  ```bash
  curl http://localhost:8080/stats
  ```
- **Add a Server**: `GET /add-server`
  ```bash
  curl http://localhost:8080/add-server
  ```
- **Remove a Server**: `GET /remove-server?id=<server-id>`
  ```bash
  curl "http://localhost:8080/remove-server?id=server-8084"
  ```

## Project Structure

```
app/
└── src/
    └── main/
        └── java/
            └── org/
                └── example/
                    ├── App.java                    # Main entry point
                    ├── common/
                    │   └── Node.java               # Server node model
                    ├── config/
                    │   └── ServerConfig.java       # Configuration reader
                    ├── loadbalancer/
                    │   └── LoadBalancer.java       # Load balancer HTTP server
                    ├── ring/
                    │   └── ConsistentHashRing.java # Hash ring implementation
                    └── server/
                        ├── ServerManager.java      # Server lifecycle manager
                        └── SimpleServer.java       # Backend HTTP server
```

## License

This project is for educational purposes.
