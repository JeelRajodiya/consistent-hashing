# Consistent Hashing Load Balancer

A production-ready load balancer implementation using **Consistent Hashing (Ring Hash)** algorithm in Java. This project demonstrates distributed systems concepts with a modular, easy-to-debug architecture.

## 🎯 Features

-   ✅ **Consistent Hashing** with virtual nodes for uniform distribution
-   ✅ **Dynamic Server Management** - Add/Remove servers at runtime
-   ✅ **Health Checks** - Automatic monitoring of backend servers
-   ✅ **REST API** - Simple HTTP interface for load balancing
-   ✅ **Detailed Logging** - Easy debugging with comprehensive logs
-   ✅ **Configuration File** - Externalized configuration
-   ✅ **Modular Architecture** - Clean separation of concerns

## 🏗️ Architecture

### Components

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

### Modules

-   **`App.java`** - Main entry point
-   **`LoadBalancer.java`** - HTTP server with consistent hashing routing
-   **`ConsistentHashRing.java`** - Ring hash implementation with virtual nodes
-   **`ServerManager.java`** - Manages backend server lifecycle
-   **`SimpleServer.java`** - Backend HTTP server
-   **`Node.java`** - Represents a server node
-   **`ServerConfig.java`** - Configuration reader

## 🚀 Getting Started

### Prerequisites

-   Java 21 or higher
-   Gradle 9.1.0+ (included via wrapper)

### Build the Project

```bash
./gradlew clean build
```

### Configuration

Edit `config.properties` to customize:

```properties
# Command to start backend servers - {PORT} will be replaced with actual port
server.command=java -cp app/build/classes/java/main org.example.server.SimpleServer {PORT}

# Initial number of servers to start
server.initial.count=3

# Starting port number (will increment for each server)
server.starting.port=8081

# Load balancer port
loadbalancer.port=8080

# Number of virtual nodes per physical server (for better distribution)
virtual.nodes=150

# Health check interval in seconds
health.check.interval=10
```

### Run the Load Balancer

```bash
./gradlew run
```

Or run the JAR directly:

```bash
java -jar app/build/libs/app.jar
```

## 📡 API Endpoints

### 1. **Load Balanced Requests**

```bash
# Any request to the root or any path will be load balanced
curl http://localhost:8080/
curl http://localhost:8080/api/users
curl http://localhost:8080/any/path
```

**Response:**

```json
{
	"server": "Server-8081",
	"port": 8081,
	"path": "/api/users",
	"method": "GET",
	"message": "Hello from Server-8081!",
	"timestamp": 1696176000000
}
```

### 2. **View Statistics**

```bash
curl http://localhost:8080/stats
```

**Response:**

```json
{
	"loadBalancer": {
		"port": 8080,
		"totalRequests": 42,
		"virtualNodesPerServer": 150
	},
	"servers": {
		"total": 3,
		"nodes": [
			{
				"id": "server-8081",
				"address": "localhost:8081",
				"active": true
			},
			{
				"id": "server-8082",
				"address": "localhost:8082",
				"active": true
			},
			{
				"id": "server-8083",
				"address": "localhost:8083",
				"active": true
			}
		]
	}
}
```

### 3. **Add a Server**

```bash
curl http://localhost:8080/add-server
```

**Response:**

```json
{
	"status": "success",
	"message": "Server added successfully",
	"server": {
		"id": "server-8084",
		"address": "localhost:8084"
	},
	"totalServers": 4
}
```

### 4. **Remove a Server**

```bash
curl "http://localhost:8080/remove-server?id=server-8084"
```

**Response:**

```json
{
	"status": "success",
	"message": "Server removed successfully",
	"serverId": "server-8084",
	"totalServers": 3
}
```

## 🔍 How Consistent Hashing Works

1. **Hash Ring**: A circular hash space (0 to 2^32-1)
2. **Virtual Nodes**: Each physical server is mapped to multiple positions (default: 150) on the ring for better distribution
3. **Request Routing**:
    - Hash the request key (client IP + path)
    - Find the first server clockwise from the hash position
    - Route the request to that server

### Benefits

