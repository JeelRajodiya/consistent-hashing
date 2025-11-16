package org.example.loadbalancer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.common.Node;
import org.example.config.ServerConfig;
import org.example.loadbalancer.handlers.AddServerHandler;
import org.example.loadbalancer.handlers.LoadBalancerHandler;
import org.example.loadbalancer.handlers.RemoveServerHandler;
import org.example.loadbalancer.handlers.ScaleDownHandler;
import org.example.loadbalancer.handlers.ScaleHandler;
import org.example.loadbalancer.handlers.ScaleUpHandler;
import org.example.loadbalancer.handlers.StatsHandler;
import org.example.ring.ConsistentHashRing;
import org.example.server.ServerManager;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Load Balancer with Consistent Hashing */
public class LoadBalancer {

  private static final Logger LOGGER = Logger.getLogger(LoadBalancer.class.getName());

  private final ServerConfig config;
  private final ConsistentHashRing hashRing;
  private final ServerManager serverManager;
  private HttpServer httpServer;
  private StatsWebSocketServer wsServer;
  private final ScheduledExecutorService scheduler;
  private final ScheduledExecutorService autoScaleScheduler;
  private final ScheduledExecutorService rpsScheduler;
  private long requestCount = 0;
  private long errorCount = 0;
  private final long startTime;
  private long lastScaleTime = 0;
  private String lastScaleAction = "none";

  // Auto-scaling metrics
  private long lastRequestCount = 0;
  private long requestsPerInterval = 0;

  // Per-server request tracking
  private final Map<String, Long> serverRequestCounts = new ConcurrentHashMap<>();
  private final Map<String, Long> serverStartTimes = new ConcurrentHashMap<>();
  private final Map<String, Double> serverRequestsPerSecond = new ConcurrentHashMap<>();
  private final Map<String, Long> serverLastRequestCounts = new ConcurrentHashMap<>();

  // Timeline data (last 60 data points)
  private final List<TimelineDataPoint> requestTimeline = new ArrayList<>();
  private final int MAX_TIMELINE_POINTS = 60;
  private final int MIN_SERVERS;
  private final int MAX_SERVERS;
  private final boolean autoScalingEnabled;
  private final double SCALE_UP_THRESHOLD;
  private final double SCALE_DOWN_THRESHOLD;
  private final int AUTO_SCALE_CHECK_INTERVAL;

  public LoadBalancer(ServerConfig config) {
    this.config = config;
    this.hashRing = new ConsistentHashRing(config.getVirtualNodes());
    this.serverManager = new ServerManager(config);
    this.scheduler = Executors.newScheduledThreadPool(1);
    this.autoScaleScheduler = Executors.newScheduledThreadPool(1);
    this.rpsScheduler = Executors.newScheduledThreadPool(1);
    this.startTime = System.currentTimeMillis();

    // Load auto-scaling configuration
    this.autoScalingEnabled = config.isAutoScalingEnabled();
    this.MIN_SERVERS = config.getAutoScalingMinServers();
    this.MAX_SERVERS = config.getAutoScalingMaxServers();
    this.SCALE_UP_THRESHOLD = config.getAutoScalingScaleUpThreshold();
    this.SCALE_DOWN_THRESHOLD = config.getAutoScalingScaleDownThreshold();
    this.AUTO_SCALE_CHECK_INTERVAL = config.getAutoScalingCheckInterval();
  }

