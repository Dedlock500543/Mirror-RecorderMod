package com.mirror.recorder.handler;

/** Pure semantic event rules shared by capture, playback and codec validation. */
public final class SemanticGuiEvents {
  public static final int MAX_PAYLOADS = 64 * 3;
  public static final int MAX_TEXT = 32767;
  static final int MAX_EVENT_OFFSET_MS = 49;
  private static final int MAX_NBT_UTF_BYTES = 65535;

  private SemanticGuiEvents() {}

  public static final class Delta {
    final int start, end;
    final String inserted;

    Delta(int start, int end, String inserted) {
      this.start = start;
      this.end = end;
      this.inserted = inserted;
    }
  }

  static Delta delta(String before, String after) {
    int prefix = 0, suffix = 0;
    while (prefix < before.length()
        && prefix < after.length()
        && before.charAt(prefix) == after.charAt(prefix)) prefix++;
    while (suffix < before.length() - prefix
        && suffix < after.length() - prefix
        && before.charAt(before.length() - suffix - 1) == after.charAt(after.length() - suffix - 1))
      suffix++;
    return new Delta(
        prefix, before.length() - suffix, after.substring(prefix, after.length() - suffix));
  }

  static String apply(String text, int start, int end, String inserted) {
    if (start < 0 || end < start || end > text.length())
      throw new IllegalArgumentException("Invalid edit range");
    return text.substring(0, start) + inserted + text.substring(end);
  }

  public static boolean validText(String text) {
    if (text == null || text.length() > MAX_TEXT) return false;
    int bytes = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      bytes += c >= 1 && c <= 127 ? 1 : (c <= 2047 ? 2 : 3);
      if (bytes > MAX_NBT_UTF_BYTES) return false;
    }
    return true;
  }

  static boolean editorStateMatches(String actual, String expected, int cursor, int selection) {
    return actual.equals(expected)
        && cursor >= 0
        && selection >= 0
        && cursor <= actual.length()
        && selection <= actual.length();
  }

  static boolean matchesComponent(
      String action, String value, String context, String[] candidate, String line) {
    return context.equals(line)
        && candidate != null
        && action.equals(candidate[0])
        && value.equals(candidate[1]);
  }

  public static boolean valid(int[] events, String[] payloads) {
    if (events == null
        || events.length % 6 != 0
        || payloads == null
        || payloads.length > MAX_PAYLOADS
        || payloads.length % 3 != 0) return false;
    for (String s : payloads) if (!validText(s)) return false;
    for (int i = 0; i < events.length; i += 6) if (!validEvent(events, i, payloads)) return false;
    return true;
  }

  private static boolean validEvent(int[] events, int offset, String[] payloads) {
    int action = events[offset], index = events[offset + 2];
    if (action < 12 || action > 16) return true;
    if (events[offset + 1] < 0 || events[offset + 1] > MAX_EVENT_OFFSET_MS) return false;
    if (action == 15) return validWheel(index, events[offset + 4], events[offset + 5]);
    if (index < 0 || index > payloads.length - 3 || index % 3 != 0) return false;
    int x = events[offset + 4], y = events[offset + 5];
    if (action == 12) {
      if (x < 0 || y < x || y > payloads[index + 1].length()) return false;
      return validText(apply(payloads[index + 1], x, y, payloads[index + 2]));
    }
    if (action == 13) return editorStateMatches(payloads[index + 1], payloads[index + 1], x, y);
    if (action == 14) return validChatAction(payloads[index]);
    return true;
  }

  private static boolean validChatAction(String action) {
    return action.equals("OPEN_URL")
        || action.equals("OPEN_FILE")
        || action.equals("RUN_COMMAND")
        || action.equals("SUGGEST_COMMAND")
        || action.equals("CHANGE_PAGE")
        || action.equals("INSERTION");
  }

  private static boolean validWheel(int kind, int position, int tab) {
    if (kind < -2) return false;
    if (kind != -2) return position >= 0;
    float scroll = Float.intBitsToFloat(position);
    return !Float.isNaN(scroll) && scroll >= 0f && scroll <= 1f && tab >= 0;
  }
}
