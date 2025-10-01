# Auto-Scaling Feature Documentation

## Overview

The load balancer now includes **intelligent auto-scaling** that automatically adjusts the number of backend servers based on incoming request load.

## How It Works

The auto-scaler monitors request rates every 10 seconds (configurable) and makes scaling decisions:

### Scale UP ⬆️

-   **When**: Request rate exceeds threshold (default: 50 req/s)
-   **Action**: Adds 1-3 servers automatically
-   **Limit**: Won't exceed maximum server count (default: 20)

### Scale DOWN ⬇️

-   **When**: Request rate falls below threshold (default: 10 req/s)
-   **Action**: Removes 1-2 servers automatically
-   **Limit**: Won't go below minimum server count (default: 1)

## Configuration

Edit `config.properties` to customize auto-scaling behavior:

```properties
# Auto-scaling configuration
autoscaling.enabled=true                    # Enable/disable auto-scaling
autoscaling.min.servers=1                   # Minimum servers to maintain
autoscaling.max.servers=20                  # Maximum servers allowed
autoscaling.check.interval=10               # Check interval in seconds
autoscaling.scale.up.threshold=50           # Scale up when req/s > this
autoscaling.scale.down.threshold=10         # Scale down when req/s < this
```

## Example Scenarios

### Scenario 1: Traffic Spike

```
Initial: 3 servers, 20 req/s
↓
Traffic increases to 100 req/s
↓
Auto-scaler detects: "100 req/s > 50 req/s threshold"
↓
Action: Adds 2 servers (3 → 5)
↓
Result: Load distributed across 5 servers (20 req/s each)
```

### Scenario 2: Traffic Drop

```
Current: 5 servers, 60 req/s
↓
Traffic decreases to 8 req/s
↓
Auto-scaler detects: "8 req/s < 10 req/s threshold"
↓
Action: Removes 2 servers (5 → 3)
↓
Result: 3 servers handling 8 req/s efficiently
```

## Monitoring

Watch the logs to see auto-scaling in action:

```
📊 Load Monitor: 55.2 req/s (552 requests in 10s) | 3 servers | 18 req/s per server
📈 AUTO-SCALE UP: High load detected! Adding 2 server(s)...
INFO: ✓ Server server-8084 started on port 8084
INFO: ✓ Added node server-8084 to the ring with 150 virtual nodes. Total nodes: 4
INFO: ✓ Server server-8085 started on port 8085
INFO: ✓ Added node server-8085 to the ring with 150 virtual nodes. Total nodes: 5
INFO: ✓ Scaled up to 5 servers
```

## Testing Auto-Scaling

### 1. Start with default config (3 servers)

```bash
./run.sh
```

### 2. Generate high load to trigger scale-up

```bash
# Send 1000 requests quickly
./load-test.sh
```

You'll see logs like:

```
📊 Load Monitor: 95.3 req/s (953 requests in 10s) | 3 servers | 31 req/s per server
📈 AUTO-SCALE UP: High load detected! Adding 2 server(s)...
✓ Scaled up to 5 servers
```

### 3. Wait for load to drop (scale-down)

After load test completes and traffic drops:

```
📊 Load Monitor: 2.1 req/s (21 requests in 10s) | 5 servers | 0 req/s per server
📉 AUTO-SCALE DOWN: Low load detected. Removing 2 server(s)...
✓ Scaled down to 3 servers
```

## Manual Scaling (Override Auto-Scaling)

You can still manually scale even with auto-scaling enabled:

```bash
# Scale to exactly 10 servers
curl "http://localhost:8080/scale?target=10"

# Add 3 more servers
curl "http://localhost:8080/scale-up?count=3"

# Remove 2 servers
curl "http://localhost:8080/scale-down?count=2"
```

## Best Practices

1. **Set realistic thresholds**:

    - Too low scale-up threshold → wasteful over-provisioning
    - Too high scale-down threshold → servers stay idle

2. **Monitor costs**:

    - Each server consumes resources
    - Set appropriate max_servers limit

3. **Consider latency**:

    - New servers take ~2 seconds to start
    - Factor this into your scaling strategy

4. **Test with real traffic patterns**:
    - Test during peak hours
    - Test gradual vs sudden spikes

## Disabling Auto-Scaling

To disable and use manual scaling only:

```properties
autoscaling.enabled=false
```

Then restart the load balancer.

## Advantages

✅ **Elastic**: Automatically handles traffic variations
✅ **Cost-effective**: Scales down during low traffic
✅ **Resilient**: Adds capacity before overload
✅ **Hands-off**: No manual intervention needed

## Architecture

```
┌─────────────────────────────────────┐
│      Load Balancer Process          │
│                                     │
│  ┌──────────────────────────────┐  │
│  │  Auto-Scale Scheduler        │  │
│  │  (runs every 10s)            │  │
│  └──────────┬───────────────────┘  │
│             │                       │
│             ├─ Monitor request rate │
│             ├─ Calculate: req/s     │
│             ├─ Compare to thresholds│
│             └─ Scale up/down        │
│                                     │
│  Consistent Hash Ring               │
│  ┌─┐ ┌─┐ ┌─┐ ┌─┐ ... ┌─┐          │
│  └─┘ └─┘ └─┘ └─┘     └─┘          │
└─────────────────────────────────────┘
           │
           ├──────┬──────┬──────┬─────
           ▼      ▼      ▼      ▼
        Server Server Server Server
        (dynamically added/removed)
```

## Metrics Logged

Every check interval, you'll see:

-   **Request rate**: req/s
-   **Total requests**: in the interval
-   **Server count**: current number
-   **Req/s per server**: distribution

Example:

```
📊 Load Monitor: 42.5 req/s (425 requests in 10s) | 4 servers | 10 req/s per server
```

---

**Pro Tip**: Start with conservative thresholds and adjust based on observed behavior!
