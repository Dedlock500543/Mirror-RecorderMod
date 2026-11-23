package com.mirror.recorder.handler;

import java.util.Locale;

/** Token-exact command gating; similarly prefixed server commands are unaffected. */
public final class ModCommandPolicy {
  private ModCommandPolicy() {}

  public static boolean isMirror(String message) {
    if (message == null) return false;
    String s = message.trim().toLowerCase(Locale.ROOT);
    return s.equals("/mirror") || s.startsWith("/mirror ") || s.startsWith("/mirror\t");
  }

  public static boolean allowed(String message, boolean chat, boolean commands) {
    return isMirror(message) ? commands : chat;
  }
}
