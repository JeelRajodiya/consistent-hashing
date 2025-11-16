package org.example.loadbalancer.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.example.common.Node;
import org.example.loadbalancer.LoadBalancer;

public class LoadBalancerHandler implements HttpHandler {

  private final LoadBalancer loadBalancer;

  public LoadBalancerHandler(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    loadBalancer.incrementRequestCount();

    // Use client IP + request path as the key for consistent hashing
    String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
    String path = exchange.getRequestURI().getPath();
    String hashKey = clientIp + path;

    Node targetNode = loadBalancer.getHashRing().getNode(hashKey);

    if (targetNode == null) {
      loadBalancer.incrementErrorCount();
      loadBalancer.sendErrorResponse(exchange, "No available servers");
      return;
    }

    // Track request count for this server
    loadBalancer.getServerRequestCounts().merge(targetNode.getId(), 1L, Long::sum);

    loadBalancer.getLogger().log(java.util.logging.Level.INFO, "Request #{0} from {1} → {2} (key: {3})",
      new Object[] { loadBalancer.getRequestCount(), clientIp, targetNode.getId(), hashKey });

    try {
      // Forward the request to the backend server
      String targetUrl = "http://" + targetNode.getAddress() + exchange.getRequestURI().toString();
      String response = forwardRequest(targetUrl, exchange.getRequestMethod());

      // Send response back to client
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.getResponseHeaders().set("X-Served-By", targetNode.getId());
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }

    } catch (IOException e) {
      loadBalancer.incrementErrorCount();
      loadBalancer.getLogger().log(java.util.logging.Level.SEVERE, "Error forwarding request: {0}", e.getMessage());
      loadBalancer.sendErrorResponse(exchange, "Error contacting backend server: " + e.getMessage());
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
}
