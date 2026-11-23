package com.mirror.recorder.gui;

/** Fade-only appearance keeps all controls at their final, immediately clickable geometry. */
final class Appearance {
  static final float SLIDE_Y = 0f;
  static final float INFO_SLIDE_X = 0f;

  private Appearance() {}

  static float progress(long at, long now) {
    return UiMotion.appearance(at, now, UiTheme.APPEAR_MS, UiTheme.animationsEnabled());
  }

  static float offsetY(float progress) {
    return 0f;
  }

  static boolean contains(int mx, int my, int x, int y, int w, int h, float progress) {
    // Opacity is decorative: it must never gate input or move the counter strip.
    return UiMotion.contains(mx, my, x, y, w, h);
  }
}
