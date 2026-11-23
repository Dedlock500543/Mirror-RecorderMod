package com.mirror.recorder.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Bounded, time-based decorative particles; no input or settings ownership. */
final class UiBubbleModel {
  static final int BURST_COUNT = 6, MAX_PARTICLES = 48;
  static final long LIFE_MS = 780L;
  private final List<Particle> particles = new ArrayList<Particle>();
  private int sequence;

  static final class Particle {
    final float x, y, vx, rise, radius;
    final int color;
    final long born;

    Particle(float x, float y, float vx, float rise, float radius, int color, long born) {
      this.x = x;
      this.y = y;
      this.vx = vx;
      this.rise = rise;
      this.radius = radius;
      this.color = color;
      this.born = born;
    }

    float progress(long now) {
      return Math.max(0f, Math.min(1f, (now - born) / (float) LIFE_MS));
    }

    float drawX(long now) {
      return x + vx * progress(now);
    }

    float drawY(long now) {
      float t = progress(now);
      return y - rise * t - 8f * t * t;
    }

    float opacity(long now) {
      float t = progress(now);
      return (1f - t) * (1f - t) * 0.85f;
    }
  }

  void burst(float x, float y, int color, long now, boolean enabled) {
    if (!enabled) {
      clear();
      return;
    }
    prune(now);
    while (particles.size() > MAX_PARTICLES - BURST_COUNT) particles.remove(0);
    for (int i = 0; i < BURST_COUNT; i++) {
      float angle = (i + sequence * 0.17f) * 1.0471976f;
      float side = (float) Math.cos(angle);
      particles.add(
          new Particle(
              x + side * 3f,
              y + (float) Math.sin(angle) * 2f,
              side * (12f + i * 2f),
              16f + i * 3f,
              1.6f + i % 3 * 0.55f,
              color,
              now));
    }
    sequence = (sequence + 1) % 12;
  }

  void prune(long now) {
    Iterator<Particle> it = particles.iterator();
    while (it.hasNext()) {
      Particle p = it.next();
      if (now - p.born >= LIFE_MS || now < p.born) it.remove();
    }
  }

  List<Particle> particles() {
    return particles;
  }

  void clear() {
    particles.clear();
  }
}
