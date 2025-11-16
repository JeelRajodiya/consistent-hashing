package org.example.loadbalancer;

/** Inner class to hold timeline data points */
public class TimelineDataPoint {
  public final long timestamp;
  public final double requestsPerSecond;
  public final int serverCount;

  public TimelineDataPoint(long timestamp, double requestsPerSecond, int serverCount) {
    this.timestamp = timestamp;
    this.requestsPerSecond = requestsPerSecond;
    this.serverCount = serverCount;
  }
}
