package org.example.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Utility class for parsing query parameters from HTTP requests */
public class QueryParamParser {

  /**
   * Parse query string into a map of parameter names to values. Handles URL encoding and multiple parameters.
   *
   * @param query
   *          The query string (e.g., "id=123&count=5")
   * @return Map of parameter names to decoded values, or empty map if query is null
   */
  public static Map<String, String> parse(String query) {
    Map<String, String> params = new HashMap<>();

    if (query == null || query.isEmpty()) {
      return params;
    }

    String[] pairs = query.split("&");
    for (String pair : pairs) {
      int idx = pair.indexOf("=");
      if (idx > 0) {
        String key = decode(pair.substring(0, idx));
        String value = idx < pair.length() - 1 ? decode(pair.substring(idx + 1)) : "";
        params.put(key, value);
      }
    }

    return params;
  }

  /**
   * Get a single parameter value from query string.
   *
   * @param query
   *          The query string
   * @param paramName
   *          The parameter name to retrieve
   * @return The decoded parameter value, or null if not found
   */
  public static String getParam(String query, String paramName) {
    Map<String, String> params = parse(query);
    return params.get(paramName);
  }

  /**
   * Get an integer parameter value from query string.
   *
   * @param query
   *          The query string
   * @param paramName
   *          The parameter name to retrieve
   * @param defaultValue
   *          Default value if parameter is missing or invalid
   * @return The integer parameter value, or defaultValue if not found/invalid
   */
  public static int getIntParam(String query, String paramName, int defaultValue) {
    String value = getParam(query, paramName);
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static String decode(String encoded) {
    try {
      return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return encoded; // Return as-is if decoding fails
    }
  }
}
