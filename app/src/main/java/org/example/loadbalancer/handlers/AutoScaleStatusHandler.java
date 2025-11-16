package org.example.loadbalancer.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.example.loadbalancer.LoadBalancer;

public class AutoScaleStatusHandler implements HttpHandler {

  private final LoadBalancer loadBalancer;

  public AutoScaleStatusHandler(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    // Set CORS headers
    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

    // Handle preflight request
    if ("OPTIONS".equals(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(204, -1);
      return;
    }

    if (!"GET".equals(exchange.getRequestMethod())) {
      String response = "{\"error\": \"Method not allowed. Use GET.\"}";
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(405, response.getBytes(StandardCharsets.UTF_8).length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }
      return;
    }

    try {
      boolean enabled = loadBalancer.isAutoScalingEnabled();

      String response = String.format("""
        {
          "status": "success",
          "autoScalingEnabled": %s
        }
        """, enabled);

      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }

    } catch (Exception e) {
      loadBalancer.getLogger().log(java.util.logging.Level.SEVERE, "Error getting auto-scaling status: {0}",
        e.getMessage());
      String response = String.format("{\"error\": \"Error getting auto-scaling status: %s\"}", e.getMessage());
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(500, response.getBytes(StandardCharsets.UTF_8).length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }
    }
  }
}
