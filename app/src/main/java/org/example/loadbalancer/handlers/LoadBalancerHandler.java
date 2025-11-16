package org.example.loadbalancer.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
      ForwardResponse forwardResponse = forwardRequest(targetUrl, exchange);

      // Forward response headers from backend (except Content-Length which we'll set)
      for (Map.Entry<String, List<String>> header : forwardResponse.headers.entrySet()) {
        String headerName = header.getKey();
        if (!headerName.equalsIgnoreCase("Content-Length") && !headerName.equalsIgnoreCase("Transfer-Encoding")) {
          for (String value : header.getValue()) {
            exchange.getResponseHeaders().add(headerName, value);
          }
        }
      }

      // Add load balancer header
      exchange.getResponseHeaders().set("X-Served-By", targetNode.getId());

      // Send response
      byte[] responseBytes = forwardResponse.body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(forwardResponse.statusCode, responseBytes.length);

      try (OutputStream os = exchange.getResponseBody()) {
        os.write(responseBytes);
      }

    } catch (IOException e) {
      loadBalancer.incrementErrorCount();
      loadBalancer.getLogger().log(java.util.logging.Level.SEVERE, "Error forwarding request: {0}", e.getMessage());
      loadBalancer.sendErrorResponse(exchange, "Error contacting backend server: " + e.getMessage());
    }
  }

  /** Response from forwarded request */
  private static class ForwardResponse {
    final int statusCode;
    final String body;
    final Map<String, List<String>> headers;

    ForwardResponse(int statusCode, String body, Map<String, List<String>> headers) {
      this.statusCode = statusCode;
      this.body = body;
      this.headers = headers;
    }
  }

  /** Forward request to backend server with headers and body */
  @SuppressWarnings("deprecation")
  private ForwardResponse forwardRequest(String targetUrl, HttpExchange exchange) throws IOException {
    URL url = new URL(targetUrl);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod(exchange.getRequestMethod());
    conn.setConnectTimeout(5000);
    conn.setReadTimeout(5000);

    // Forward request headers (excluding Host and Connection)
    for (Map.Entry<String, List<String>> header : exchange.getRequestHeaders().entrySet()) {
      String headerName = header.getKey();
      if (!headerName.equalsIgnoreCase("Host") && !headerName.equalsIgnoreCase("Connection")
        && !headerName.equalsIgnoreCase("Content-Length")) {
        for (String value : header.getValue()) {
          conn.addRequestProperty(headerName, value);
        }
      }
    }

    // Forward request body if present
    boolean hasBody = exchange.getRequestMethod().equals("POST") || exchange.getRequestMethod().equals("PUT")
      || exchange.getRequestMethod().equals("PATCH");

    if (hasBody) {
      conn.setDoOutput(true);
      try (InputStream requestBody = exchange.getRequestBody(); OutputStream connOut = conn.getOutputStream()) {
        requestBody.transferTo(connOut);
      }
    }

    // Read response
    int statusCode = conn.getResponseCode();
    Map<String, List<String>> responseHeaders = conn.getHeaderFields();

    InputStream inputStream = statusCode >= 200 && statusCode < 300 ? conn.getInputStream() : conn.getErrorStream();

    StringBuilder responseBody = new StringBuilder();
    if (inputStream != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (responseBody.length() > 0) {
            responseBody.append("\n");
          }
          responseBody.append(line);
        }
      }
    }

    conn.disconnect();
    return new ForwardResponse(statusCode, responseBody.toString(), responseHeaders);
  }
}
