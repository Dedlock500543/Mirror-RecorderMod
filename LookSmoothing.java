package com.mirror.recorder.handler;

/** Pure render offset math; logical playback rotation is never interpolated. */
final class LookSmoothing {
  private LookSmoothing() {}

  static float renderOffset(float offset, float partialTicks, int ticksLeft) {
    float progress = Math.max(0f, Math.min(1f, partialTicks));
    return offset * (1f - progress / Math.max(1, ticksLeft));
  }

  static float nextTickOffset(float offset, int ticksLeft) {
    return renderOffset(offset, 1f, ticksLeft);
  }
}
