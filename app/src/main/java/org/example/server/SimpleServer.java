package org.example.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/** Simple HTTP server that represents a backend server */
public class SimpleServer {

  private static final Logger LOGGER = Logger.getLogger(SimpleServer.class.getName());
  private final int port;
  private HttpServer server;
  private final String serverId;

  public SimpleServer(int port) {
    this.port = port;
    this.serverId = "Server-" + port;
  }

  public void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/", new MyHandler());
    server.createContext("/health", new HealthHandler());
    server.setExecutor(Executors.newFixedThreadPool(10));
    server.start();
    LOGGER.log(Level.INFO, "{0} started on port {1}", new Object[] { serverId, port });
  }

  public void stop() {
    if (server != null) {
      server.stop(0);
      LOGGER.log(Level.INFO, "{0} stopped", serverId);
    }
  }

  class MyHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String requestPath = exchange.getRequestURI().getPath();
      String requestMethod = exchange.getRequestMethod();

      String response = String.format("""
        {
          "server": "%s",
          "port": %d,
          "path": "%s",
          "method": "%s",
          "message": "Hello from %s!",
          "timestamp": %d
        }""", serverId, port, requestPath, requestMethod, serverId, System.currentTimeMillis());

      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }

      LOGGER.log(Level.INFO, "{0} handled request: {1} {2}", new Object[] { serverId, requestMethod, requestPath });
    }
  }

  class HealthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String response = "{\"status\": \"UP\", \"server\": \"" + serverId + "\"}";
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Usage: SimpleServer <port>");
      System.exit(1);
    }

    int port = Integer.parseInt(args[0]);
    SimpleServer server = new SimpleServer(port);

    try {
      server.start();

      // Keep the server running
      Thread.currentThread().join();
    } catch (IOException | InterruptedException e) {
      LOGGER.log(Level.SEVERE, "Error starting server: {0}", e.getMessage());
    }
  }
}
