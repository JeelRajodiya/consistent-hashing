# Auto-Scaling Feature Documentation

## 1. Overview

The load balancer features an intelligent auto-scaling mechanism that dynamically adjusts the number of backend servers in response to request load. This ensures optimal resource utilization and high availability.

## 2. Auto-Scaling Logic

The auto-scaler monitors the request rate at a configurable interval and performs scaling actions based on predefined thresholds.

### Scale-Up

- **Trigger**: The request rate exceeds the `autoscaling.scale.up.threshold`.
- **Action**: The system automatically provisions and adds new server instances to the consistent hashing ring.
- **Limit**: The total number of servers will not exceed `autoscaling.max.servers`.

### Scale-Down

- **Trigger**: The request rate falls below the `autoscaling.scale.down.threshold`.
- **Action**: The system automatically de-provisions surplus server instances.
- **Limit**: The total number of servers will not fall below `autoscaling.min.servers`.

## 3. Configuration

Auto-scaling behavior is configured in the `config.properties` file.

```properties
# Auto-scaling configuration
autoscaling.enabled=true                    # Enable or disable the auto-scaling feature
autoscaling.min.servers=1                   # Minimum number of active servers
autoscaling.max.servers=20                  # Maximum number of active servers
autoscaling.check.interval=10               # Monitoring interval in seconds
autoscaling.scale.up.threshold=50           # Request rate (req/s) to trigger a scale-up
autoscaling.scale.down.threshold=10         # Request rate (req/s) to trigger a scale-down
```

## 4. Example Scenarios

### Scenario A: Traffic Spike (Scale-Up)

1.  **Initial State**: 3 servers are handling 20 req/s.
2.  **Event**: Traffic increases to 100 req/s.
3.  **Detection**: The auto-scaler detects the rate (100 req/s) is above the scale-up threshold (50 req/s).
4.  **Action**: Two new servers are added, bringing the total to 5.
5.  **Result**: The load is distributed across 5 servers, with each handling approximately 20 req/s.

### Scenario B: Traffic Lull (Scale-Down)

1.  **Initial State**: 5 servers are handling 60 req/s.
2.  **Event**: Traffic decreases to 8 req/s.
3.  **Detection**: The auto-scaler detects the rate (8 req/s) is below the scale-down threshold (10 req/s).
4.  **Action**: Two servers are removed, bringing the total to 3.
5.  **Result**: The remaining 3 servers efficiently handle the reduced load.

## 5. Monitoring

Monitor the application logs to observe auto-scaling events in real-time.

**Scale-Up Log Example:**

```
INFO: Load Monitor: 55.2 req/s (552 requests in 10s) | 3 servers | 18 req/s per server
INFO: AUTO-SCALE UP: High load detected. Adding 2 server(s)...
INFO: Server server-8084 started on port 8084
INFO: Added node server-8084 to the ring with 150 virtual nodes. Total nodes: 4
INFO: Server server-8085 started on port 8085
INFO: Added node server-8085 to the ring with 150 virtual nodes. Total nodes: 5
INFO: Scaled up to 5 servers
```

**Scale-Down Log Example:**

```
INFO: Load Monitor: 2.1 req/s (21 requests in 10s) | 5 servers | 0 req/s per server
INFO: AUTO-SCALE DOWN: Low load detected. Removing 2 server(s)...
INFO: Scaled down to 3 servers
```

## 6. Testing

To test the auto-scaling functionality:

1.  **Start the service** with the default configuration (3 servers).
    ```bash
    ./run.sh
    ```
2.  **Generate a high load** to trigger a scale-up event.
    ```bash
    ./load-test.sh
    ```
3.  **Observe the logs** for scale-up messages. Once the test completes, the load will decrease, and after a short period, you will observe scale-down events.

## 7. Manual Override

Manual scaling commands can be issued even when auto-scaling is enabled. These commands provide immediate control over the server pool.

- **Set a specific number of servers:**
  ```bash
  curl "http://localhost:8080/scale?target=10"
  ```
- **Add a specific number of servers:**
  ```bash
  curl "http://localhost:8080/scale-up?count=3"
  ```
- **Remove a specific number of servers:**
  ```bash
  curl "http://localhost:8080/scale-down?count=2"
  ```

## 8. Best Practices

- **Tune Thresholds**: Set realistic scale-up and scale-down thresholds based on typical traffic patterns to avoid over-provisioning or under-utilization.
- **Monitor Costs**: Be mindful that each server instance incurs resource costs. Set an appropriate `autoscaling.max.servers` limit to manage your budget.
- **Account for Latency**: New server instances require a brief startup period. Factor this latency into your capacity planning.
- **Test with Realistic Patterns**: Validate your configuration by testing against traffic patterns that mimic real-world usage, including both sudden spikes and gradual changes.

## 9. Disabling Auto-Scaling

To disable this feature and rely solely on manual scaling, set the following property in `config.properties` and restart the service:

```properties
autoscaling.enabled=false
```

## 10. Key Benefits

- **Elasticity**: Automatically adapts to traffic variations.
- **Cost-Effectiveness**: Reduces resource consumption during low-traffic periods.
- **Resilience**: Proactively adds capacity to prevent system overloads.
- **Automation**: Requires no manual intervention for routine load management.

## 11. System Architecture

The auto-scaling system is managed by a scheduler within the main load balancer process. It periodically assesses the request rate and interacts with the consistent hash ring to add or remove server nodes.

```
┌─────────────────────────────────────┐
│      Load Balancer Process          │
│                                     │
│  ┌──────────────────────────────┐  │
│  │  Auto-Scale Scheduler        │  │
│  │  (runs every `check.interval`) │  │
│  └──────────┬───────────────────┘  │
│             │                       │
│             ├─ Monitor request rate │
│             ├─ Calculate req/s      │
│             ├─ Compare to thresholds│
│             └─ Scale up or down     │
│                                     │
│  Consistent Hash Ring               │
│  ┌─┐ ┌─┐ ┌─┐ ┌─┐ ... ┌─┐          │
│  └─┘ └─┘ └─┘ └─┘     └─┘          │
└─────────────────────────────────────┘
           │
           ├──────┬──────┬──────┬─────
           ▼      ▼      ▼      ▼
        Server Server Server Server
        (Dynamically added or removed)
```