  /** Initialize and start the load balancer */
  public void start() throws Exception {
    LOGGER.info("========================================");
    LOGGER.info("Starting Consistent Hash Load Balancer");
    LOGGER.info("========================================");

    // Start initial servers
    int initialCount = config.getInitialServerCount();
    LOGGER.log(Level.INFO, "Starting {0} initial servers", initialCount);

    for (int i = 0; i < initialCount; i++) {
      Node node = serverManager.startServer();
      hashRing.addNode(node);
      serverStartTimes.put(node.getId(), System.currentTimeMillis());
      serverRequestCounts.put(node.getId(), 0L);
    }

    // Log the ring stats
    LOGGER.info(hashRing.getStats());

    // Start the load balancer HTTP server
    int lbPort = config.getLoadBalancerPort();
    httpServer = HttpServer.create(new InetSocketAddress(lbPort), 0);
    httpServer.createContext("/", new LoadBalancerHandler(this));
    httpServer.createContext("/stats", new StatsHandler(this));
    httpServer.createContext("/add-server", new AddServerHandler(this));
    httpServer.createContext("/remove-server", new RemoveServerHandler(this));
    httpServer.createContext("/scale", new ScaleHandler(this));
    httpServer.createContext("/scale-up", new ScaleUpHandler(this));
    httpServer.createContext("/scale-down", new ScaleDownHandler(this));
    httpServer.setExecutor(Executors.newFixedThreadPool(20));
    httpServer.start();

    // Start WebSocket server for stats streaming
    int wsPort = lbPort + 1; // Use next port for WebSocket
    wsServer = new StatsWebSocketServer(new InetSocketAddress(wsPort), this::generateStatsJson, 1);
    wsServer.start();

    LOGGER.info("========================================");
    LOGGER.log(Level.INFO, "Load Balancer started on port {0}", lbPort);
    LOGGER.log(Level.INFO, "Stats: http://localhost:{0}/stats", lbPort);
    LOGGER.log(Level.INFO, "Stats WebSocket: ws://localhost:{0}", wsPort);
    LOGGER.log(Level.INFO, "Add server: http://localhost:{0}/add-server", lbPort);
    LOGGER.log(Level.INFO, "Remove server: http://localhost:{0}/remove-server?id=<server-id>", lbPort);
    LOGGER.log(Level.INFO, "Scale up: http://localhost:{0}/scale-up?count=<number>", lbPort);
    LOGGER.log(Level.INFO, "Scale down: http://localhost:{0}/scale-down?count=<number>", lbPort);
    LOGGER.log(Level.INFO, "Scale to: http://localhost:{0}/scale?target=<number>", lbPort);
    LOGGER.info("========================================");

    // Start health check scheduler
    startHealthCheck();

    // Start auto-scaling monitor
    startAutoScaling();

    // Start per-server requests per second calculation
    startRpsCalculator();
  }

