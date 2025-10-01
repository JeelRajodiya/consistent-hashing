package org.example.loadbalancer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.example.common.Node;
import org.example.config.ServerConfig;
import org.example.ring.ConsistentHashRing;
import org.example.server.ServerManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Load Balancer with Consistent Hashing
 */
public class LoadBalancer {
    private static final Logger LOGGER = Logger.getLogger(LoadBalancer.class.getName());
    
    private final ServerConfig config;
    private final ConsistentHashRing hashRing;
    private final ServerManager serverManager;
    private HttpServer httpServer;
    private final ScheduledExecutorService scheduler;
    private final ScheduledExecutorService autoScaleScheduler;
    private long requestCount = 0;
    
    // Auto-scaling metrics
    private long lastRequestCount = 0;
    private long requestsPerInterval = 0;
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
        
        // Load auto-scaling configuration
        this.autoScalingEnabled = config.isAutoScalingEnabled();
        this.MIN_SERVERS = config.getAutoScalingMinServers();
        this.MAX_SERVERS = config.getAutoScalingMaxServers();
        this.SCALE_UP_THRESHOLD = config.getAutoScalingScaleUpThreshold();
        this.SCALE_DOWN_THRESHOLD = config.getAutoScalingScaleDownThreshold();
        this.AUTO_SCALE_CHECK_INTERVAL = config.getAutoScalingCheckInterval();
    }

    /**
     * Initialize and start the load balancer
     */
    public void start() throws Exception {
        LOGGER.info("========================================");
        LOGGER.info("Starting Consistent Hash Load Balancer");
        LOGGER.info("========================================");
        
        // Start initial servers
        int initialCount = config.getInitialServerCount();
        LOGGER.info("Starting " + initialCount + " initial servers...");
        
        for (int i = 0; i < initialCount; i++) {
            Node node = serverManager.startServer();
            hashRing.addNode(node);
        }
        
        // Log the ring stats
        LOGGER.info(hashRing.getStats());
        
        // Start the load balancer HTTP server
        int lbPort = config.getLoadBalancerPort();
        httpServer = HttpServer.create(new InetSocketAddress(lbPort), 0);
        httpServer.createContext("/", new LoadBalancerHandler());
        httpServer.createContext("/stats", new StatsHandler());
        httpServer.createContext("/add-server", new AddServerHandler());
        httpServer.createContext("/remove-server", new RemoveServerHandler());
        httpServer.createContext("/scale", new ScaleHandler());
        httpServer.createContext("/scale-up", new ScaleUpHandler());
        httpServer.createContext("/scale-down", new ScaleDownHandler());
        httpServer.setExecutor(Executors.newFixedThreadPool(20));
        httpServer.start();
        
        LOGGER.info("========================================");
        LOGGER.info("🎯 Load Balancer started on port " + lbPort);
        LOGGER.info("📊 Access stats at: http://localhost:" + lbPort + "/stats");
        LOGGER.info("➕ Add server: http://localhost:" + lbPort + "/add-server");
        LOGGER.info("➖ Remove server: http://localhost:" + lbPort + "/remove-server?id=<server-id>");
        LOGGER.info("📈 Scale up: http://localhost:" + lbPort + "/scale-up?count=<number>");
        LOGGER.info("📉 Scale down: http://localhost:" + lbPort + "/scale-down?count=<number>");
        LOGGER.info("⚖️  Scale to: http://localhost:" + lbPort + "/scale?target=<number>");
        LOGGER.info("========================================");
        
        // Start health check scheduler
        startHealthCheck();
        
        // Start auto-scaling monitor
        startAutoScaling();
    }

    /**
     * Start periodic health checks
     */
    private void startHealthCheck() {
        int interval = config.getHealthCheckInterval();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                for (Node node : serverManager.getNodes()) {
                    boolean healthy = serverManager.isServerHealthy(node);
                    if (!healthy && node.isActive()) {
                        LOGGER.warning("⚠️  Node " + node.getId() + " is unhealthy!");
                        node.setActive(false);
                    } else if (healthy && !node.isActive()) {
                        LOGGER.info("✓ Node " + node.getId() + " is back online!");
                        node.setActive(true);
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Health check error: " + e.getMessage());
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    /**
     * Start auto-scaling based on request load
     */
    private void startAutoScaling() {
        if (!autoScalingEnabled) {
            LOGGER.info("⚠️  Auto-scaling is DISABLED");
            return;
        }
        
        LOGGER.info("🔄 Auto-scaling ENABLED:");
        LOGGER.info("   - Scale UP when: > " + SCALE_UP_THRESHOLD + " requests/second");
        LOGGER.info("   - Scale DOWN when: < " + SCALE_DOWN_THRESHOLD + " requests/second");
        LOGGER.info("   - Check interval: " + AUTO_SCALE_CHECK_INTERVAL + " seconds");
        LOGGER.info("   - Min servers: " + MIN_SERVERS + ", Max servers: " + MAX_SERVERS);
        
        autoScaleScheduler.scheduleAtFixedRate(() -> {
            try {
                // Calculate requests per second in this interval
                long currentRequests = requestCount;
                requestsPerInterval = currentRequests - lastRequestCount;
                lastRequestCount = currentRequests;
                
                double requestsPerSecond = requestsPerInterval / (double) AUTO_SCALE_CHECK_INTERVAL;
                int currentServerCount = serverManager.getServerCount();
                
                int requestsPerServer = currentServerCount > 0 ? (int) (requestsPerSecond / currentServerCount) : 0;
                
                LOGGER.info("📊 Load Monitor: " + String.format("%.1f", requestsPerSecond) + " req/s " +
                           "(" + requestsPerInterval + " requests in " + AUTO_SCALE_CHECK_INTERVAL + "s) | " +
                           currentServerCount + " servers | " +
                           requestsPerServer + " req/s per server");
                
                // Scale up if load is high
                if (requestsPerSecond > SCALE_UP_THRESHOLD && currentServerCount < MAX_SERVERS) {
                    // Calculate how many servers to add (at least 1, max 3 at a time)
                    int serversToAdd = Math.min(3, Math.max(1, (int) (requestsPerSecond / SCALE_UP_THRESHOLD)));
                    serversToAdd = Math.min(serversToAdd, MAX_SERVERS - currentServerCount);
                    
                    LOGGER.info("📈 AUTO-SCALE UP: High load detected! Adding " + serversToAdd + " server(s)...");
                    
                    for (int i = 0; i < serversToAdd; i++) {
                        Node node = serverManager.startServer();
                        hashRing.addNode(node);
                    }
                    
                    LOGGER.info("✓ Scaled up to " + serverManager.getServerCount() + " servers");
                }
                // Scale down if load is low (but keep at least MIN_SERVERS)
                else if (requestsPerSecond < SCALE_DOWN_THRESHOLD && currentServerCount > MIN_SERVERS) {
                    // Only scale down if load has been consistently low
                    // Calculate how many servers to remove (at least 1, max 2 at a time)
                    int serversToRemove = Math.min(2, currentServerCount - MIN_SERVERS);
                    
                    // Don't scale down too aggressively
                    if (requestsPerSecond > 5.0) {
                        serversToRemove = Math.min(1, serversToRemove);
                    }
                    
                    LOGGER.info("📉 AUTO-SCALE DOWN: Low load detected. Removing " + serversToRemove + " server(s)...");
                    
                    List<Node> nodes = new ArrayList<>(serverManager.getNodes());
                    for (int i = 0; i < serversToRemove && nodes.size() > MIN_SERVERS; i++) {
                        Node node = nodes.get(nodes.size() - 1 - i);
                        hashRing.removeNode(node.getId());
                        serverManager.stopServer(node.getId());
                    }
                    
                    LOGGER.info("✓ Scaled down to " + serverManager.getServerCount() + " servers");
                }
                
            } catch (Exception e) {
                LOGGER.warning("Auto-scaling error: " + e.getMessage());
            }
        }, AUTO_SCALE_CHECK_INTERVAL, AUTO_SCALE_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * Main load balancer request handler
     */
    class LoadBalancerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount++;
            
            // Use client IP + request path as the key for consistent hashing
            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            String path = exchange.getRequestURI().getPath();
            String hashKey = clientIp + path;
            
            Node targetNode = hashRing.getNode(hashKey);
            
            if (targetNode == null) {
                sendErrorResponse(exchange, "No available servers");
                return;
            }
            
            LOGGER.info("Request #" + requestCount + " from " + clientIp + " → " + targetNode.getId() + " (key: " + hashKey + ")");
            
            try {
                // Forward the request to the backend server
                String targetUrl = "http://" + targetNode.getAddress() + exchange.getRequestURI().toString();
                String response = forwardRequest(targetUrl, exchange.getRequestMethod());
                
                // Send response back to client
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("X-Served-By", targetNode.getId());
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
                
            } catch (Exception e) {
                LOGGER.severe("Error forwarding request: " + e.getMessage());
                sendErrorResponse(exchange, "Error contacting backend server: " + e.getMessage());
            }
        }
    }

    /**
     * Stats endpoint handler
     */
    class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder stats = new StringBuilder();
            stats.append("{\n");
            stats.append("  \"loadBalancer\": {\n");
            stats.append("    \"port\": ").append(config.getLoadBalancerPort()).append(",\n");
            stats.append("    \"totalRequests\": ").append(requestCount).append(",\n");
            stats.append("    \"virtualNodesPerServer\": ").append(config.getVirtualNodes()).append("\n");
            stats.append("  },\n");
            stats.append("  \"servers\": {\n");
            stats.append("    \"total\": ").append(serverManager.getServerCount()).append(",\n");
            stats.append("    \"nodes\": [\n");
            
            List<Node> nodes = new ArrayList<>(serverManager.getNodes());
            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                stats.append("      {\n");
                stats.append("        \"id\": \"").append(node.getId()).append("\",\n");
                stats.append("        \"address\": \"").append(node.getAddress()).append("\",\n");
                stats.append("        \"active\": ").append(node.isActive()).append("\n");
                stats.append("      }");
                if (i < nodes.size() - 1) stats.append(",");
                stats.append("\n");
            }
            
            stats.append("    ]\n");
            stats.append("  }\n");
            stats.append("}\n");
            
            String response = stats.toString();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
            
            LOGGER.info("Stats requested");
        }
    }

    /**
     * Add server endpoint handler
     */
    class AddServerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Node node = serverManager.startServer();
                hashRing.addNode(node);
                
                LOGGER.info(hashRing.getStats());
                
                String response = "{\n" +
                        "  \"status\": \"success\",\n" +
                        "  \"message\": \"Server added successfully\",\n" +
                        "  \"server\": {\n" +
                        "    \"id\": \"" + node.getId() + "\",\n" +
                        "    \"address\": \"" + node.getAddress() + "\"\n" +
                        "  },\n" +
                        "  \"totalServers\": " + serverManager.getServerCount() + "\n" +
                        "}\n";
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
                
            } catch (Exception e) {
                LOGGER.severe("Error adding server: " + e.getMessage());
                sendErrorResponse(exchange, "Error adding server: " + e.getMessage());
            }
        }
    }

    /**
     * Remove server endpoint handler
     */
    class RemoveServerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("id=")) {
                sendErrorResponse(exchange, "Missing 'id' parameter");
                return;
            }
            
            String nodeId = query.substring(3);
            Node node = serverManager.getNode(nodeId);
            
            if (node == null) {
                sendErrorResponse(exchange, "Server not found: " + nodeId);
                return;
            }
            
            hashRing.removeNode(nodeId);
            serverManager.stopServer(nodeId);
            
            LOGGER.info(hashRing.getStats());
            
            String response = "{\n" +
                    "  \"status\": \"success\",\n" +
                    "  \"message\": \"Server removed successfully\",\n" +
                    "  \"serverId\": \"" + nodeId + "\",\n" +
                    "  \"totalServers\": " + serverManager.getServerCount() + "\n" +
                    "}\n";
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        }
    }

    /**
     * Scale up handler - Add multiple servers
     */
    class ScaleUpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            int count = 1; // Default to 1 server
            
            if (query != null && query.startsWith("count=")) {
                try {
                    count = Integer.parseInt(query.substring(6));
                    if (count < 1 || count > 20) {
                        sendErrorResponse(exchange, "Count must be between 1 and 20");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sendErrorResponse(exchange, "Invalid count parameter");
                    return;
                }
            }
            
            try {
                List<String> addedServers = new ArrayList<>();
                LOGGER.info("📈 Scaling up by " + count + " server(s)...");
                
                for (int i = 0; i < count; i++) {
                    Node node = serverManager.startServer();
                    hashRing.addNode(node);
                    addedServers.add(node.getId());
                }
                
                LOGGER.info(hashRing.getStats());
                
                StringBuilder serversJson = new StringBuilder();
                for (int i = 0; i < addedServers.size(); i++) {
                    serversJson.append("\"").append(addedServers.get(i)).append("\"");
                    if (i < addedServers.size() - 1) serversJson.append(", ");
                }
                
                String response = "{\n" +
                        "  \"status\": \"success\",\n" +
                        "  \"message\": \"Scaled up successfully\",\n" +
                        "  \"serversAdded\": " + count + ",\n" +
                        "  \"serverIds\": [" + serversJson.toString() + "],\n" +
                        "  \"totalServers\": " + serverManager.getServerCount() + "\n" +
                        "}\n";
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
                
            } catch (Exception e) {
                LOGGER.severe("Error scaling up: " + e.getMessage());
                sendErrorResponse(exchange, "Error scaling up: " + e.getMessage());
            }
        }
    }

    /**
     * Scale down handler - Remove multiple servers
     */
    class ScaleDownHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            int count = 1; // Default to 1 server
            
            if (query != null && query.startsWith("count=")) {
                try {
                    count = Integer.parseInt(query.substring(6));
                    if (count < 1) {
                        sendErrorResponse(exchange, "Count must be at least 1");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sendErrorResponse(exchange, "Invalid count parameter");
                    return;
                }
            }
            
            int currentCount = serverManager.getServerCount();
            if (count >= currentCount) {
                sendErrorResponse(exchange, "Cannot remove all servers. Current: " + currentCount + ", Requested: " + count);
                return;
            }
            
            try {
                List<String> removedServers = new ArrayList<>();
                LOGGER.info("📉 Scaling down by " + count + " server(s)...");
                
                // Get list of servers and remove the last N servers
                List<Node> nodes = new ArrayList<>(serverManager.getNodes());
                for (int i = 0; i < count && i < nodes.size(); i++) {
                    Node node = nodes.get(nodes.size() - 1 - i);
                    hashRing.removeNode(node.getId());
                    serverManager.stopServer(node.getId());
                    removedServers.add(node.getId());
                }
                
                LOGGER.info(hashRing.getStats());
                
                StringBuilder serversJson = new StringBuilder();
                for (int i = 0; i < removedServers.size(); i++) {
                    serversJson.append("\"").append(removedServers.get(i)).append("\"");
                    if (i < removedServers.size() - 1) serversJson.append(", ");
                }
                
                String response = "{\n" +
                        "  \"status\": \"success\",\n" +
                        "  \"message\": \"Scaled down successfully\",\n" +
                        "  \"serversRemoved\": " + removedServers.size() + ",\n" +
                        "  \"serverIds\": [" + serversJson.toString() + "],\n" +
                        "  \"totalServers\": " + serverManager.getServerCount() + "\n" +
                        "}\n";
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
                
            } catch (Exception e) {
                LOGGER.severe("Error scaling down: " + e.getMessage());
                sendErrorResponse(exchange, "Error scaling down: " + e.getMessage());
            }
        }
    }

    /**
     * Scale handler - Scale to a specific number of servers
     */
    class ScaleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            
            if (query == null || !query.startsWith("target=")) {
                sendErrorResponse(exchange, "Missing 'target' parameter");
                return;
            }
            
            int targetCount;
            try {
                targetCount = Integer.parseInt(query.substring(7));
                if (targetCount < 1 || targetCount > 50) {
                    sendErrorResponse(exchange, "Target must be between 1 and 50");
                    return;
                }
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, "Invalid target parameter");
                return;
            }
            
            int currentCount = serverManager.getServerCount();
            
            try {
                String action;
                int changeCount;
                List<String> changedServers = new ArrayList<>();
                
                if (targetCount > currentCount) {
                    // Scale up
                    changeCount = targetCount - currentCount;
                    action = "scaled up";
                    LOGGER.info("⚖️  Scaling to " + targetCount + " servers (adding " + changeCount + ")...");
                    
                    for (int i = 0; i < changeCount; i++) {
                        Node node = serverManager.startServer();
                        hashRing.addNode(node);
                        changedServers.add(node.getId());
                    }
                    
                } else if (targetCount < currentCount) {
                    // Scale down
                    changeCount = currentCount - targetCount;
                    action = "scaled down";
                    LOGGER.info("⚖️  Scaling to " + targetCount + " servers (removing " + changeCount + ")...");
                    
                    List<Node> nodes = new ArrayList<>(serverManager.getNodes());
                    for (int i = 0; i < changeCount; i++) {
                        Node node = nodes.get(nodes.size() - 1 - i);
                        hashRing.removeNode(node.getId());
                        serverManager.stopServer(node.getId());
                        changedServers.add(node.getId());
                    }
                    
                } else {
                    // No change needed
                    String response = "{\n" +
                            "  \"status\": \"success\",\n" +
                            "  \"message\": \"Already at target server count\",\n" +
                            "  \"totalServers\": " + currentCount + "\n" +
                            "}\n";
                    
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                    
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    return;
                }
                
                LOGGER.info(hashRing.getStats());
                
                StringBuilder serversJson = new StringBuilder();
                for (int i = 0; i < changedServers.size(); i++) {
                    serversJson.append("\"").append(changedServers.get(i)).append("\"");
                    if (i < changedServers.size() - 1) serversJson.append(", ");
                }
                
                String response = "{\n" +
                        "  \"status\": \"success\",\n" +
                        "  \"message\": \"Successfully " + action + " to " + targetCount + " servers\",\n" +
                        "  \"previousCount\": " + currentCount + ",\n" +
                        "  \"currentCount\": " + serverManager.getServerCount() + ",\n" +
                        "  \"serversChanged\": " + changedServers.size() + ",\n" +
                        "  \"serverIds\": [" + serversJson.toString() + "]\n" +
                        "}\n";
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
                
            } catch (Exception e) {
                LOGGER.severe("Error scaling: " + e.getMessage());
                sendErrorResponse(exchange, "Error scaling: " + e.getMessage());
            }
        }
    }

    /**
     * Forward request to backend server
     */
    @SuppressWarnings("deprecation")
    private String forwardRequest(String targetUrl, String method) throws IOException {
        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();
        conn.disconnect();
        
        return response.toString();
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(HttpExchange exchange, String message) throws IOException {
        String response = "{\"error\": \"" + message + "\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(500, response.getBytes(StandardCharsets.UTF_8).length);
        
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    /**
     * Stop the load balancer
     */
    public void stop() {
        LOGGER.info("Stopping load balancer...");
        
        if (httpServer != null) {
            httpServer.stop(0);
        }
        
        scheduler.shutdown();
        autoScaleScheduler.shutdown();
        serverManager.shutdownAll();
        
        LOGGER.info("Load balancer stopped");
    }
}
