package com.mirror.recorder.gui;

/** Pure wall-clock interpolation; neither rendering cadence nor Minecraft ticks drive motion. */
final class UiMotion {
  private UiMotion() {}

  static float clamp(float value) {
    return Math.max(0f, Math.min(1f, value));
  }

  static float ease(float t) {
    t = clamp(t);
    return t * t * t * (t * (t * 6f - 15f) + 10f);
  }

  static float easeOut(float t) {
    float u = 1f - clamp(t);
    return 1f - u * u * u;
  }

  static float transition(float from, float to, long at, long now, long duration) {
    if (duration <= 0L) return to;
    if (now <= at) return from;
    if (now - at >= duration) return to;
    return from + (to - from) * ease((now - at) / (float) duration);
  }

  static float appearance(long at, long now, long duration, boolean animated) {
    if (!animated || at < 0L || duration <= 0L) return 1f;
    return easeOut((now - at) / (float) duration);
  }

  static boolean contains(int mx, int my, int x, int y, int width, int height) {
    return width > 0 && height > 0 && mx >= x && mx < x + width && my >= y && my < y + height;
  }

  static boolean inGuard(int mx, int left, int right) {
    return right > left && mx >= left && mx < right;
  }

  static int textOffset(int height, int fontHeight) {
    return Math.max(0, (height - fontHeight + 1) / 2);
  }

  /** Retargets from the interpolated value at this instant, not the previous rendered frame. */
  static final class Channel {
    private float from, target;
    private long at;
    private long duration;

    Channel(float initial) {
      snap(initial);
    }

    void snap(float value) {
      from = target = value;
      duration = 0L;
    }

    float update(float next, long now, long millis, boolean animated) {
      if (!animated) {
        snap(next);
        return next;
      }
      float current = transition(from, target, at, now, duration);
      if (next != target) {
        from = current;
        target = next;
        at = now;
        duration = millis;
      }
      return transition(from, target, at, now, duration);
    }
  }
}
