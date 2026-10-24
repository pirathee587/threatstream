package com.threatstream.common;

/**
 * Utility to sanitize user-supplied input before logging,
 * preventing CRLF log injection (CWE-117) and excessive log flooding.
 */
public final class LogSanitizer {

  private static final int MAX_LOG_FIELD_LENGTH = 256;

  private LogSanitizer() {}

  public static String sanitize(String input) {
    if (input == null) {
      return "null";
    }
    String stripped = input.replaceAll("[\\r\\n\\t\\u0000-\\u001F\\u007F]", "_");
    if (stripped.length() > MAX_LOG_FIELD_LENGTH) {
      return stripped.substring(0, MAX_LOG_FIELD_LENGTH) + "...[truncated]";
    }
    return stripped;
  }
}
