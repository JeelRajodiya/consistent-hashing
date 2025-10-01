# Quick Start Guide

## Build and Run

1. **Build the project:**

    ```bash
    ./gradlew clean build
    ```

2. **Start the load balancer:**

    ```bash
    ./run.sh
    ```

    Or manually:

    ```bash
    java -cp app/build/classes/java/main org.example.App config.properties
    ```

3. **In another terminal, test it:**
    ```bash
    ./test.sh
    ```

## Manual Testing

### Send a request:

```bash
curl http://localhost:8080/
```

### View statistics:

```bash
curl http://localhost:8080/stats
```

### Add a server:

```bash
curl http://localhost:8080/add-server
```

### Remove a server:

```bash
curl "http://localhost:8080/remove-server?id=server-8084"
```

### Test consistent hashing (same path goes to same server):

```bash
# These should all go to the same server
curl http://localhost:8080/api/user/123
curl http://localhost:8080/api/user/123
curl http://localhost:8080/api/user/123
```

## Expected Output

When you start the load balancer, you should see:

```
========================================
Starting Consistent Hash Load Balancer
========================================
INFO: Starting 3 initial servers...
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
```

## Troubleshooting

### Port already in use

If you see "Address already in use", kill the process using the port:

```bash
# Find the process
lsof -i :8080

# Kill it
kill -9 <PID>
```

### Build fails

Make sure you have Java 21+ installed:

```bash
java -version
```

### Servers not starting

Check that ports 8081-8083 are available.

## Architecture Overview

```
Client Request
      ↓
[Load Balancer - Port 8080]
      ↓
[Consistent Hash Ring]
      ↓ (routes based on hash of client IP + path)
      ↓
[Backend Servers: 8081, 8082, 8083, ...]
```

The load balancer uses consistent hashing to ensure:

-   Same requests go to the same server (session affinity)
-   When servers are added/removed, minimal keys are redistributed
-   Even distribution across all servers

Enjoy! 🚀
