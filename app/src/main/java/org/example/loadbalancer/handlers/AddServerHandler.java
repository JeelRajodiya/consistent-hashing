package org.example.loadbalancer.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.example.common.Node;
import org.example.loadbalancer.LoadBalancer;

public class AddServerHandler implements HttpHandler {

  private final LoadBalancer loadBalancer;

  public AddServerHandler(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      Node node = loadBalancer.addServerNode();

      loadBalancer.getLogger().info(loadBalancer.getHashRing().getStats());

      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append("""
        {
          "status": "success",
          "message": "Server added successfully",
          "server": {
            "id": """);
      stringBuilder.append(node.getId());
      stringBuilder.append("\",\n");
      stringBuilder.append("    \"address\": \"");
      stringBuilder.append(node.getAddress());
      stringBuilder.append("\"\n");
      stringBuilder.append("  },\n");
      stringBuilder.append("  \"totalServers\": ");
      stringBuilder.append(loadBalancer.getServerManager().getServerCount());
      stringBuilder.append("\n");
      stringBuilder.append("}\n");
      String response = stringBuilder.toString();

      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }

    } catch (IOException e) {
      loadBalancer.getLogger().log(java.util.logging.Level.SEVERE, "Error adding server: {0}", e.getMessage());
      loadBalancer.sendErrorResponse(exchange, "Error adding server: " + e.getMessage());
    }
  }
}