  /** Start periodic health checks */
  private void startHealthCheck() {
    int interval = config.getHealthCheckInterval();
    scheduler.scheduleAtFixedRate(() -> {
      try {
        for (Node node : serverManager.getNodes()) {
          boolean healthy = serverManager.isServerHealthy(node);
          if (!healthy && node.isActive()) {
            LOGGER.log(Level.WARNING, "Node {0} is unhealthy", node.getId());
            node.setActive(false);
          } else if (healthy && !node.isActive()) {
            LOGGER.log(Level.INFO, "Node {0} recovered", node.getId());
            node.setActive(true);
          }
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Health check error: {0}", e.getMessage());
      }
    }, interval, interval, TimeUnit.SECONDS);
  }

  /** Start auto-scaling based on request load */
  private void startAutoScaling() {
    if (!autoScalingEnabled) {
      LOGGER.info("Auto-scaling disabled");
      return;
    }

    LOGGER.info("Auto-scaling enabled:");
    LOGGER.log(Level.INFO, "  Scale up threshold: {0} req/s", SCALE_UP_THRESHOLD);
    LOGGER.log(Level.INFO, "  Scale down threshold: {0} req/s", SCALE_DOWN_THRESHOLD);
    LOGGER.log(Level.INFO, "  Check interval: {0}s", AUTO_SCALE_CHECK_INTERVAL);
    LOGGER.log(Level.INFO, "  Server range: {0}-{1}", new Object[] { MIN_SERVERS, MAX_SERVERS });

    autoScaleScheduler.scheduleAtFixedRate(() -> {
      try {
        // Calculate requests per second in this interval
        long currentRequests = requestCount;
        requestsPerInterval = currentRequests - lastRequestCount;
        lastRequestCount = currentRequests;

        double requestsPerSecond = requestsPerInterval / (double) AUTO_SCALE_CHECK_INTERVAL;
        int currentServerCount = serverManager.getServerCount();

        int requestsPerServer = currentServerCount > 0 ? (int) (requestsPerSecond / currentServerCount) : 0;

        // Add to timeline
        addToTimeline(requestsPerSecond, currentServerCount);

        LOGGER.log(Level.INFO, "Load: {0} req/s ({1} reqs in {2}s) | {3} servers | {4} req/s per server",
          new Object[] { String.format("%.1f", requestsPerSecond), requestsPerInterval, AUTO_SCALE_CHECK_INTERVAL,
              currentServerCount, requestsPerServer });

        // Scale up if load is high
        if (requestsPerSecond > SCALE_UP_THRESHOLD && currentServerCount < MAX_SERVERS) {
          // Calculate how many servers to add (at least 1, max 20 at a time)
          int serversToAdd = (int) Math.ceil(requestsPerSecond / SCALE_UP_THRESHOLD);
          serversToAdd = Math.max(1, Math.min(serversToAdd, MAX_SERVERS - currentServerCount));

          LOGGER.log(Level.INFO, "AUTO-SCALE UP: Adding {0} server(s)", serversToAdd);

          for (int i = 0; i < serversToAdd; i++) {
            Node node = serverManager.startServer();
            hashRing.addNode(node);
            serverStartTimes.put(node.getId(), System.currentTimeMillis());
            serverRequestCounts.put(node.getId(), 0L);
            serverLastRequestCounts.put(node.getId(), 0L);
            serverRequestsPerSecond.put(node.getId(), 0.0);
          }

          lastScaleTime = System.currentTimeMillis();
          lastScaleAction = "Scaled up by " + serversToAdd;
          LOGGER.log(Level.INFO, "Scaled up to {0} servers", serverManager.getServerCount());
        } // Scale down if load is low (but keep at least MIN_SERVERS)
        else if (requestsPerSecond < SCALE_DOWN_THRESHOLD && currentServerCount > MIN_SERVERS) {
          // Only scale down if load has been consistently low
          // Calculate how many servers to remove (at least 1, max 2 at a time)
          int serversToRemove = Math.min(2, currentServerCount - MIN_SERVERS);

          // Don't scale down too aggressively
          if (requestsPerSecond > 5.0) {
            serversToRemove = Math.min(1, serversToRemove);
          }

          LOGGER.log(Level.INFO, "AUTO-SCALE DOWN: Removing {0} server(s)", serversToRemove);

          List<Node> nodes = new ArrayList<>(serverManager.getNodes());
          for (int i = 0; i < serversToRemove && nodes.size() > MIN_SERVERS; i++) {
            Node node = nodes.get(nodes.size() - 1 - i);
            hashRing.removeNode(node.getId());
            serverManager.stopServer(node.getId());
            serverStartTimes.remove(node.getId());
            serverRequestCounts.remove(node.getId());
          }

          lastScaleTime = System.currentTimeMillis();
          lastScaleAction = "Scaled down by " + serversToRemove;
          LOGGER.log(Level.INFO, "Scaled down to {0} servers", serverManager.getServerCount());
        }

      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Auto-scaling error: {0}", e.getMessage());
      }
    }, AUTO_SCALE_CHECK_INTERVAL, AUTO_SCALE_CHECK_INTERVAL, TimeUnit.SECONDS);
  }

  /** Start calculating requests per second for each server */
  private void startRpsCalculator() {
    rpsScheduler.scheduleAtFixedRate(() -> {
      for (String serverId : serverRequestCounts.keySet()) {
        long currentCount = serverRequestCounts.getOrDefault(serverId, 0L);
        long lastCount = serverLastRequestCounts.getOrDefault(serverId, 0L);
        double rps = (currentCount - lastCount) / 1.0; // Per second
        serverRequestsPerSecond.put(serverId, rps);
        serverLastRequestCounts.put(serverId, currentCount);
      }
    }, 1, 1, TimeUnit.SECONDS);
  }

  /** Add data point to timeline */
  private synchronized void addToTimeline(double requestsPerSecond, int serverCount) {
    TimelineDataPoint point = new TimelineDataPoint(System.currentTimeMillis(), requestsPerSecond, serverCount);

    requestTimeline.add(point);

    // Keep only last MAX_TIMELINE_POINTS
    if (requestTimeline.size() > MAX_TIMELINE_POINTS) {
      requestTimeline.remove(0);
    }
  }

  /** Generate stats JSON */
  public String generateStatsJson() {
    long uptime = System.currentTimeMillis() - startTime;
    long uptimeSeconds = uptime / 1000;
    double requestsPerSecond = uptimeSeconds > 0 ? (double) requestCount / uptimeSeconds : 0;
    double errorRate = requestCount > 0 ? (double) errorCount / requestCount * 100 : 0;
    int currentServerCount = serverManager.getServerCount();
    double avgRequestsPerServer = currentServerCount > 0 ? (double) requestCount / currentServerCount : 0;

    // Calculate current instantaneous load (recent request rate)
    double currentLoad = requestsPerInterval / (double) AUTO_SCALE_CHECK_INTERVAL;

    StringBuilder stats = new StringBuilder();
    stats.append("{\n");

    // Load Balancer Info
    stats.append("  \"loadBalancer\": {\n");
    stats.append("    \"port\": ").append(config.getLoadBalancerPort()).append(",\n");
    stats.append("    \"uptime\": ").append(uptimeSeconds).append(",\n");
    stats.append("    \"uptimeFormatted\": \"").append(formatUptime(uptimeSeconds)).append("\",\n");
    stats.append("    \"virtualNodesPerServer\": ").append(config.getVirtualNodes()).append("\n");
    stats.append("  },\n");

    // Performance Metrics
    stats.append("  \"performance\": {\n");
    stats.append("    \"totalRequests\": ").append(requestCount).append(",\n");
    stats.append("    \"totalErrors\": ").append(errorCount).append(",\n");
    stats.append("    \"errorRate\": ").append(String.format("%.2f", errorRate)).append(",\n");
    stats.append("    \"requestsPerSecond\": ").append(String.format("%.2f", requestsPerSecond)).append(",\n");
    stats.append("    \"currentLoad\": ").append(String.format("%.2f", currentLoad)).append(",\n");
    stats.append("    \"currentLoadPercentage\": ")
      .append(String.format("%.2f", SCALE_UP_THRESHOLD > 0 ? (currentLoad / SCALE_UP_THRESHOLD) * 100 : 0))
      .append(",\n");
    stats.append("    \"avgRequestsPerServer\": ").append(String.format("%.2f", avgRequestsPerServer)).append("\n");
    stats.append("  },\n");

    // Auto-scaling Info
    stats.append("  \"autoScaling\": {\n");
    stats.append("    \"enabled\": ").append(autoScalingEnabled).append(",\n");
    stats.append("    \"minServers\": ").append(MIN_SERVERS).append(",\n");
    stats.append("    \"maxServers\": ").append(MAX_SERVERS).append(",\n");
    stats.append("    \"scaleUpThreshold\": ").append(SCALE_UP_THRESHOLD).append(",\n");
    stats.append("    \"scaleDownThreshold\": ").append(SCALE_DOWN_THRESHOLD).append(",\n");
    stats.append("    \"checkInterval\": ").append(AUTO_SCALE_CHECK_INTERVAL).append(",\n");
    stats.append("    \"lastScaleAction\": \"").append(lastScaleAction).append("\",\n");
    stats.append("    \"lastScaleTime\": ").append(lastScaleTime).append("\n");
    stats.append("  },\n");

    // Hash Ring Stats
    stats.append("  \"hashRing\": {\n");
    stats.append("    \"totalVirtualNodes\": ").append(currentServerCount * config.getVirtualNodes()).append(",\n");
    stats.append("    \"physicalNodes\": ").append(currentServerCount).append("\n");
    stats.append("  },\n");

    // Servers Info
    stats.append("  \"servers\": {\n");
    stats.append("    \"total\": ").append(currentServerCount).append(",\n");
    stats.append("    \"active\": ").append(serverManager.getNodes().stream().filter(Node::isActive).count())
      .append(",\n");
    stats.append("    \"inactive\": ").append(serverManager.getNodes().stream().filter(n -> !n.isActive()).count())
      .append(",\n");
    stats.append("    \"nodes\": [\n");

    // Calculate per-server capacity based on recent load
    double perServerCapacity = currentServerCount > 0
      ? (requestsPerInterval / (double) AUTO_SCALE_CHECK_INTERVAL) / currentServerCount
      : 0;

    List<Node> nodes = new ArrayList<>(serverManager.getNodes());
    for (int i = 0; i < nodes.size(); i++) {
      Node node = nodes.get(i);
      long nodeUptime = (System.currentTimeMillis() - serverStartTimes.getOrDefault(node.getId(), startTime)) / 1000;
      long nodeRequests = serverRequestCounts.getOrDefault(node.getId(), 0L);
      double nodeRequestsPerSecond = serverRequestsPerSecond.getOrDefault(node.getId(), 0.0);

      // Calculate load percentage based on deviation from the average number of requests.
      // This avoids spikes when a server has low uptime.
      double nodeLoadPercentage = avgRequestsPerServer > 0 ? (nodeRequests / avgRequestsPerServer) * 100 : 0;

      stats.append("      {\n");
      stats.append("        \"id\": \"").append(node.getId()).append("\",\n");
      stats.append("        \"address\": \"").append(node.getAddress()).append("\",\n");
      stats.append("        \"active\": ").append(node.isActive()).append(",\n");
      stats.append("        \"uptime\": ").append(nodeUptime).append(",\n");
      stats.append("        \"uptimeFormatted\": \"").append(formatUptime(nodeUptime)).append("\",\n");
      stats.append("        \"requestCount\": ").append(nodeRequests).append(",\n");
      stats.append("        \"requestsPerSecond\": ").append(String.format("%.2f", nodeRequestsPerSecond))
        .append(",\n");
      stats.append("        \"loadPercentage\": ").append(String.format("%.2f", nodeLoadPercentage)).append("\n");
      stats.append("      }");
      if (i < nodes.size() - 1) {
        stats.append(",");
      }
      stats.append("\n");
    }

    stats.append("    ]\n");
    stats.append("  }\n");

    // Timeline data for charts
    // stats.append(" \"timeline\": [\n");
    // synchronized (requestTimeline) {
    // for (int i = 0; i < requestTimeline.size(); i++) {
    // TimelineDataPoint point = requestTimeline.get(i);
    // stats.append(" {\n");
    // stats.append(" \"timestamp\": ").append(point.timestamp).append(",\n");
    // stats.append(" \"requestsPerSecond\": ").append(String.format("%.2f", point.requestsPerSecond))
    // .append(",\n");
    // stats.append(" \"serverCount\": ").append(point.serverCount).append("\n");
    // stats.append(" }");
    // if (i < requestTimeline.size() - 1) {
    // stats.append(",");
    // }
    // stats.append("\n");
    // }
    // }
    // stats.append(" ]\n");

    stats.append("}\n");

    return stats.toString();
  }

  /** Format uptime in human-readable format */
  private String formatUptime(long seconds) {
    long days = seconds / 86400;
    long hours = (seconds % 86400) / 3600;
    long minutes = (seconds % 3600) / 60;
    long secs = seconds % 60;

    if (days > 0) {
      return String.format("%dd %dh %dm", days, hours, minutes);
    } else if (hours > 0) {
      return String.format("%dh %dm %ds", hours, minutes, secs);
    } else if (minutes > 0) {
      return String.format("%dm %ds", minutes, secs);
    } else {
      return String.format("%ds", secs);
    }
  }

  /** Forward request to backend server */
  @SuppressWarnings("deprecation")
  private String forwardRequest(String targetUrl, String method) throws IOException {
    URL url = new URL(targetUrl);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod(method);
    conn.setConnectTimeout(5000);
    conn.setReadTimeout(5000);

    StringBuilder response;
    try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
      response = new StringBuilder();
      String line;
      while ((line = in.readLine()) != null) {
        response.append(line);
      }
    }
    conn.disconnect();

    return response.toString();
  }

