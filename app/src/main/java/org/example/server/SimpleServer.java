package org.example.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

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
    LOGGER.info("🚀 " + serverId + " started on port " + port);
  }

  public void stop() {
    if (server != null) {
      server.stop(0);
      LOGGER.info("🛑 " + serverId + " stopped");
    }
  }

  class MyHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String requestPath = exchange.getRequestURI().getPath();
      String requestMethod = exchange.getRequestMethod();

      String response = String.format(
        "{\n" + "  \"server\": \"%s\",\n" + "  \"port\": %d,\n" + "  \"path\": \"%s\",\n" + "  \"method\": \"%s\",\n"
          + "  \"message\": \"Hello from %s!\",\n" + "  \"timestamp\": %d\n" + "}",
        serverId, port, requestPath, requestMethod, serverId, System.currentTimeMillis());

      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

      OutputStream os = exchange.getResponseBody();
      os.write(response.getBytes(StandardCharsets.UTF_8));
      os.close();

      LOGGER.info(serverId + " handled request: " + requestMethod + " " + requestPath);
    }
  }

  class HealthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String response = "{\"status\": \"UP\", \"server\": \"" + serverId + "\"}";
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);

      OutputStream os = exchange.getResponseBody();
      os.write(response.getBytes(StandardCharsets.UTF_8));
      os.close();
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
    } catch (Exception e) {
      LOGGER.severe("Error starting server: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
