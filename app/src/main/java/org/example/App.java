package org.example;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.example.config.ServerConfig;
import org.example.loadbalancer.LoadBalancer;

/** Main application entry point for the Consistent Hash Load Balancer */
public class App {

  private static final Logger LOGGER = Logger.getLogger(App.class.getName());

  public static void main(String[] args) {
    // Setup logging
    setupLogging();

    String configPath = "config.properties";

    // Allow custom config path via command line
    if (args.length > 0) {
      configPath = args[0];
    }

    try {
      // Load configuration
      LOGGER.info("Loading configuration from: " + configPath);
      ServerConfig config = new ServerConfig(configPath);

      // Create and start load balancer
      LoadBalancer loadBalancer = new LoadBalancer(config);
      loadBalancer.start();

      // Add shutdown hook
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        LOGGER.info("\nShutdown signal received...");
        loadBalancer.stop();
      }));

      // Keep the application running
      LOGGER.info("\n✓ Application is running. Press Ctrl+C to stop.\n");
      Thread.currentThread().join();

    } catch (Exception e) {
      LOGGER.severe("Fatal error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  /** Setup logging configuration */
  private static void setupLogging() {
    Logger rootLogger = Logger.getLogger("");
    rootLogger.setLevel(Level.INFO);

    // Remove default handlers
    for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
      rootLogger.removeHandler(handler);
    }

    // Add custom console handler
    ConsoleHandler consoleHandler = new ConsoleHandler();
    consoleHandler.setLevel(Level.INFO);
    consoleHandler.setFormatter(new SimpleFormatter());
    rootLogger.addHandler(consoleHandler);
  }
}
