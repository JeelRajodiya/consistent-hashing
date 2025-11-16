package org.example.loadbalancer.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.example.loadbalancer.LoadBalancer;
import org.json.JSONObject;

public class ToggleAutoScaleHandler implements HttpHandler {

  private final LoadBalancer loadBalancer;

  public ToggleAutoScaleHandler(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    // Set CORS headers
    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

    // Handle preflight request
    if ("OPTIONS".equals(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(204, -1);
      return;
    }

    if (!"POST".equals(exchange.getRequestMethod())) {
      String response = "{\"error\": \"Method not allowed. Use POST.\"}";
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(405, response.getBytes(StandardCharsets.UTF_8).length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }
      return;
    }

    try {
      // Read the request body
      String body = new BufferedReader(new InputStreamReader(exchange.getRequestBody())).lines()
        .collect(Collectors.joining("\n"));

      JSONObject jsonObject = new JSONObject(body);
      boolean enabled = jsonObject.getBoolean("enabled");

      // Update auto-scaling status
      loadBalancer.setAutoScalingEnabled(enabled);

      // Build response
      String response = String.format("""
        {
          "status": "success",
          "message": "Auto-scaling %s",
          "autoScalingEnabled": %s
        }
        """, enabled ? "enabled" : "disabled", enabled);

      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }

    } catch (Exception e) {
      loadBalancer.getLogger().log(java.util.logging.Level.SEVERE, "Error toggling auto-scaling: {0}", e.getMessage());
      String response = String.format("{\"error\": \"Error toggling auto-scaling: %s\"}", e.getMessage());
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(500, response.getBytes(StandardCharsets.UTF_8).length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }
    }
  }
}
