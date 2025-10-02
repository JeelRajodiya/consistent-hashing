package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.example.common.Node;
import org.example.config.ServerConfig;

/** Manages the lifecycle of backend servers */
public class ServerManager {

  private static final Logger LOGGER = Logger.getLogger(ServerManager.class.getName());

  private final ServerConfig config;
  private final Map<String, Process> serverProcesses;
  private final Map<String, Node> nodes;
  private int nextPort;

  public ServerManager(ServerConfig config) {
    this.config = config;
    this.serverProcesses = new ConcurrentHashMap<>();
    this.nodes = new ConcurrentHashMap<>();
    this.nextPort = config.getStartingPort();
  }

  /** Start a new server instance */
  public Node startServer() throws IOException {
    int port = nextPort++;
    String nodeId = "server-" + port;

    String command = config.getServerCommand().replace("{PORT}", String.valueOf(port));

    LOGGER.info("Starting server with command: " + command);

    ProcessBuilder pb = new ProcessBuilder(command.split("\\s+"));
    pb.redirectErrorStream(true);
    Process process = pb.start();

    // Start a thread to consume the output
    new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  LOGGER.fine("[" + nodeId + "] " + line);
                }
              } catch (IOException e) {
                LOGGER.warning("Error reading server output: " + e.getMessage());
              }
            })
        .start();

    serverProcesses.put(nodeId, process);
    Node node = new Node(nodeId, "localhost", port);
    nodes.put(nodeId, node);

    // Wait a bit for the server to start
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    LOGGER.info("✓ Server " + nodeId + " started on port " + port);
    return node;
  }

  /** Stop a server instance */
  public void stopServer(String nodeId) {
    Process process = serverProcesses.get(nodeId);
    if (process != null) {
      process.destroy();
      serverProcesses.remove(nodeId);
      nodes.remove(nodeId);
      LOGGER.info("✗ Server " + nodeId + " stopped");
    }
  }

  /** Get all active nodes */
  public Collection<Node> getNodes() {
    return new ArrayList<>(nodes.values());
  }

  /** Get a specific node */
  public Node getNode(String nodeId) {
    return nodes.get(nodeId);
  }

  /** Check if a server is healthy */
  public boolean isServerHealthy(Node node) {
    try {
      URL url = new URL("http://" + node.getAddress() + "/health");
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(2000);
      conn.setReadTimeout(2000);

      int responseCode = conn.getResponseCode();
      conn.disconnect();

      return responseCode == 200;
    } catch (Exception e) {
      return false;
    }
  }

  /** Shutdown all servers */
  public void shutdownAll() {
    LOGGER.info("Shutting down all servers...");
    for (String nodeId : new ArrayList<>(serverProcesses.keySet())) {
      stopServer(nodeId);
    }
  }

  /** Get server count */
  public int getServerCount() {
    return nodes.size();
  }
}