-   **Minimal Redistribution**: When adding/removing servers, only ~1/N keys need to be redistributed
-   **Load Distribution**: Virtual nodes ensure uniform distribution even with few servers
-   **Scalability**: Easy to add/remove servers without massive rebalancing

## 📊 Logging & Debugging

The application provides detailed logs:

```
========================================
Starting Consistent Hash Load Balancer
========================================
INFO: Starting 3 initial servers...
INFO: Starting server with command: java -cp app/build/classes/java/main org.example.server.SimpleServer 8081
INFO: ✓ Server server-8081 started on port 8081
INFO: ✓ Added node server-8081 to the ring with 150 virtual nodes. Total nodes: 1
INFO: ✓ Server server-8082 started on port 8082
INFO: ✓ Added node server-8082 to the ring with 150 virtual nodes. Total nodes: 2
INFO: ✓ Server server-8083 started on port 8083
INFO: ✓ Added node server-8083 to the ring with 150 virtual nodes. Total nodes: 3

=== Consistent Hash Ring Stats ===
Physical Nodes: 3
Virtual Nodes per Physical Node: 150
Total Positions in Ring: 450
Active Nodes:
  - Node{id='server-8081', address='localhost:8081', active=true}
  - Node{id='server-8082', address='localhost:8082', active=true}
  - Node{id='server-8083', address='localhost:8083', active=true}
==================================

========================================
🎯 Load Balancer started on port 8080
📊 Access stats at: http://localhost:8080/stats
➕ Add server: http://localhost:8080/add-server
➖ Remove server: http://localhost:8080/remove-server?id=<server-id>
========================================

✓ Application is running. Press Ctrl+C to stop.

INFO: Request #1 from 127.0.0.1 → server-8082 (key: 127.0.0.1/api/test)
INFO: Request #2 from 127.0.0.1 → server-8081 (key: 127.0.0.1/api/users)
```

## 🧪 Testing

### Manual Testing

1. **Test Load Balancing**:

```bash
# Send multiple requests and observe different servers handling them
for i in {1..10}; do
  curl http://localhost:8080/test$i
  echo ""
done
```

2. **Test Server Addition**:

```bash
# Add a new server
curl http://localhost:8080/add-server

# Verify it's in the stats
curl http://localhost:8080/stats
```

3. **Test Server Removal**:

```bash
# Remove a server
curl "http://localhost:8080/remove-server?id=server-8083"

# Verify it's removed
curl http://localhost:8080/stats
```

### Run Unit Tests

```bash
./gradlew test
```

## 🛠️ Development

### Project Structure

```
app/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── org/
│   │           └── example/
│   │               ├── App.java                    # Main entry point
│   │               ├── common/
│   │               │   └── Node.java               # Server node model
│   │               ├── config/
│   │               │   └── ServerConfig.java       # Configuration reader
│   │               ├── loadbalancer/
│   │               │   └── LoadBalancer.java       # Load balancer HTTP server
│   │               ├── ring/
│   │               │   └── ConsistentHashRing.java # Hash ring implementation
│   │               └── server/
│   │                   ├── ServerManager.java      # Server lifecycle manager
│   │                   └── SimpleServer.java       # Backend HTTP server
│   └── test/
│       └── java/
│           └── org/
│               └── example/
│                   └── AppTest.java                # Unit tests
├── build.gradle.kts                                 # Build configuration
└── ...
config.properties                                    # Application configuration
```

## 🎓 Learning Resources

### Consistent Hashing Concepts

-   **Hash Space**: Circular space from 0 to 2^32-1
-   **Virtual Nodes**: Multiple hash positions per physical server
-   **Clockwise Search**: Find next server in clockwise direction
-   **Minimal Disruption**: Only ~K/N keys move when N servers exist

### Key Algorithms

1. **Add Node**: Hash node ID multiple times (virtual nodes) and add to ring
2. **Remove Node**: Remove all virtual node positions from ring
3. **Get Node**: Hash key → Find ceiling entry in ring → Return server

## 🤝 Contributing

Feel free to submit issues and enhancement requests!

## 📝 License

This project is for educational purposes.

## 👨‍💻 Author

Built with ☕ and Java

---

**Happy Load Balancing! 🚀**
