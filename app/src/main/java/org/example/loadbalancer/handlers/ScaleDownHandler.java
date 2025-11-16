package org.example.loadbalancer.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.example.common.Node;
import org.example.loadbalancer.LoadBalancer;

public class ScaleDownHandler implements HttpHandler {

  private final LoadBalancer loadBalancer;

  public ScaleDownHandler(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    String query = exchange.getRequestURI().getQuery();
    int count = 1; // Default to 1 server

    if (query != null && query.startsWith("count=")) {
      try {
        count = Integer.parseInt(query.substring(6));
        if (count < 1) {
          loadBalancer.sendErrorResponse(exchange, "Count must be at least 1");
          return;
        }
      } catch (NumberFormatException e) {
        loadBalancer.sendErrorResponse(exchange, "Invalid count parameter");
        return;
      }
    }

    int currentCount = loadBalancer.getServerManager().getServerCount();
    if (count >= currentCount) {
      loadBalancer.sendErrorResponse(exchange,
        "Cannot remove all servers. Current: " + currentCount + ", Requested: " + count);
      return;
    }

    try {
      List<String> removedServers = new ArrayList<>();
      loadBalancer.getLogger().log(java.util.logging.Level.INFO, "Scaling down by {0} server(s)", count);

      // Get list of servers and remove the last N servers
      List<Node> nodes = new ArrayList<>(loadBalancer.getServerManager().getNodes());
      for (int i = 0; i < count && i < nodes.size(); i++) {
        Node node = nodes.get(nodes.size() - 1 - i);
        loadBalancer.getHashRing().removeNode(node.getId());
        loadBalancer.getServerManager().stopServer(node.getId());
        loadBalancer.getServerStartTimes().remove(node.getId());
        loadBalancer.getServerRequestCounts().remove(node.getId());
        removedServers.add(node.getId());
      }

      loadBalancer.getLogger().info(loadBalancer.getHashRing().getStats());

      StringBuilder serversJson = new StringBuilder();
      for (int i = 0; i < removedServers.size(); i++) {
        serversJson.append("\"").append(removedServers.get(i)).append("\"");
        if (i < removedServers.size() - 1) {
          serversJson.append(", ");
        }
      }

      String response = """
        {
          "status": "success",
          "message": "Scaled down successfully",
          "serversRemoved": """ + removedServers.size() + ",\n" + "  \"serverIds\": [" + serversJson.toString() + "],\n"
        + "  \"totalServers\": " + loadBalancer.getServerManager().getServerCount() + "\n" + "}\n";

      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }

    } catch (IOException e) {
      loadBalancer.getLogger().log(java.util.logging.Level.SEVERE, "Error scaling down: {0}", e.getMessage());
      loadBalancer.sendErrorResponse(exchange, "Error scaling down: " + e.getMessage());
    }
  }
}
