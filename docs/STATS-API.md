# Enhanced Stats API Documentation

## Endpoint

`GET /stats`

## Response Format

The enhanced stats endpoint now provides comprehensive metrics for building a beautiful, real-time monitoring UI.

### Sample Response

```json
{
  "loadBalancer": {
    "port": 8080,
    "uptime": 3725,
    "uptimeFormatted": "1h 2m 5s",
    "virtualNodesPerServer": 150
  },
  "performance": {
    "totalRequests": 15234,
    "totalErrors": 23,
    "errorRate": 0.15,
    "requestsPerSecond": 4.09,
    "currentLoad": 12.5,
    "avgRequestsPerServer": 3808.5
  },
  "autoScaling": {
    "enabled": true,
    "minServers": 2,
    "maxServers": 10,
    "scaleUpThreshold": 50.0,
    "scaleDownThreshold": 10.0,
    "checkInterval": 10,
    "lastScaleAction": "Scaled up by 2",
    "lastScaleTime": 1731659447123
  },
  "hashRing": {
    "totalVirtualNodes": 600,
    "physicalNodes": 4
  },
  "servers": {
    "total": 4,
    "active": 4,
    "inactive": 0,
    "nodes": [
      {
        "id": "server-9001",
        "address": "localhost:9001",
        "active": true,
        "uptime": 3600,
        "uptimeFormatted": "1h 0m 0s",
        "requestCount": 3842,
        "requestsPerSecond": 1.07,
        "loadPercentage": 25.21
      },
      {
        "id": "server-9002",
        "address": "localhost:9002",
        "active": true,
        "uptime": 3600,
        "uptimeFormatted": "1h 0m 0s",
        "requestCount": 3756,
        "requestsPerSecond": 1.04,
        "loadPercentage": 24.65
      },
      {
        "id": "server-9003",
        "address": "localhost:9003",
        "active": true,
        "uptime": 1800,
        "uptimeFormatted": "30m 0s",
        "requestCount": 3850,
        "requestsPerSecond": 2.14,
        "loadPercentage": 25.27
      },
      {
        "id": "server-9004",
        "address": "localhost:9004",
        "active": true,
        "uptime": 1800,
        "uptimeFormatted": "30m 0s",
        "requestCount": 3786,
        "requestsPerSecond": 2.1,
        "loadPercentage": 24.85
      }
    ]
  },
  "timeline": [
    {
      "timestamp": 1731659387000,
      "requestsPerSecond": 8.5,
      "serverCount": 3
    },
    {
      "timestamp": 1731659397000,
      "requestsPerSecond": 12.3,
      "serverCount": 4
    },
    {
      "timestamp": 1731659407000,
      "requestsPerSecond": 15.2,
      "serverCount": 4
    }
  ]
}
```

## Metrics Breakdown

### 📊 Load Balancer Metrics

- **port**: Load balancer port
- **uptime**: Uptime in seconds
- **uptimeFormatted**: Human-readable uptime (e.g., "1h 2m 5s")
- **virtualNodesPerServer**: Number of virtual nodes per physical server

### 🚀 Performance Metrics

- **totalRequests**: Total requests handled since start
- **totalErrors**: Total errors encountered
- **errorRate**: Percentage of failed requests (0-100)
- **requestsPerSecond**: Average throughput since start
- **currentLoad**: Current requests/second (last interval)
- **avgRequestsPerServer**: Average requests per server

### ⚖️ Auto-Scaling Metrics

- **enabled**: Whether auto-scaling is active
- **minServers**: Minimum number of servers
- **maxServers**: Maximum number of servers
- **scaleUpThreshold**: Requests/sec to trigger scale up
- **scaleDownThreshold**: Requests/sec to trigger scale down
- **checkInterval**: How often to check (seconds)
- **lastScaleAction**: Description of last scaling event
- **lastScaleTime**: Timestamp of last scaling (milliseconds)

### 🔄 Hash Ring Statistics

- **totalVirtualNodes**: Total virtual nodes in the ring
- **physicalNodes**: Number of actual servers

### 🖥️ Server Metrics (per server)

- **id**: Server identifier
- **address**: Server address (host:port)
- **active**: Health status
- **uptime**: Server uptime in seconds
- **uptimeFormatted**: Human-readable uptime
- **requestCount**: Total requests handled by this server
- **requestsPerSecond**: Average throughput for this server
- **loadPercentage**: Percentage of total requests handled

### 📈 Timeline Data (for charts)

Array of data points (last 60 points, one per auto-scale interval):

- **timestamp**: Unix timestamp in milliseconds
- **requestsPerSecond**: Requests per second at this point
- **serverCount**: Number of servers at this point

## UI Visualization Ideas

### 1. **Real-time Dashboard**

- Big numbers showing current load, total requests, active servers
- Error rate indicator with color coding (green/yellow/red)
- Uptime display

### 2. **Line Charts**

- Requests/second over time (timeline data)
- Server count overlay on the same chart
- Visual markers for scaling events

### 3. **Server Grid/Cards**

- Each server as a card showing:
  - ID and address
  - Status indicator (active/inactive)
  - Request count with progress bar
  - Load percentage
  - Individual uptime

### 4. **Load Distribution**

- Pie chart or bar chart showing load percentage per server
- Should be relatively balanced with consistent hashing

### 5. **Auto-scaling Panel**

- Status indicator (enabled/disabled)
- Current thresholds displayed
- Last action and time
- Visual gauge showing current load vs thresholds

### 6. **Hash Ring Visualization**

- Circular visualization showing virtual node distribution
- Interactive view of the consistent hash ring

### 7. **Live Activity Feed**

- Recent requests (sampled)
- Scaling events
- Server health changes
- Error events

## CORS Support

The endpoint includes CORS headers (`Access-Control-Allow-Origin: *`) to allow frontend applications to fetch the data from different origins.

## Polling Recommendations

- **Dashboard metrics**: Poll every 1-2 seconds
- **Timeline data**: Poll every 5-10 seconds (auto-scale interval)
- **Server details**: Poll every 2-3 seconds

## Example Frontend Code

```javascript
// Fetch stats
async function fetchStats() {
  const response = await fetch("http://localhost:8080/stats");
  const data = await response.json();

  // Update UI
  updateDashboard(data);
  updateCharts(data.timeline);
  updateServerCards(data.servers.nodes);
  updateAutoScalingStatus(data.autoScaling);
}

// Poll every 2 seconds
setInterval(fetchStats, 2000);
```

## Color Scheme Suggestions

- **Healthy/Active**: Green (#10b981)
- **Warning**: Yellow (#f59e0b)
- **Error/Inactive**: Red (#ef4444)
- **Primary**: Blue (#3b82f6)
- **Scale Up**: Green gradient
- **Scale Down**: Orange gradient

## Animated Elements

- Pulse animation on active servers
- Smooth transitions for chart updates
- Number counters with animated increments
- Loading indicators during scaling operations
