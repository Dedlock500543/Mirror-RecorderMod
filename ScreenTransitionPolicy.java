package com.mirror.recorder.gui;

/** Pure visual timing; the screen changes immediately and input is never delayed. */
final class ScreenTransitionPolicy {
  static final long DURATION_MS = 150L;

  private ScreenTransitionPolicy() {}

  static float outgoingOpacity(long started, long now, boolean animated) {
    if (!animated || started < 0L) return 0f;
    long elapsed = Math.max(0L, now - started);
    if (elapsed >= DURATION_MS) return 0f;
    return 1f - UiMotion.easeOut(elapsed / (float) DURATION_MS);
  }

  static boolean validCapture(int width, int height) {
    return width > 0 && height > 0 && width <= 4096 && height <= 4096;
  }
}
