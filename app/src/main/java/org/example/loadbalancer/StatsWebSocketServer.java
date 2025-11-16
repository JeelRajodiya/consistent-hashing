package org.example.loadbalancer;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/** WebSocket server for streaming stats */
public class StatsWebSocketServer extends WebSocketServer {

  private static final Logger LOGGER = Logger.getLogger(StatsWebSocketServer.class.getName());

  private final Set<WebSocket> connections = new CopyOnWriteArraySet<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private final StatsProvider statsProvider;
  private final int broadcastInterval;

  public StatsWebSocketServer(InetSocketAddress address, StatsProvider statsProvider, int broadcastIntervalSeconds) {
    super(address);
    this.statsProvider = statsProvider;
    this.broadcastInterval = broadcastIntervalSeconds;
    setReuseAddr(true);
  }

  @Override
  public void onStart() {
    LOGGER.log(Level.INFO, "WebSocket Stats Server started on {0}", getAddress());
    startBroadcasting();
  }

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    connections.add(conn);
    LOGGER.log(Level.INFO, "New WebSocket connection: {0}", conn.getRemoteSocketAddress());

    // Send initial stats immediately
    try {
      String stats = statsProvider.getStatsJson();
      conn.send(stats);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error sending initial stats: {0}", e.getMessage());
    }
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    connections.remove(conn);
    LOGGER.log(Level.INFO, "WebSocket connection closed: {0}", conn.getRemoteSocketAddress());
  }

  @Override
  public void onMessage(WebSocket conn, String message) {
    // Handle incoming messages if needed (e.g., commands to change interval)
    LOGGER.log(Level.INFO, "Received message: {0}", message);
  }

  @Override
  public void onError(WebSocket conn, Exception ex) {
    LOGGER.log(Level.WARNING, "WebSocket error: {0}", ex.getMessage());
    if (conn != null) {
      connections.remove(conn);
    }
  }

  /** Start broadcasting stats to all connected clients */
  private void startBroadcasting() {
    scheduler.scheduleAtFixedRate(() -> {
      if (connections.isEmpty()) {
        return;
      }

      try {
        String stats = statsProvider.getStatsJson();
        broadcastStats(stats);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error broadcasting stats: {0}", e.getMessage());
      }
    }, broadcastInterval, broadcastInterval, TimeUnit.SECONDS);
  }

  /** Broadcast stats to all connected clients */
  private void broadcastStats(String message) {
    for (WebSocket conn : connections) {
      try {
        if (conn.isOpen()) {
          conn.send(message);
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error sending to client: {0}", e.getMessage());
      }
    }
  }

  /** Shutdown the WebSocket server */
  public void shutdown() {
    try {
      scheduler.shutdown();
      scheduler.awaitTermination(2, TimeUnit.SECONDS);
      stop(1000);
      LOGGER.info("WebSocket Stats Server stopped");
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error stopping WebSocket server: {0}", e.getMessage());
    }
  }

  /** Interface for providing stats data */
  public interface StatsProvider {
    String getStatsJson() throws Exception;
  }
}
