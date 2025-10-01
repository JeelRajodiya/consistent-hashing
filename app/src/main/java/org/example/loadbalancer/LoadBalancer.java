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
    private long requestCount = 0;

    public LoadBalancer(ServerConfig config) {
        this.config = config;
        this.hashRing = new ConsistentHashRing(config.getVirtualNodes());
        this.serverManager = new ServerManager(config);
        this.scheduler = Executors.newScheduledThreadPool(1);
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
        httpServer.setExecutor(Executors.newFixedThreadPool(20));
        httpServer.start();
        
        LOGGER.info("========================================");
        LOGGER.info("🎯 Load Balancer started on port " + lbPort);
        LOGGER.info("📊 Access stats at: http://localhost:" + lbPort + "/stats");
        LOGGER.info("➕ Add server: http://localhost:" + lbPort + "/add-server");
        LOGGER.info("➖ Remove server: http://localhost:" + lbPort + "/remove-server?id=<server-id>");
        LOGGER.info("========================================");
        
        // Start health check scheduler
        startHealthCheck();
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
        serverManager.shutdownAll();
        
        LOGGER.info("Load balancer stopped");
    }
}
