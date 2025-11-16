package org.example.loadbalancer.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.example.common.Node;
import org.example.loadbalancer.LoadBalancer;

public class RemoveServerHandler implements HttpHandler {

  private final LoadBalancer loadBalancer;

  public RemoveServerHandler(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    String query = exchange.getRequestURI().getQuery();
    String nodeId = org.example.util.QueryParamParser.getParam(query, "id");

    if (nodeId == null || nodeId.isEmpty()) {
      loadBalancer.sendErrorResponse(exchange, "Missing 'id' parameter");
      return;
    }
    Node node = loadBalancer.getServerManager().getNode(nodeId);

    if (node == null) {
      loadBalancer.sendErrorResponse(exchange, "Server not found: " + nodeId);
      return;
    }

    loadBalancer.removeServerNode(nodeId);

    loadBalancer.getLogger().info(loadBalancer.getHashRing().getStats());

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("""
      {
        "status": "success",
        "message": "Server removed successfully",
        "serverId": """);
    stringBuilder.append(nodeId);
    stringBuilder.append("\",\n");
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
  }
}
