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

public class ScaleUpHandler implements HttpHandler {

  private final LoadBalancer loadBalancer;

  public ScaleUpHandler(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    String query = exchange.getRequestURI().getQuery();
    int count = org.example.util.QueryParamParser.getIntParam(query, "count", 1);

    if (count < 1 || count > 20) {
      loadBalancer.sendErrorResponse(exchange, "Count must be between 1 and 20");
      return;
    }

    try {
      List<String> addedServers = new ArrayList<>();
      loadBalancer.getLogger().log(java.util.logging.Level.INFO, "Scaling up by {0} server(s)", count);

      for (int i = 0; i < count; i++) {
        Node node = loadBalancer.addServerNode();
        addedServers.add(node.getId());
      }

      loadBalancer.getLogger().info(loadBalancer.getHashRing().getStats());

      StringBuilder serversJson = new StringBuilder();
      for (int i = 0; i < addedServers.size(); i++) {
        serversJson.append("\"").append(addedServers.get(i)).append("\"");
        if (i < addedServers.size() - 1) {
          serversJson.append(", ");
        }
      }

      String response = """
        {
          "status": "success",
          "message": "Scaled up successfully",
          "serversAdded": """ + count + ",\n" + "  \"serverIds\": [" + serversJson.toString() + "],\n"
        + "  \"totalServers\": " + loadBalancer.getServerManager().getServerCount() + "\n" + "}\n";

      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }

    } catch (IOException e) {
      loadBalancer.getLogger().log(java.util.logging.Level.SEVERE, "Error scaling up: {0}", e.getMessage());
      loadBalancer.sendErrorResponse(exchange, "Error scaling up: " + e.getMessage());
    }
  }
}
