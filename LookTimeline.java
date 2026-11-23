package com.mirror.recorder.handler;

import com.mirror.recorder.model.Frame;
import java.util.Arrays;

/** Render-time camera samples inside an ordinary recorded game tick. */
final class LookTimeline {
  private static final int SCALE = 10000;

  private LookTimeline() {}

  static float value(int[] samples, float partial, float start, float end, int component) {
    float t = Float.isNaN(partial) ? 0f : Math.max(0f, Math.min(1f, partial));
    int time = Math.round(t * SCALE), beforeTime = 0;
    float before = start;
    for (int i = 0; i < samples.length; i += 3) {
      int afterTime = samples[i];
      float after = Float.intBitsToFloat(samples[i + component]);
      if (time <= afterTime) return interpolate(before, after, beforeTime, afterTime, time);
      beforeTime = afterTime;
      before = after;
    }
    return interpolate(before, end, beforeTime, SCALE, time);
  }

  private static float interpolate(float a, float b, int x0, int x1, int x) {
    if (x1 <= x0) return b;
    float t = (x - x0) / (float) (x1 - x0);
    return a + (b - a) * Math.max(0f, Math.min(1f, t));
  }

  static final class Capture {
    private final int[] data = new int[Frame.MAX_LOOK_SAMPLES * 3];
    private int count;

    void sample(float partial, float yaw, float pitch) {
      if (Float.isNaN(partial) || Float.isInfinite(partial) || Float.isNaN(yaw)
          || Float.isInfinite(yaw) || Float.isNaN(pitch) || Float.isInfinite(pitch)
          || Math.abs(pitch) > 90.001f) return;
      int time = Math.round(Math.max(0f, Math.min(1f, partial)) * SCALE);
      if (count > 0 && time < data[(count - 1) * 3]) return;
      int index;
      if (count > 0 && time == data[(count - 1) * 3]) index = count - 1;
      else if (count < Frame.MAX_LOOK_SAMPLES) index = count++;
      else index = count - 1;
      int offset = index * 3;
      data[offset] = time;
      data[offset + 1] = Float.floatToIntBits(yaw);
      data[offset + 2] = Float.floatToIntBits(pitch);
    }

    int[] drain() {
      int[] result = Arrays.copyOf(data, count * 3);
      count = 0;
      return result;
    }

    void clear() {
      count = 0;
    }
  }
}
