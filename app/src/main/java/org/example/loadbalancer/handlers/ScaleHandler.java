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

public class ScaleHandler implements HttpHandler {

  private final LoadBalancer loadBalancer;

  public ScaleHandler(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    String query = exchange.getRequestURI().getQuery();

    if (query == null || !query.startsWith("target=")) {
      loadBalancer.sendErrorResponse(exchange, "Missing 'target' parameter");
      return;
    }

    int targetCount;
    try {
      targetCount = Integer.parseInt(query.substring(7));
      if (targetCount < 1 || targetCount > 50) {
        loadBalancer.sendErrorResponse(exchange, "Target must be between 1 and 50");
        return;
      }
    } catch (NumberFormatException e) {
      loadBalancer.sendErrorResponse(exchange, "Invalid target parameter");
      return;
    }

    int currentCount = loadBalancer.getServerManager().getServerCount();

    try {
      String action;
      int changeCount;
      List<String> changedServers = new ArrayList<>();

      if (targetCount > currentCount) {
        // Scale up
        changeCount = targetCount - currentCount;
        action = "scaled up";
        loadBalancer.getLogger().log(java.util.logging.Level.INFO, "Scaling to {0} servers (adding {1})",
          new Object[] { targetCount, changeCount });

        for (int i = 0; i < changeCount; i++) {
          Node node = loadBalancer.getServerManager().startServer();
          loadBalancer.getHashRing().addNode(node);
          loadBalancer.getServerStartTimes().put(node.getId(), System.currentTimeMillis());
          loadBalancer.getServerRequestCounts().put(node.getId(), 0L);
          loadBalancer.getServerLastRequestCounts().put(node.getId(), 0L);
          loadBalancer.getServerRequestsPerSecond().put(node.getId(), 0.0);
          changedServers.add(node.getId());
        }

      } else if (targetCount < currentCount) {
        // Scale down
        changeCount = currentCount - targetCount;
        action = "scaled down";
        loadBalancer.getLogger().log(java.util.logging.Level.INFO, "Scaling to {0} servers (removing {1})",
          new Object[] { targetCount, changeCount });

        List<Node> nodes = new ArrayList<>(loadBalancer.getServerManager().getNodes());
        for (int i = 0; i < changeCount; i++) {
          Node node = nodes.get(nodes.size() - 1 - i);
          loadBalancer.getHashRing().removeNode(node.getId());
          loadBalancer.getServerManager().stopServer(node.getId());
          loadBalancer.getServerStartTimes().remove(node.getId());
          loadBalancer.getServerRequestCounts().remove(node.getId());
          changedServers.add(node.getId());
        }

      } else {
        // No change needed
        String response = """
          {
            "status": "success",
            "message": "Already at target server count",
            "totalServers": """ + currentCount + "\n" + "}\n";

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

        try (OutputStream os = exchange.getResponseBody()) {
          os.write(response.getBytes(StandardCharsets.UTF_8));
          os.close();
        }
        return;
      }

      loadBalancer.getLogger().info(loadBalancer.getHashRing().getStats());

      StringBuilder serversJson = new StringBuilder();
      for (int i = 0; i < changedServers.size(); i++) {
        serversJson.append("\"").append(changedServers.get(i)).append("\"");
        if (i < changedServers.size() - 1) {
          serversJson.append(", ");
        }
      }

      String response = """
        {
          "status": "success",
          "message": "Successfully %s to %d servers",
          "previousCount": %d,
          "currentCount": %d,
          "serversChanged": %d,
          "serverIds": [%s]
        }
        """.formatted(action, targetCount, currentCount, loadBalancer.getServerManager().getServerCount(),
        changedServers.size(), serversJson.toString());

      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }

    } catch (IOException e) {
      loadBalancer.getLogger().log(java.util.logging.Level.SEVERE, "Error scaling: {0}", e.getMessage());
      loadBalancer.sendErrorResponse(exchange, "Error scaling: " + e.getMessage());
    }
  }
}
