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

- Same requests go to the same server (session affinity)
- When servers are added/removed, minimal keys are redistributed
- Even distribution across all servers
