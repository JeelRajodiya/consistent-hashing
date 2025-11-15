package org.example.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/** Custom formatter with ANSI color support for console logging */
public class ColoredConsoleFormatter extends Formatter {

  // ANSI color codes
  private static final String RESET = "\u001B[0m";
  private static final String RED = "\u001B[31m";
  private static final String YELLOW = "\u001B[33m";
  private static final String GREEN = "\u001B[32m";
  private static final String BLUE = "\u001B[34m";
  private static final String CYAN = "\u001B[36m";
  private static final String MAGENTA = "\u001B[35m";
  private static final String GRAY = "\u001B[90m";
  private static final String BOLD = "\u001B[1m";
  private static final String DIM = "\u001B[2m";

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

  @Override
  public String format(LogRecord record) {
    StringBuilder builder = new StringBuilder();

    // Time in gray
    LocalTime time = LocalTime.ofInstant(java.time.Instant.ofEpochMilli(record.getMillis()),
      java.time.ZoneId.systemDefault());
    builder.append(GRAY).append("[").append(time.format(TIME_FORMATTER)).append("]").append(RESET).append(" ");

    // Log level with color
    String level = formatLevel(record.getLevel());
    builder.append(level).append(" ");

    // Logger name in dim (shortened)
    String loggerName = shortenLoggerName(record.getLoggerName());
    builder.append(DIM).append(loggerName).append(RESET).append(" ");

    // Message
    String message = formatMessage(record);
    builder.append(message);

    // Exception if present
    if (record.getThrown() != null) {
      builder.append("\n");
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      record.getThrown().printStackTrace(pw);
      builder.append(RED).append(sw.toString()).append(RESET);
    }

    builder.append("\n");
    return builder.toString();
  }

  private String formatLevel(Level level) {
    String levelName = level.getName();
    String color;
    String symbol;

    if (level == Level.SEVERE) {
      color = RED + BOLD;
      symbol = "ERROR";
    } else if (level == Level.WARNING) {
      color = YELLOW;
      symbol = "WARN ";
    } else if (level == Level.INFO) {
      color = GREEN;
      symbol = "INFO ";
    } else if (level == Level.FINE || level == Level.FINER || level == Level.FINEST) {
      color = CYAN;
      symbol = "DEBUG";
    } else {
      color = RESET;
      symbol = levelName;
    }

    return color + "[" + symbol + "]" + RESET;
  }

  private String shortenLoggerName(String loggerName) {
    if (loggerName == null || loggerName.isEmpty()) {
      return "";
    }

    // Extract just the class name
    int lastDot = loggerName.lastIndexOf('.');
    if (lastDot > 0 && lastDot < loggerName.length() - 1) {
      return loggerName.substring(lastDot + 1);
    }

    return loggerName;
  }
}
