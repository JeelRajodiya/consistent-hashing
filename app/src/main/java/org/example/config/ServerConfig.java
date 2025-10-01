package org.example.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration reader for the load balancer
 */
public class ServerConfig {
    private final Properties properties;

    public ServerConfig(String configFilePath) throws IOException {
        properties = new Properties();
        try (InputStream input = new FileInputStream(configFilePath)) {
            properties.load(input);
        }
    }

    public String getServerCommand() {
        return properties.getProperty("server.command", "java -cp app/build/classes/java/main org.example.server.SimpleServer {PORT}");
    }

    public int getInitialServerCount() {
        return Integer.parseInt(properties.getProperty("server.initial.count", "3"));
    }

    public int getStartingPort() {
        return Integer.parseInt(properties.getProperty("server.starting.port", "8081"));
    }

    public int getLoadBalancerPort() {
        return Integer.parseInt(properties.getProperty("loadbalancer.port", "8080"));
    }

    public int getVirtualNodes() {
        return Integer.parseInt(properties.getProperty("virtual.nodes", "150"));
    }

    public int getHealthCheckInterval() {
        return Integer.parseInt(properties.getProperty("health.check.interval", "10"));
    }

    public boolean isAutoScalingEnabled() {
        return Boolean.parseBoolean(properties.getProperty("autoscaling.enabled", "true"));
    }

    public int getAutoScalingMinServers() {
        return Integer.parseInt(properties.getProperty("autoscaling.min.servers", "1"));
    }

    public int getAutoScalingMaxServers() {
        return Integer.parseInt(properties.getProperty("autoscaling.max.servers", "20"));
    }

    public int getAutoScalingCheckInterval() {
        return Integer.parseInt(properties.getProperty("autoscaling.check.interval", "10"));
    }

    public double getAutoScalingScaleUpThreshold() {
        return Double.parseDouble(properties.getProperty("autoscaling.scale.up.threshold", "50"));
    }

    public double getAutoScalingScaleDownThreshold() {
        return Double.parseDouble(properties.getProperty("autoscaling.scale.down.threshold", "10"));
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