  /** Send error response */
  public void sendErrorResponse(HttpExchange exchange, String message) throws IOException {
    String response = "{\"error\": \"" + message + "\"}";
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(500, response.getBytes(StandardCharsets.UTF_8).length);

    try (OutputStream os = exchange.getResponseBody()) {
      os.write(response.getBytes(StandardCharsets.UTF_8));
    }
  }

  /** Stop the load balancer */
  public void stop() {
    LOGGER.info("Stopping load balancer...");

    if (httpServer != null) {
      httpServer.stop(0);
    }

    if (wsServer != null) {
      wsServer.shutdown();
    }

    scheduler.shutdown();
    autoScaleScheduler.shutdown();
    rpsScheduler.shutdown();
    serverManager.shutdownAll();

    LOGGER.info("Load balancer stopped");
  }

  public ServerConfig getConfig() {
    return config;
  }

  public ConsistentHashRing getHashRing() {
    return hashRing;
  }

  public ServerManager getServerManager() {
    return serverManager;
  }

  public long getRequestCount() {
    return requestCount;
  }

  public void incrementRequestCount() {
    this.requestCount++;
  }

  public long getErrorCount() {
    return errorCount;
  }

  public void incrementErrorCount() {
    this.errorCount++;
  }

  public long getStartTime() {
    return startTime;
  }

  public Map<String, Long> getServerRequestCounts() {
    return serverRequestCounts;
  }

  public Map<String, Long> getServerStartTimes() {
    return serverStartTimes;
  }

  public Map<String, Double> getServerRequestsPerSecond() {
    return serverRequestsPerSecond;
  }

  public Map<String, Long> getServerLastRequestCounts() {
    return serverLastRequestCounts;
  }

  public Logger getLogger() {
    return LOGGER;
  }
}
