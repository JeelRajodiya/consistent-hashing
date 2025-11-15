package org.example;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.config.ServerConfig;
import org.example.loadbalancer.LoadBalancer;
import org.example.util.ColoredConsoleFormatter;

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
      LOGGER.log(Level.INFO, "Loading configuration from: {0}", configPath);
      ServerConfig config = new ServerConfig(configPath);

      // Create and start load balancer
      LoadBalancer loadBalancer = new LoadBalancer(config);
      loadBalancer.start();

      // Add shutdown hook
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        LOGGER.info("Shutdown signal received");
        loadBalancer.stop();
      }));

      // Keep the application running
      LOGGER.info("Application running. Press Ctrl+C to stop");
      Thread.currentThread().join();

    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Fatal error: {0}", e.getMessage());
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

    // Add custom console handler with colored formatter
    ConsoleHandler consoleHandler = new ConsoleHandler();
    consoleHandler.setLevel(Level.INFO);
    consoleHandler.setFormatter(new ColoredConsoleFormatter());
    rootLogger.addHandler(consoleHandler);
  }
}
