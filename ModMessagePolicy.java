package com.mirror.recorder.handler;

/** Pure policy: own notices use real monotonic time, not FPS or paused game ticks. */
public final class ModMessagePolicy {
  private ModMessagePolicy() {}

  public static boolean shouldDelete(
      boolean messagesEnabled, boolean autoDelete, int seconds, long createdNs, long nowNs) {
    if (!messagesEnabled) return true;
    if (!autoDelete) return false;
    long lifetime = Math.max(1, Math.min(600, seconds)) * 1_000_000_000L;
    return nowNs - createdNs >= lifetime;
  }
}
